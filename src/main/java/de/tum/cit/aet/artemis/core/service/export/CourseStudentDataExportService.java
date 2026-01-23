package de.tum.cit.aet.artemis.core.service.export;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.core.util.RoundingUtil.roundScoreSpecifiedByCourseSettings;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.assessment.domain.GradeStep;
import de.tum.cit.aet.artemis.assessment.domain.GradingScale;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.repository.GradingScaleRepository;
import de.tum.cit.aet.artemis.atlas.api.CompetencyProgressApi;
import de.tum.cit.aet.artemis.atlas.api.LearnerProfileApi;
import de.tum.cit.aet.artemis.communication.repository.AnswerPostRepository;
import de.tum.cit.aet.artemis.communication.repository.PostRepository;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.repository.LLMTokenUsageTraceRepository;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.util.StatisticsUtil;
import de.tum.cit.aet.artemis.exam.dto.ExamScoresDTO;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.IncludedInOverallScore;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.dto.CourseGradeScoreDTO;
import de.tum.cit.aet.artemis.exercise.repository.ParticipationRepository;
import de.tum.cit.aet.artemis.exercise.repository.StudentParticipationRepository;
import de.tum.cit.aet.artemis.iris.api.IrisSettingsApi;
import de.tum.cit.aet.artemis.tutorialgroup.api.TutorialGroupApi;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupRegistration;

/**
 * Service for exporting student data from a course for archival purposes.
 * <p>
 * This service exports data that would be deleted during a course reset, allowing
 * instructors to preserve student data before resetting or at the end of a semester.
 * <p>
 * Exported data includes:
 * <ul>
 * <li>Course scores (aggregated student scores for all exercises)</li>
 * <li>Exam scores (aggregated student scores per exam)</li>
 * <li>Participation results with feedback (latest submission + result)</li>
 * <li>Posts and messages from conversations</li>
 * <li>Competency progress records</li>
 * <li>Course learner profiles</li>
 * <li>Tutorial group registrations</li>
 * <li>Iris chat sessions</li>
 * <li>LLM token usage traces</li>
 * </ul>
 */
@Service
@Profile(PROFILE_CORE)
@Lazy
public class CourseStudentDataExportService {

    private static final Logger log = LoggerFactory.getLogger(CourseStudentDataExportService.class);

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ParticipationRepository participationRepository;

    private final PostRepository postRepository;

    private final AnswerPostRepository answerPostRepository;

    private final LLMTokenUsageTraceRepository llmTokenUsageTraceRepository;

    private final CourseRepository courseRepository;

    private final Optional<CompetencyProgressApi> competencyProgressApi;

    private final Optional<LearnerProfileApi> learnerProfileApi;

    private final Optional<IrisSettingsApi> irisSettingsApi;

    private final Optional<TutorialGroupApi> tutorialGroupApi;

    private final GradingScaleRepository gradingScaleRepository;

    private final StudentParticipationRepository studentParticipationRepository;

    private final UserRepository userRepository;

    private final ObjectMapper objectMapper;

    public CourseStudentDataExportService(ParticipationRepository participationRepository, PostRepository postRepository, AnswerPostRepository answerPostRepository,
            LLMTokenUsageTraceRepository llmTokenUsageTraceRepository, CourseRepository courseRepository, Optional<CompetencyProgressApi> competencyProgressApi,
            Optional<LearnerProfileApi> learnerProfileApi, Optional<IrisSettingsApi> irisSettingsApi, Optional<TutorialGroupApi> tutorialGroupApi,
            GradingScaleRepository gradingScaleRepository, StudentParticipationRepository studentParticipationRepository, UserRepository userRepository) {
        this.participationRepository = participationRepository;
        this.postRepository = postRepository;
        this.answerPostRepository = answerPostRepository;
        this.llmTokenUsageTraceRepository = llmTokenUsageTraceRepository;
        this.courseRepository = courseRepository;
        this.competencyProgressApi = competencyProgressApi;
        this.learnerProfileApi = learnerProfileApi;
        this.irisSettingsApi = irisSettingsApi;
        this.tutorialGroupApi = tutorialGroupApi;
        this.gradingScaleRepository = gradingScaleRepository;
        this.studentParticipationRepository = studentParticipationRepository;
        this.userRepository = userRepository;

        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    /**
     * Exports all student data for a course to the specified output directory.
     * <p>
     * This method creates a "student-data" subdirectory containing:
     * <ul>
     * <li>course-scores.csv - Aggregated student scores for all exercises</li>
     * <li>exam-scores-{examId}.csv - Aggregated student scores per exam</li>
     * <li>participation-results.csv - Latest submissions and results with feedback</li>
     * <li>posts.csv - All posts and answer posts</li>
     * <li>competency-progress.csv - Competency progress records</li>
     * <li>learner-profiles.csv - Course learner profiles</li>
     * <li>tutorial-group-registrations.csv - Tutorial group registrations</li>
     * <li>iris-sessions.json - Iris chat sessions (if available)</li>
     * <li>llm-token-usage.csv - LLM token usage traces</li>
     * </ul>
     *
     * @param courseId  the ID of the course to export data from
     * @param outputDir the directory where the student-data folder will be created
     * @param errors    list to collect any export errors
     * @return list of paths to exported files
     */
    public List<Path> exportAllStudentData(long courseId, Path outputDir, List<String> errors) {
        List<Path> exportedFiles = new ArrayList<>();

        try {
            Path studentDataDir = outputDir.resolve("student-data");
            Files.createDirectories(studentDataDir);

            // Export course scores (aggregated student scores with per-exercise breakdown)
            exportCourseScores(courseId, studentDataDir, errors).ifPresent(exportedFiles::add);

            // Export course grade distribution (if grading scale exists)
            exportCourseGradeDistribution(courseId, studentDataDir, errors).ifPresent(exportedFiles::add);

            // Note: Exam scores are exported separately by CourseArchiveService to avoid circular dependencies

            // Export participation results with feedback
            exportParticipationResults(courseId, studentDataDir, errors).ifPresent(exportedFiles::add);

            // Export posts and answers
            exportPosts(courseId, studentDataDir, errors).ifPresent(exportedFiles::add);

            // Export competency progress
            exportCompetencyProgress(courseId, studentDataDir, errors).ifPresent(exportedFiles::add);

            // Export learner profiles
            exportLearnerProfiles(courseId, studentDataDir, errors).ifPresent(exportedFiles::add);

            // Export tutorial group registrations
            exportTutorialGroupRegistrations(courseId, studentDataDir, errors).ifPresent(exportedFiles::add);

            // Export Iris chat sessions
            exportIrisChatSessions(courseId, studentDataDir, errors).ifPresent(exportedFiles::add);

            // Export LLM token usage traces
            exportLLMTokenUsageTraces(courseId, studentDataDir, errors).ifPresent(exportedFiles::add);

            log.info("Successfully exported student data for course {} to {}", courseId, studentDataDir);
        }
        catch (IOException e) {
            String message = "Failed to create student-data directory for course " + courseId;
            log.error(message, e);
            errors.add(message);
        }

        return exportedFiles;
    }

    /**
     * Exports participation results with the latest submission and result for each participation.
     * Includes both rated (before due date) and practice (after due date) participations.
     *
     * @param courseId  the course ID
     * @param outputDir the directory to write to
     * @param errors    list to collect errors
     * @return path to the exported file
     */
    private Optional<Path> exportParticipationResults(long courseId, Path outputDir, List<String> errors) {
        try {
            List<StudentParticipation> participations = participationRepository.findAllWithLatestSubmissionAndResultByCourseId(courseId);

            if (participations.isEmpty()) {
                log.info("No participations found for course {}", courseId);
                return Optional.empty();
            }

            List<String> lines = new ArrayList<>();
            lines.add("ExerciseId,ExerciseTitle,ParticipantLogin,ParticipantName,ParticipationType,SubmissionDate,Score,Rated,Successful,FeedbackCount,FeedbackDetails");

            for (StudentParticipation participation : participations) {
                Submission latestSubmission = getLatestSubmission(participation);
                Result latestResult = latestSubmission != null ? getLatestResult(latestSubmission) : null;

                String exerciseId = participation.getExercise() != null ? String.valueOf(participation.getExercise().getId()) : "";
                String exerciseTitle = participation.getExercise() != null ? escapeCSV(participation.getExercise().getTitle()) : "";
                String participantLogin = participation.getParticipant() != null ? participation.getParticipant().getParticipantIdentifier() : "";
                String participantName = participation.getParticipant() != null ? escapeCSV(participation.getParticipant().getName()) : "";
                String participationType = participation.isPracticeMode() ? "PRACTICE" : "RATED";
                String submissionDate = latestSubmission != null && latestSubmission.getSubmissionDate() != null ? latestSubmission.getSubmissionDate().format(DATE_FORMATTER) : "";

                String score = "";
                String rated = "";
                String successful = "";
                String feedbackCount = "0";
                String feedbackDetails = "";

                if (latestResult != null) {
                    score = latestResult.getScore() != null ? String.valueOf(latestResult.getScore()) : "";
                    rated = String.valueOf(latestResult.isRated());
                    successful = latestResult.isSuccessful() != null ? String.valueOf(latestResult.isSuccessful()) : "";

                    if (latestResult.getFeedbacks() != null && !latestResult.getFeedbacks().isEmpty()) {
                        feedbackCount = String.valueOf(latestResult.getFeedbacks().size());
                        feedbackDetails = escapeCSV(formatFeedbacks(latestResult.getFeedbacks()));
                    }
                }

                lines.add(String.join(",", exerciseId, exerciseTitle, participantLogin, participantName, participationType, submissionDate, score, rated, successful, feedbackCount,
                        feedbackDetails));
            }

            Path outputFile = outputDir.resolve("participation-results.csv");
            return Optional.of(writeLinesToFile(lines, outputFile));
        }
        catch (Exception e) {
            String message = "Failed to export participation results for course " + courseId;
            log.error(message, e);
            errors.add(message);
            return Optional.empty();
        }
    }

    /**
     * Exports all posts and answer posts from the course.
     *
     * @param courseId  the course ID
     * @param outputDir the directory to write to
     * @param errors    list to collect errors
     * @return path to the exported file
     */
    private Optional<Path> exportPosts(long courseId, Path outputDir, List<String> errors) {
        try {
            var posts = postRepository.findAllByCourseId(courseId);
            var answerPosts = answerPostRepository.findAllByCourseId(courseId);

            if (posts.isEmpty() && answerPosts.isEmpty()) {
                log.info("No posts found for course {}", courseId);
                return Optional.empty();
            }

            List<String> lines = new ArrayList<>();
            lines.add("Type,Id,AuthorLogin,CreationDate,Content,ConversationId,ParentPostId");

            // Export regular posts
            for (var post : posts) {
                String authorLogin = post.getAuthor() != null ? post.getAuthor().getLogin() : "";
                String creationDate = post.getCreationDate() != null ? post.getCreationDate().format(DATE_FORMATTER) : "";
                String content = escapeCSV(post.getContent());
                String conversationId = post.getConversation() != null ? String.valueOf(post.getConversation().getId()) : "";

                lines.add(String.join(",", "POST", String.valueOf(post.getId()), authorLogin, creationDate, content, conversationId, ""));
            }

            // Export answer posts
            for (var answerPost : answerPosts) {
                String authorLogin = answerPost.getAuthor() != null ? answerPost.getAuthor().getLogin() : "";
                String creationDate = answerPost.getCreationDate() != null ? answerPost.getCreationDate().format(DATE_FORMATTER) : "";
                String content = escapeCSV(answerPost.getContent());
                String conversationId = "";
                String parentPostId = answerPost.getPost() != null ? String.valueOf(answerPost.getPost().getId()) : "";

                lines.add(String.join(",", "ANSWER", String.valueOf(answerPost.getId()), authorLogin, creationDate, content, conversationId, parentPostId));
            }

            Path outputFile = outputDir.resolve("posts.csv");
            return Optional.of(writeLinesToFile(lines, outputFile));
        }
        catch (Exception e) {
            String message = "Failed to export posts for course " + courseId;
            log.error(message, e);
            errors.add(message);
            return Optional.empty();
        }
    }

    /**
     * Exports competency progress records for the course.
     *
     * @param courseId  the course ID
     * @param outputDir the directory to write to
     * @param errors    list to collect errors
     * @return path to the exported file
     */
    private Optional<Path> exportCompetencyProgress(long courseId, Path outputDir, List<String> errors) {
        if (competencyProgressApi.isEmpty()) {
            return Optional.empty();
        }

        try {
            var progressList = competencyProgressApi.get().findAllForExportByCourseId(courseId);

            if (progressList.isEmpty()) {
                log.info("No competency progress found for course {}", courseId);
                return Optional.empty();
            }

            List<String> lines = new ArrayList<>();
            lines.add("CompetencyId,CompetencyTitle,UserLogin,Progress,Confidence,LastModified");

            for (var progress : progressList) {
                String competencyId = progress.competencyId() != null ? String.valueOf(progress.competencyId()) : "";
                String competencyTitle = escapeCSV(progress.competencyTitle());
                String userLogin = progress.userLogin() != null ? progress.userLogin() : "";
                String progressValue = progress.progress() != null ? String.valueOf(progress.progress()) : "";
                String confidence = progress.confidence() != null ? String.valueOf(progress.confidence()) : "";
                String lastModified = progress.lastModifiedDate() != null ? DATE_FORMATTER.format(progress.lastModifiedDate().atZone(java.time.ZoneId.systemDefault())) : "";

                lines.add(String.join(",", competencyId, competencyTitle, userLogin, progressValue, confidence, lastModified));
            }

            Path outputFile = outputDir.resolve("competency-progress.csv");
            return Optional.of(writeLinesToFile(lines, outputFile));
        }
        catch (Exception e) {
            String message = "Failed to export competency progress for course " + courseId;
            log.error(message, e);
            errors.add(message);
            return Optional.empty();
        }
    }

    /**
     * Exports course learner profiles.
     *
     * @param courseId  the course ID
     * @param outputDir the directory to write to
     * @param errors    list to collect errors
     * @return path to the exported file
     */
    private Optional<Path> exportLearnerProfiles(long courseId, Path outputDir, List<String> errors) {
        if (learnerProfileApi.isEmpty()) {
            return Optional.empty();
        }

        try {
            var profiles = learnerProfileApi.get().findAllForExportByCourseId(courseId);

            if (profiles.isEmpty()) {
                log.info("No learner profiles found for course {}", courseId);
                return Optional.empty();
            }

            List<String> lines = new ArrayList<>();
            lines.add("UserLogin,AimForGradeOrBonus,TimeInvestment,RepetitionIntensity");

            for (var profile : profiles) {
                String userLogin = profile.userLogin() != null ? profile.userLogin() : "";
                String aimForGradeOrBonus = profile.aimForGradeOrBonus() != null ? String.valueOf(profile.aimForGradeOrBonus()) : "";
                String timeInvestment = profile.timeInvestment() != null ? String.valueOf(profile.timeInvestment()) : "";
                String repetitionIntensity = profile.repetitionIntensity() != null ? String.valueOf(profile.repetitionIntensity()) : "";

                lines.add(String.join(",", userLogin, aimForGradeOrBonus, timeInvestment, repetitionIntensity));
            }

            Path outputFile = outputDir.resolve("learner-profiles.csv");
            return Optional.of(writeLinesToFile(lines, outputFile));
        }
        catch (Exception e) {
            String message = "Failed to export learner profiles for course " + courseId;
            log.error(message, e);
            errors.add(message);
            return Optional.empty();
        }
    }

    /**
     * Exports Iris chat sessions for the course.
     *
     * @param courseId  the course ID
     * @param outputDir the directory to write to
     * @param errors    list to collect errors
     * @return path to the exported file
     */
    private Optional<Path> exportIrisChatSessions(long courseId, Path outputDir, List<String> errors) {
        if (irisSettingsApi.isEmpty()) {
            return Optional.empty();
        }

        try {
            var sessions = irisSettingsApi.get().findCourseChatSessionsForExport(courseId);

            if (sessions.isEmpty()) {
                log.info("No Iris chat sessions found for course {}", courseId);
                return Optional.empty();
            }

            Path outputFile = outputDir.resolve("iris-sessions.json");
            objectMapper.writeValue(outputFile.toFile(), sessions);
            return Optional.of(outputFile);
        }
        catch (Exception e) {
            String message = "Failed to export Iris chat sessions for course " + courseId;
            log.error(message, e);
            errors.add(message);
            return Optional.empty();
        }
    }

    /**
     * Exports LLM token usage traces for the course.
     * Each trace may contain multiple requests, and each request is exported as a separate row.
     *
     * @param courseId  the course ID
     * @param outputDir the directory to write to
     * @param errors    list to collect errors
     * @return path to the exported file
     */
    private Optional<Path> exportLLMTokenUsageTraces(long courseId, Path outputDir, List<String> errors) {
        try {
            var traces = llmTokenUsageTraceRepository.findAllWithRequestsByCourseId(courseId);

            if (traces.isEmpty()) {
                log.info("No LLM token usage traces found for course {}", courseId);
                return Optional.empty();
            }

            List<String> lines = new ArrayList<>();
            lines.add("TraceId,ServiceType,UserId,ExerciseId,Time,Model,Pipeline,NumInputTokens,NumOutputTokens,CostPerMillionInputTokens,CostPerMillionOutputTokens");

            for (var trace : traces) {
                String traceId = String.valueOf(trace.getId());
                String serviceType = trace.getServiceType() != null ? trace.getServiceType().name() : "";
                String userId = trace.getUserId() != null ? String.valueOf(trace.getUserId()) : "";
                String exerciseId = trace.getExerciseId() != null ? String.valueOf(trace.getExerciseId()) : "";
                String time = trace.getTime() != null ? trace.getTime().format(DATE_FORMATTER) : "";

                // Export each request within the trace
                for (var request : trace.getLLMRequests()) {
                    String model = request.getModel() != null ? request.getModel() : "";
                    String pipeline = request.getServicePipelineId() != null ? request.getServicePipelineId() : "";
                    String numInputTokens = String.valueOf(request.getNumInputTokens());
                    String numOutputTokens = String.valueOf(request.getNumOutputTokens());
                    String costPerMillionInputTokens = String.valueOf(request.getCostPerMillionInputTokens());
                    String costPerMillionOutputTokens = String.valueOf(request.getCostPerMillionOutputTokens());

                    lines.add(String.join(",", traceId, serviceType, userId, exerciseId, time, model, pipeline, numInputTokens, numOutputTokens, costPerMillionInputTokens,
                            costPerMillionOutputTokens));
                }
            }

            Path outputFile = outputDir.resolve("llm-token-usage.csv");
            return Optional.of(writeLinesToFile(lines, outputFile));
        }
        catch (Exception e) {
            String message = "Failed to export LLM token usage traces for course " + courseId;
            log.error(message, e);
            errors.add(message);
            return Optional.empty();
        }
    }

    /**
     * Exports comprehensive course scores for all students.
     * Includes per-exercise breakdown, exercise type summaries, presentation points, overall scores, and grades.
     * Also includes statistics rows (max, average, median, standard deviation) at the end.
     *
     * @param courseId  the course ID
     * @param outputDir the directory to write to
     * @param errors    list to collect errors
     * @return path to the exported file
     */
    private Optional<Path> exportCourseScores(long courseId, Path outputDir, List<String> errors) {
        try {
            Course course = courseRepository.findByIdWithEagerExercisesElseThrow(courseId);
            Set<CourseGradeScoreDTO> gradeScores = studentParticipationRepository.findGradeScoresForAllExercisesForCourse(courseId);

            if (gradeScores.isEmpty()) {
                log.info("No course scores found for course {}", courseId);
                return Optional.empty();
            }

            // Get grading scale if it exists
            Optional<GradingScale> gradingScaleOpt = gradingScaleRepository.findByCourseId(courseId);

            // Get all exercises sorted by type and title
            List<Exercise> exercises = course.getExercises().stream().filter(e -> e.getIncludedInOverallScore() != IncludedInOverallScore.NOT_INCLUDED)
                    .sorted(Comparator.comparing((Exercise e) -> e.getExerciseType().name()).thenComparing(Exercise::getTitle)).toList();

            // Get all student IDs from the grade scores
            Set<Long> studentIds = gradeScores.stream().map(CourseGradeScoreDTO::userId).collect(Collectors.toSet());
            Map<Long, User> usersById = userRepository.findAllById(studentIds).stream().collect(Collectors.toMap(User::getId, u -> u));

            // Group scores by student
            Map<Long, Map<Long, Double>> scoresByStudentAndExercise = new HashMap<>();
            Map<Long, Double> presentationScoresByStudent = new HashMap<>();
            for (CourseGradeScoreDTO score : gradeScores) {
                scoresByStudentAndExercise.computeIfAbsent(score.userId(), _ -> new HashMap<>()).put(score.exerciseId(), score.score());
                if (score.presentationScore() != null && score.presentationScore() > 0) {
                    presentationScoresByStudent.merge(score.userId(), 1.0, Double::sum);
                }
            }

            // Build header
            List<String> headerParts = new ArrayList<>();
            headerParts.add("Name");
            headerParts.add("Username");
            headerParts.add("Email");
            headerParts.add("RegistrationNumber");

            // Add exercise columns
            for (Exercise exercise : exercises) {
                headerParts.add(escapeCSV(exercise.getTitle()) + " Points");
                headerParts.add(escapeCSV(exercise.getTitle()) + " Score");
            }

            // Check if presentation points are configured
            boolean hasPresentationPoints = course.getPresentationScore() != null && course.getPresentationScore() > 0;
            if (hasPresentationPoints) {
                headerParts.add("Presentation Score");
            }

            headerParts.add("Overall Points");
            headerParts.add("Overall Score");
            headerParts.add("Max Points");

            if (gradingScaleOpt.isPresent()) {
                headerParts.add("Grade");
                headerParts.add("Passed");
            }

            List<String> lines = new ArrayList<>();
            lines.add(String.join(",", headerParts));

            // Calculate max points from all exercises in the list (already filtered to exclude NOT_INCLUDED)
            double maxPoints = exercises.stream().mapToDouble(Exercise::getMaxPoints).sum();

            // Collect statistics data
            List<Double> allOverallPoints = new ArrayList<>();
            List<Double> allOverallScores = new ArrayList<>();
            Map<Long, List<Double>> pointsByExercise = new HashMap<>();

            // Build data rows for each student
            for (Long studentId : studentIds) {
                User user = usersById.get(studentId);
                if (user == null) {
                    continue;
                }

                Map<Long, Double> studentScores = scoresByStudentAndExercise.getOrDefault(studentId, Map.of());

                List<String> rowParts = new ArrayList<>();
                rowParts.add(escapeCSV(user.getName()));
                rowParts.add(user.getLogin());
                rowParts.add(user.getEmail() != null ? user.getEmail() : "");
                rowParts.add(user.getRegistrationNumber() != null ? user.getRegistrationNumber() : "");

                double totalPoints = 0.0;

                // Add exercise columns
                for (Exercise exercise : exercises) {
                    Double score = studentScores.get(exercise.getId());
                    double exercisePoints = 0.0;
                    double exerciseScore = 0.0;

                    if (score != null) {
                        exerciseScore = score;
                        exercisePoints = roundScoreSpecifiedByCourseSettings(score * 0.01 * exercise.getMaxPoints(), course);
                        // Add to total - exercises list is already filtered to exclude NOT_INCLUDED
                        totalPoints += exercisePoints;
                    }

                    rowParts.add(String.valueOf(exercisePoints));
                    rowParts.add(String.valueOf(exerciseScore));

                    // Collect for statistics
                    pointsByExercise.computeIfAbsent(exercise.getId(), _ -> new ArrayList<>()).add(exercisePoints);
                }

                // Presentation score
                if (hasPresentationPoints) {
                    double presentationScore = presentationScoresByStudent.getOrDefault(studentId, 0.0);
                    rowParts.add(String.valueOf(presentationScore));
                }

                double overallScore = maxPoints > 0 ? roundScoreSpecifiedByCourseSettings(totalPoints / maxPoints * 100.0, course) : 0.0;

                rowParts.add(String.valueOf(totalPoints));
                rowParts.add(String.valueOf(overallScore));
                rowParts.add(String.valueOf(maxPoints));

                // Collect for statistics
                allOverallPoints.add(totalPoints);
                allOverallScores.add(overallScore);

                // Grade if grading scale exists
                if (gradingScaleOpt.isPresent()) {
                    try {
                        GradeStep gradeStep = gradingScaleRepository.matchPercentageToGradeStep(overallScore, gradingScaleOpt.get());
                        rowParts.add(escapeCSV(gradeStep.getGradeName()));
                        rowParts.add(String.valueOf(gradeStep.getIsPassingGrade()));
                    }
                    catch (Exception e) {
                        rowParts.add("");
                        rowParts.add("");
                    }
                }

                lines.add(String.join(",", rowParts));
            }

            // Add statistics rows
            lines.add("");
            lines.add("---Statistics---");

            // Prepare data for statistics helper
            Map<Long, Double> maxPointsByExercise = exercises.stream().collect(Collectors.toMap(Exercise::getId, Exercise::getMaxPoints));
            int suffixCells = gradingScaleOpt.isPresent() ? 2 : 0;

            // Max row
            lines.add(String.join(",", createCourseMaxRow(exercises, maxPoints, hasPresentationPoints, course, gradingScaleOpt.isPresent())));

            // Average row
            lines.add(String.join(",", createCourseStatisticsRow("Average", exercises, pointsByExercise, maxPointsByExercise, StatisticsUtil::calculateMean, hasPresentationPoints,
                    allOverallPoints, allOverallScores, suffixCells)));

            // Median row
            lines.add(String.join(",", createCourseStatisticsRow("Median", exercises, pointsByExercise, maxPointsByExercise, StatisticsUtil::calculateMedian, hasPresentationPoints,
                    allOverallPoints, allOverallScores, suffixCells)));

            // Standard deviation row
            lines.add(String.join(",", createCourseStatisticsRow("Std Dev", exercises, pointsByExercise, maxPointsByExercise, StatisticsUtil::calculateStandardDeviation,
                    hasPresentationPoints, allOverallPoints, allOverallScores, suffixCells)));

            // Participant count row
            lines.add("Participants," + studentIds.size());

            Path outputFile = outputDir.resolve("course-scores.csv");
            return Optional.of(writeLinesToFile(lines, outputFile));
        }
        catch (Exception e) {
            String message = "Failed to export course scores for course " + courseId;
            log.error(message, e);
            errors.add(message);
            return Optional.empty();
        }
    }

    /**
     * Exports comprehensive exam scores from the provided ExamScoresDTO.
     * Provides detailed information including per-exercise-group breakdown,
     * grades, bonus information, and plagiarism verdicts.
     * Also includes statistics rows.
     * <p>
     * This method is public to allow CourseArchiveService to call it with pre-fetched exam data,
     * avoiding circular dependencies with ExamApi.
     *
     * @param examScores the exam scores data to export
     * @param outputDir  the directory to write to
     * @param errors     list to collect errors
     * @return list of paths to exported files (exam scores and grade distribution)
     */
    public List<Path> exportExamScoresFromData(ExamScoresDTO examScores, Path outputDir, List<String> errors) {
        List<Path> exportedFiles = new ArrayList<>();

        if (examScores == null || examScores.studentResults().isEmpty()) {
            log.info("No exam scores found for exam {}", examScores != null ? examScores.examId() : "null");
            return exportedFiles;
        }

        Long examId = examScores.examId();

        try {

            // Build header
            List<String> headerParts = new ArrayList<>();
            headerParts.add("Name");
            headerParts.add("Username");
            headerParts.add("Email");
            headerParts.add("RegistrationNumber");
            headerParts.add("Submitted");

            // Add exercise group columns
            for (ExamScoresDTO.ExerciseGroup exerciseGroup : examScores.exerciseGroups()) {
                headerParts.add(escapeCSV(exerciseGroup.title()) + " Exercise");
                headerParts.add(escapeCSV(exerciseGroup.title()) + " Points");
                headerParts.add(escapeCSV(exerciseGroup.title()) + " Score");
            }

            headerParts.add("Overall Points");
            headerParts.add("Overall Score");
            headerParts.add("Max Points");

            // Check for grading scale presence
            boolean hasGrade = examScores.studentResults().stream().anyMatch(sr -> sr.overallGrade() != null && !sr.overallGrade().isEmpty());
            boolean hasBonus = examScores.studentResults().stream().anyMatch(sr -> sr.gradeWithBonus() != null);
            boolean hasSecondCorrection = Boolean.TRUE.equals(examScores.hasSecondCorrectionAndStarted());

            if (hasGrade) {
                headerParts.add("Grade");
                headerParts.add("Passed");
            }

            if (hasSecondCorrection) {
                headerParts.add("First Correction Points");
                headerParts.add("First Correction Grade");
            }

            if (hasBonus) {
                headerParts.add("Bonus Source Points");
                headerParts.add("Bonus Grade");
                headerParts.add("Final Grade");
            }

            headerParts.add("Plagiarism Verdict");

            List<String> lines = new ArrayList<>();
            lines.add(String.join(",", headerParts));

            // Collect statistics data
            List<Double> allOverallPoints = new ArrayList<>();
            List<Double> submittedOverallPoints = new ArrayList<>();
            List<Double> passedOverallPoints = new ArrayList<>();
            Map<Long, List<Double>> pointsByExerciseGroup = new HashMap<>();

            int submittedCount = 0;
            int passedCount = 0;

            // Build data rows for each student
            final var points = examScores.maxPoints() != null ? String.valueOf(examScores.maxPoints()) : "";
            for (ExamScoresDTO.StudentResult studentResult : examScores.studentResults()) {
                List<String> rowParts = new ArrayList<>();
                rowParts.add(escapeCSV(studentResult.name()));
                rowParts.add(studentResult.login() != null ? studentResult.login() : "");
                rowParts.add(studentResult.email() != null ? studentResult.email() : "");
                rowParts.add(studentResult.registrationNumber() != null ? studentResult.registrationNumber() : "");
                rowParts.add(studentResult.submitted() != null ? String.valueOf(studentResult.submitted()) : "false");

                // Add exercise group columns
                for (ExamScoresDTO.ExerciseGroup exerciseGroup : examScores.exerciseGroups()) {
                    ExamScoresDTO.ExerciseResult exerciseResult = studentResult.exerciseGroupIdToExerciseResult() != null
                            ? studentResult.exerciseGroupIdToExerciseResult().get(exerciseGroup.id())
                            : null;

                    if (exerciseResult != null) {
                        rowParts.add(escapeCSV(exerciseResult.title()));
                        rowParts.add(exerciseResult.achievedPoints() != null ? String.valueOf(exerciseResult.achievedPoints()) : "0.0");
                        rowParts.add(exerciseResult.achievedScore() != null ? String.valueOf(exerciseResult.achievedScore()) : "0.0");

                        // Collect for statistics
                        if (exerciseResult.achievedPoints() != null) {
                            pointsByExerciseGroup.computeIfAbsent(exerciseGroup.id(), _ -> new ArrayList<>()).add(exerciseResult.achievedPoints());
                        }
                    }
                    else {
                        rowParts.add("");
                        rowParts.add("0.0");
                        rowParts.add("0.0");
                        pointsByExerciseGroup.computeIfAbsent(exerciseGroup.id(), _ -> new ArrayList<>()).add(0.0);
                    }
                }

                double overallPoints = studentResult.overallPointsAchieved() != null ? studentResult.overallPointsAchieved() : 0.0;
                double overallScore = studentResult.overallScoreAchieved() != null ? studentResult.overallScoreAchieved() : 0.0;

                rowParts.add(String.valueOf(overallPoints));
                rowParts.add(String.valueOf(overallScore));
                rowParts.add(points);

                // Collect for statistics
                allOverallPoints.add(overallPoints);
                if (Boolean.TRUE.equals(studentResult.submitted())) {
                    submittedCount++;
                    submittedOverallPoints.add(overallPoints);
                }
                if (Boolean.TRUE.equals(studentResult.hasPassed())) {
                    passedCount++;
                    passedOverallPoints.add(overallPoints);
                }

                if (hasGrade) {
                    rowParts.add(studentResult.overallGrade() != null ? escapeCSV(studentResult.overallGrade()) : "");
                    rowParts.add(studentResult.hasPassed() != null ? String.valueOf(studentResult.hasPassed()) : "");
                }

                if (hasSecondCorrection) {
                    rowParts.add(studentResult.overallPointsAchievedInFirstCorrection() != null ? String.valueOf(studentResult.overallPointsAchievedInFirstCorrection()) : "");
                    rowParts.add(studentResult.overallGradeInFirstCorrection() != null ? escapeCSV(studentResult.overallGradeInFirstCorrection()) : "");
                }

                if (hasBonus) {
                    if (studentResult.gradeWithBonus() != null) {
                        rowParts.add(
                                studentResult.gradeWithBonus().studentPointsOfBonusSource() != null ? String.valueOf(studentResult.gradeWithBonus().studentPointsOfBonusSource())
                                        : "");
                        rowParts.add(studentResult.gradeWithBonus().bonusGrade() != null ? escapeCSV(studentResult.gradeWithBonus().bonusGrade()) : "");
                        rowParts.add(studentResult.gradeWithBonus().finalGrade() != null ? escapeCSV(studentResult.gradeWithBonus().finalGrade()) : "");
                    }
                    else {
                        rowParts.add("");
                        rowParts.add("");
                        rowParts.add("");
                    }
                }

                rowParts.add(studentResult.mostSeverePlagiarismVerdict() != null ? studentResult.mostSeverePlagiarismVerdict().name() : "");

                lines.add(String.join(",", rowParts));
            }

            // Add statistics rows
            lines.add("");
            lines.add("---Statistics---");

            int totalStudents = examScores.studentResults().size();
            Integer maxPts = examScores.maxPoints();
            Double maxPointsDouble = maxPts != null ? maxPts.doubleValue() : null;

            // Counts
            lines.add("Registered," + totalStudents);
            lines.add("Submitted," + formatCountWithPercentage(submittedCount, totalStudents));
            if (hasGrade) {
                lines.add("Passed," + formatCountWithPercentage(passedCount, totalStudents));
            }

            // Prepare data for statistics rows
            List<Long> exerciseGroupIds = examScores.exerciseGroups().stream().map(ExamScoresDTO.ExerciseGroup::id).toList();
            Map<Long, Double> maxPointsByGroup = examScores.exerciseGroups().stream()
                    .collect(Collectors.toMap(ExamScoresDTO.ExerciseGroup::id, eg -> eg.maxPoints() != null ? eg.maxPoints() : 0.0));

            // Max row
            lines.add(String.join(",", createExamMaxRow(examScores, points)));

            // Average (All) row
            lines.add(String.join(",", createStatisticsRow("Average (All)", exerciseGroupIds, pointsByExerciseGroup, maxPointsByGroup, StatisticsUtil::calculateMean,
                    allOverallPoints, maxPointsDouble)));

            // Average (Submitted) row
            if (!submittedOverallPoints.isEmpty()) {
                lines.add(String.join(",", createSimpleOverallStatRow("Average (Submitted)", examScores.exerciseGroups().size() * 3, submittedOverallPoints, maxPointsDouble)));
            }

            // Average (Passed) row
            if (!passedOverallPoints.isEmpty()) {
                lines.add(String.join(",", createSimpleOverallStatRow("Average (Passed)", examScores.exerciseGroups().size() * 3, passedOverallPoints, maxPointsDouble)));
            }

            // Median row
            lines.add(String.join(",",
                    createStatisticsRow("Median", exerciseGroupIds, pointsByExerciseGroup, maxPointsByGroup, StatisticsUtil::calculateMedian, allOverallPoints, maxPointsDouble)));

            // Standard deviation row
            lines.add(String.join(",", createStatisticsRow("Std Dev", exerciseGroupIds, pointsByExerciseGroup, maxPointsByGroup, StatisticsUtil::calculateStandardDeviation,
                    allOverallPoints, maxPointsDouble)));

            String sanitizedExamTitle = examScores.title() != null ? examScores.title().replaceAll("[^a-zA-Z0-9-_]", "_") : "unnamed";
            Path outputFile = outputDir.resolve("exam-scores-" + examId + "-" + sanitizedExamTitle + ".csv");
            exportedFiles.add(writeLinesToFile(lines, outputFile));

            // Export grade distribution for this exam
            exportExamGradeDistribution(examId, examScores, outputDir, errors).ifPresent(exportedFiles::add);
        }
        catch (Exception e) {
            String message = "Failed to export exam scores for exam " + examId;
            log.error(message, e);
            errors.add(message);
        }

        return exportedFiles;
    }

    /**
     * Creates the "Max" statistics row for the exam scores export.
     *
     * @param examScores the exam scores data
     * @param points     the max points string
     * @return list of strings representing the max row
     */
    private static List<String> createExamMaxRow(ExamScoresDTO examScores, String points) {
        List<String> maxRow = new ArrayList<>();
        maxRow.add("Max");
        maxRow.addAll(Collections.nCopies(4, ""));
        for (ExamScoresDTO.ExerciseGroup exerciseGroup : examScores.exerciseGroups()) {
            maxRow.add("");
            maxRow.add(exerciseGroup.maxPoints() != null ? String.valueOf(exerciseGroup.maxPoints()) : "");
            maxRow.add("100.0");
        }
        maxRow.add(points);
        maxRow.add("100.0");
        maxRow.add("");
        return maxRow;
    }

    /**
     * Creates a simple statistics row that only shows overall statistics (used for subset averages like Submitted/Passed).
     *
     * @param label                   the row label
     * @param exerciseGroupEmptyCells number of empty cells for exercise group columns (3 per group)
     * @param overallPoints           list of overall points for the subset
     * @param maxPoints               max points for score calculation
     * @return the formatted row
     */
    private static List<String> createSimpleOverallStatRow(String label, int exerciseGroupEmptyCells, List<Double> overallPoints, Double maxPoints) {
        List<String> row = new ArrayList<>();
        row.add(label);
        row.addAll(Collections.nCopies(4, ""));
        row.addAll(Collections.nCopies(exerciseGroupEmptyCells, ""));
        double avgPoints = StatisticsUtil.calculateMean(overallPoints);
        row.add(String.format("%.2f", avgPoints));
        if (maxPoints != null && maxPoints > 0) {
            row.add(String.format("%.2f", avgPoints / maxPoints * 100.0));
        }
        row.add("");
        return row;
    }

    /**
     * Exports grade distribution for a course.
     * Includes both grade step distribution (if grading scale exists) and score interval distribution.
     *
     * @param courseId  the course ID
     * @param outputDir the directory to write to
     * @param errors    list to collect errors
     * @return path to the exported file, or empty if no grading scale exists
     */
    private Optional<Path> exportCourseGradeDistribution(long courseId, Path outputDir, List<String> errors) {
        try {
            Optional<GradingScale> gradingScaleOpt = gradingScaleRepository.findByCourseId(courseId);
            if (gradingScaleOpt.isEmpty()) {
                log.info("No grading scale found for course {}, skipping grade distribution export", courseId);
                return Optional.empty();
            }

            Course course = courseRepository.findByIdWithEagerExercisesElseThrow(courseId);
            Set<CourseGradeScoreDTO> gradeScores = studentParticipationRepository.findGradeScoresForAllExercisesForCourse(courseId);

            if (gradeScores.isEmpty()) {
                return Optional.empty();
            }

            GradingScale gradingScale = gradingScaleOpt.get();

            // Calculate overall scores for each student
            Set<Long> studentIds = gradeScores.stream().map(CourseGradeScoreDTO::userId).collect(Collectors.toSet());
            Map<Long, Map<Long, Double>> scoresByStudentAndExercise = new HashMap<>();
            for (CourseGradeScoreDTO score : gradeScores) {
                scoresByStudentAndExercise.computeIfAbsent(score.userId(), _ -> new HashMap<>()).put(score.exerciseId(), score.score());
            }

            // Include all exercises that are not explicitly excluded (NOT_INCLUDED)
            List<Exercise> exercises = course.getExercises().stream().filter(e -> e.getIncludedInOverallScore() != IncludedInOverallScore.NOT_INCLUDED).toList();

            double maxPoints = exercises.stream().mapToDouble(Exercise::getMaxPoints).sum();

            // Calculate overall scores
            List<Double> overallScores = new ArrayList<>();
            for (Long studentId : studentIds) {
                Map<Long, Double> studentScores = scoresByStudentAndExercise.getOrDefault(studentId, Map.of());
                double totalPoints = 0.0;
                for (Exercise exercise : exercises) {
                    Double score = studentScores.get(exercise.getId());
                    if (score != null) {
                        totalPoints += roundScoreSpecifiedByCourseSettings(score * 0.01 * exercise.getMaxPoints(), course);
                    }
                }
                double overallScore = maxPoints > 0 ? roundScoreSpecifiedByCourseSettings(totalPoints / maxPoints * 100.0, course) : 0.0;
                overallScores.add(overallScore);
            }

            List<String> lines = new ArrayList<>();
            lines.add("Type,Value,LowerBound,UpperBound,IsPassingGrade,StudentCount,Percentage");

            // Count students per grade and write distribution
            int totalStudents = overallScores.size();
            Map<String, Integer> gradeCountMap = countStudentsPerGrade(gradingScale, overallScores);
            writeGradeDistributionRows(lines, gradingScale, gradeCountMap, totalStudents);

            // Score interval distribution
            writeIntervalDistributionRows(lines, overallScores, totalStudents);

            Path outputFile = outputDir.resolve("course-grade-distribution.csv");
            return Optional.of(writeLinesToFile(lines, outputFile));
        }
        catch (Exception e) {
            String message = "Failed to export course grade distribution for course " + courseId;
            log.error(message, e);
            errors.add(message);
            return Optional.empty();
        }
    }

    /**
     * Exports grade distribution for an exam.
     * Includes both grade step distribution (if grading scale exists) and score interval distribution.
     *
     * @param examId     the exam ID
     * @param examScores the pre-calculated exam scores DTO
     * @param outputDir  the directory to write to
     * @param errors     list to collect errors
     * @return path to the exported file, or empty if no grading scale exists
     */
    private Optional<Path> exportExamGradeDistribution(Long examId, ExamScoresDTO examScores, Path outputDir, List<String> errors) {
        try {
            Optional<GradingScale> gradingScaleOpt = gradingScaleRepository.findByExamId(examId);
            if (gradingScaleOpt.isEmpty()) {
                log.info("No grading scale found for exam {}, skipping grade distribution export", examId);
                return Optional.empty();
            }

            GradingScale gradingScale = gradingScaleOpt.get();

            // Get overall scores from exam scores DTO
            List<Double> overallScores = examScores.studentResults().stream().map(sr -> sr.overallScoreAchieved() != null ? sr.overallScoreAchieved() : 0.0).toList();

            if (overallScores.isEmpty()) {
                return Optional.empty();
            }

            List<String> lines = new ArrayList<>();
            lines.add("Type,Value,LowerBound,UpperBound,IsPassingGrade,StudentCount,Percentage");

            // Count students per grade using the grade from the DTO (more accurate than recalculating)
            Map<String, Integer> gradeCountMap = new LinkedHashMap<>();
            gradingScale.getGradeSteps().forEach(step -> gradeCountMap.put(step.getGradeName(), 0));
            for (ExamScoresDTO.StudentResult studentResult : examScores.studentResults()) {
                if (studentResult.overallGrade() != null && !studentResult.overallGrade().isEmpty()) {
                    gradeCountMap.merge(studentResult.overallGrade(), 1, Integer::sum);
                }
            }

            int totalStudents = overallScores.size();
            writeGradeDistributionRows(lines, gradingScale, gradeCountMap, totalStudents);

            // Score interval distribution
            writeIntervalDistributionRows(lines, overallScores, totalStudents);

            String sanitizedExamTitle = examScores.title().replaceAll("[^a-zA-Z0-9-_]", "_");
            Path outputFile = outputDir.resolve("exam-" + examId + "-" + sanitizedExamTitle + "-grade-distribution.csv");
            return Optional.of(writeLinesToFile(lines, outputFile));
        }
        catch (Exception e) {
            String message = "Failed to export exam grade distribution for exam " + examId;
            log.error(message, e);
            errors.add(message);
            return Optional.empty();
        }
    }

    /**
     * Exports tutorial group registrations for the course.
     *
     * @param courseId  the course ID
     * @param outputDir the directory to write to
     * @param errors    list to collect errors
     * @return path to the exported file
     */
    private Optional<Path> exportTutorialGroupRegistrations(long courseId, Path outputDir, List<String> errors) {
        if (tutorialGroupApi.isEmpty()) {
            return Optional.empty();
        }

        try {
            var registrations = tutorialGroupApi.get().findAllRegistrationsByCourseId(courseId);

            if (registrations.isEmpty()) {
                log.info("No tutorial group registrations found for course {}", courseId);
                return Optional.empty();
            }

            List<String> lines = new ArrayList<>();
            lines.add("TutorialGroupId,TutorialGroupTitle,StudentLogin,StudentName,RegistrationType");

            for (TutorialGroupRegistration registration : registrations) {
                String tutorialGroupId = registration.getTutorialGroup() != null ? String.valueOf(registration.getTutorialGroup().getId()) : "";
                String tutorialGroupTitle = registration.getTutorialGroup() != null ? escapeCSV(registration.getTutorialGroup().getTitle()) : "";
                String studentLogin = registration.getStudent() != null ? registration.getStudent().getLogin() : "";
                String studentName = registration.getStudent() != null ? escapeCSV(registration.getStudent().getName()) : "";
                String registrationType = registration.getType() != null ? registration.getType().name() : "";

                lines.add(String.join(",", tutorialGroupId, tutorialGroupTitle, studentLogin, studentName, registrationType));
            }

            Path outputFile = outputDir.resolve("tutorial-group-registrations.csv");
            return Optional.of(writeLinesToFile(lines, outputFile));
        }
        catch (Exception e) {
            String message = "Failed to export tutorial group registrations for course " + courseId;
            log.error(message, e);
            errors.add(message);
            return Optional.empty();
        }
    }

    private Submission getLatestSubmission(Participation participation) {
        if (participation.getSubmissions() == null || participation.getSubmissions().isEmpty()) {
            return null;
        }
        return participation.getSubmissions().stream().filter(Objects::nonNull)
                .max(Comparator.comparing(Submission::getSubmissionDate, Comparator.nullsFirst(Comparator.naturalOrder()))).orElse(null);
    }

    private Result getLatestResult(Submission submission) {
        if (submission.getResults() == null || submission.getResults().isEmpty()) {
            return null;
        }
        return submission.getResults().stream().filter(Objects::nonNull).max(Comparator.comparing(Result::getCompletionDate, Comparator.nullsFirst(Comparator.naturalOrder())))
                .orElse(null);
    }

    private String formatFeedbacks(List<Feedback> feedbacks) {
        return feedbacks.stream().map(f -> {
            String text = f.getDetailText() != null ? f.getDetailText() : (f.getText() != null ? f.getText() : "");
            String credits = f.getCredits() != null ? " (" + f.getCredits() + " pts)" : "";
            return text + credits;
        }).collect(Collectors.joining(" | "));
    }

    private String escapeCSV(String value) {
        if (value == null) {
            return "";
        }
        // Escape quotes and wrap in quotes if contains comma, quote, or newline
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private Path writeLinesToFile(List<String> lines, Path outputFile) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(outputFile, StandardCharsets.UTF_8)) {
            for (String line : lines) {
                writer.write(line);
                writer.write(System.lineSeparator());
            }
        }
        return outputFile;
    }

    // ==================== Helper Methods for Statistics and Distribution ====================

    /**
     * Creates a statistics row with a label and values computed by applying a function to grouped data.
     *
     * @param label             the row label (e.g., "Average", "Median")
     * @param groups            the list of group identifiers (exercise IDs or exercise group IDs)
     * @param pointsByGroup     map of group ID to list of points
     * @param maxPointsByGroup  map of group ID to max points for score calculation
     * @param statisticFunction function to calculate the statistic (mean, median, stddev)
     * @param overallPoints     list of overall points for the summary columns
     * @param maxPoints         max points for overall score calculation (null to skip)
     * @return the formatted statistics row
     */
    private List<String> createStatisticsRow(String label, List<Long> groups, Map<Long, List<Double>> pointsByGroup, Map<Long, Double> maxPointsByGroup,
            Function<List<Double>, Double> statisticFunction, List<Double> overallPoints, Double maxPoints) {
        List<String> row = new ArrayList<>();
        row.add(label);
        row.addAll(Collections.nCopies(4, ""));

        for (Long groupId : groups) {
            List<Double> groupPoints = pointsByGroup.getOrDefault(groupId, List.of());
            double statValue = statisticFunction.apply(groupPoints);
            row.add(""); // Empty for exercise name column in exam export
            row.add(String.format("%.2f", statValue));
            Double groupMax = maxPointsByGroup.get(groupId);
            double scoreValue = groupMax != null && groupMax > 0 ? statValue / groupMax * 100.0 : 0.0;
            row.add(String.format("%.2f", scoreValue));
        }

        // Overall statistics
        double overallStat = statisticFunction.apply(overallPoints);
        row.add(String.format("%.2f", overallStat));
        if (maxPoints != null && maxPoints > 0) {
            row.add(String.format("%.2f", overallStat / maxPoints * 100.0));
        }
        row.addAll(Collections.nCopies(1, ""));

        return row;
    }

    /**
     * Writes grade distribution rows to the lines list.
     *
     * @param lines         the list of CSV lines to append to
     * @param gradingScale  the grading scale containing grade steps
     * @param gradeCountMap map of grade name to student count
     * @param totalStudents total number of students for percentage calculation
     */
    private void writeGradeDistributionRows(List<String> lines, GradingScale gradingScale, Map<String, Integer> gradeCountMap, int totalStudents) {
        Map<String, GradeStep> gradeStepMap = gradingScale.getGradeSteps().stream()
                .collect(Collectors.toMap(GradeStep::getGradeName, step -> step, (a, _) -> a, LinkedHashMap::new));

        // Sort by lower bound percentage (descending)
        List<String> sortedGrades = gradingScale.getGradeSteps().stream().sorted(Comparator.comparing(GradeStep::getLowerBoundPercentage).reversed()).map(GradeStep::getGradeName)
                .toList();

        for (String gradeName : sortedGrades) {
            GradeStep step = gradeStepMap.get(gradeName);
            int count = gradeCountMap.getOrDefault(gradeName, 0);
            double percentage = totalStudents > 0 ? (double) count / totalStudents * 100.0 : 0.0;
            lines.add(String.join(",", "GRADE", escapeCSV(gradeName), String.valueOf(step.getLowerBoundPercentage()), String.valueOf(step.getUpperBoundPercentage()),
                    String.valueOf(step.getIsPassingGrade()), String.valueOf(count), String.format("%.1f", percentage)));
        }
    }

    /**
     * Writes score interval distribution rows (0-10%, 10-20%, ..., 90-100%) to the lines list.
     *
     * @param lines         the list of CSV lines to append to
     * @param overallScores list of overall score percentages
     * @param totalStudents total number of students for percentage calculation
     */
    private void writeIntervalDistributionRows(List<String> lines, List<Double> overallScores, int totalStudents) {
        lines.add("");
        int[] intervalCounts = new int[10];
        for (Double score : overallScores) {
            int interval = Math.min(Math.max((int) (score / 10), 0), 9);
            intervalCounts[interval]++;
        }

        for (int i = 0; i < 10; i++) {
            int lowerBound = i * 10;
            int upperBound = (i + 1) * 10;
            String intervalName = lowerBound + "-" + upperBound + "%";
            double percentage = totalStudents > 0 ? (double) intervalCounts[i] / totalStudents * 100.0 : 0.0;
            lines.add(String.join(",", "INTERVAL", intervalName, String.valueOf(lowerBound), String.valueOf(upperBound), "", String.valueOf(intervalCounts[i]),
                    String.format("%.1f", percentage)));
        }
    }

    /**
     * Counts students per grade step based on their overall scores.
     *
     * @param gradingScale  the grading scale to match against
     * @param overallScores list of overall score percentages
     * @return map of grade name to student count
     */
    private Map<String, Integer> countStudentsPerGrade(GradingScale gradingScale, List<Double> overallScores) {
        Map<String, Integer> gradeCountMap = new LinkedHashMap<>();

        // Initialize all grades with 0
        gradingScale.getGradeSteps().forEach(step -> gradeCountMap.put(step.getGradeName(), 0));

        // Count students per grade
        for (Double score : overallScores) {
            try {
                GradeStep gradeStep = gradingScaleRepository.matchPercentageToGradeStep(score, gradingScale);
                gradeCountMap.merge(gradeStep.getGradeName(), 1, Integer::sum);
            }
            catch (Exception e) {
                // Score doesn't match any grade step - skip
            }
        }

        return gradeCountMap;
    }

    /**
     * Formats a count with percentage for display.
     *
     * @param count the count value
     * @param total the total for percentage calculation
     * @return formatted string like "42 (84.0%)"
     */
    private String formatCountWithPercentage(int count, int total) {
        String percentage = total > 0 ? String.format("%.1f", (double) count / total * 100.0) : "0.0";
        return count + " (" + percentage + "%)";
    }

    /**
     * Creates the "Max" statistics row for course scores export.
     *
     * @param exercises             list of exercises
     * @param maxPoints             total max points
     * @param hasPresentationPoints whether presentation points are configured
     * @param course                the course for presentation score
     * @param hasGradingScale       whether a grading scale exists
     * @return the formatted max row
     */
    private List<String> createCourseMaxRow(List<Exercise> exercises, double maxPoints, boolean hasPresentationPoints, Course course, boolean hasGradingScale) {
        List<String> maxRow = new ArrayList<>();
        maxRow.add("Max");
        maxRow.addAll(Collections.nCopies(3, ""));
        for (Exercise exercise : exercises) {
            maxRow.add(String.valueOf(exercise.getMaxPoints()));
            maxRow.add("100.0");
        }
        if (hasPresentationPoints) {
            maxRow.add(String.valueOf(course.getPresentationScore()));
        }
        maxRow.add(String.valueOf(maxPoints));
        maxRow.add("100.0");
        maxRow.add(String.valueOf(maxPoints));
        if (hasGradingScale) {
            maxRow.addAll(Collections.nCopies(2, ""));
        }
        return maxRow;
    }

    /**
     * Creates a statistics row for course scores export.
     *
     * @param label                 the row label (e.g., "Average", "Median")
     * @param exercises             list of exercises
     * @param pointsByExercise      map of exercise ID to list of points
     * @param maxPointsByExercise   map of exercise ID to max points
     * @param statisticFunction     function to calculate the statistic
     * @param hasPresentationPoints whether presentation points are configured
     * @param allOverallPoints      list of all overall points
     * @param allOverallScores      list of all overall scores
     * @param suffixEmptyCells      number of empty cells to append
     * @return the formatted statistics row
     */
    private List<String> createCourseStatisticsRow(String label, List<Exercise> exercises, Map<Long, List<Double>> pointsByExercise, Map<Long, Double> maxPointsByExercise,
            Function<List<Double>, Double> statisticFunction, boolean hasPresentationPoints, List<Double> allOverallPoints, List<Double> allOverallScores, int suffixEmptyCells) {
        List<String> row = new ArrayList<>();
        row.add(label);
        row.addAll(Collections.nCopies(3, ""));

        for (Exercise exercise : exercises) {
            List<Double> exercisePointsList = pointsByExercise.getOrDefault(exercise.getId(), List.of());
            double statPoints = statisticFunction.apply(exercisePointsList);
            Double exerciseMax = maxPointsByExercise.get(exercise.getId());
            double statScore = exerciseMax != null && exerciseMax > 0 ? statPoints / exerciseMax * 100.0 : 0.0;
            row.add(String.format("%.2f", statPoints));
            row.add(String.format("%.2f", statScore));
        }

        if (hasPresentationPoints) {
            row.add("");
        }

        row.add(String.format("%.2f", statisticFunction.apply(allOverallPoints)));
        row.add(String.format("%.2f", statisticFunction.apply(allOverallScores)));
        row.add("");

        row.addAll(Collections.nCopies(suffixEmptyCells, ""));

        return row;
    }
}
