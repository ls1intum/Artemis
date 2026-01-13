package de.tum.cit.aet.artemis.core.service.export;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.dto.score.ScoreDTO;
import de.tum.cit.aet.artemis.assessment.service.ParticipantScoreService;
import de.tum.cit.aet.artemis.atlas.api.CompetencyProgressApi;
import de.tum.cit.aet.artemis.atlas.api.LearnerProfileApi;
import de.tum.cit.aet.artemis.communication.repository.AnswerPostRepository;
import de.tum.cit.aet.artemis.communication.repository.PostRepository;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.repository.LLMTokenUsageTraceRepository;
import de.tum.cit.aet.artemis.exam.api.ExamRepositoryApi;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.repository.ParticipationRepository;
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

    private final ParticipantScoreService participantScoreService;

    private final Optional<CompetencyProgressApi> competencyProgressApi;

    private final Optional<LearnerProfileApi> learnerProfileApi;

    private final Optional<IrisSettingsApi> irisSettingsApi;

    private final Optional<TutorialGroupApi> tutorialGroupApi;

    private final Optional<ExamRepositoryApi> examRepositoryApi;

    private final ObjectMapper objectMapper;

    public CourseStudentDataExportService(ParticipationRepository participationRepository, PostRepository postRepository, AnswerPostRepository answerPostRepository,
            LLMTokenUsageTraceRepository llmTokenUsageTraceRepository, CourseRepository courseRepository, ParticipantScoreService participantScoreService,
            Optional<CompetencyProgressApi> competencyProgressApi, Optional<LearnerProfileApi> learnerProfileApi, Optional<IrisSettingsApi> irisSettingsApi,
            Optional<TutorialGroupApi> tutorialGroupApi, Optional<ExamRepositoryApi> examRepositoryApi) {
        this.participationRepository = participationRepository;
        this.postRepository = postRepository;
        this.answerPostRepository = answerPostRepository;
        this.llmTokenUsageTraceRepository = llmTokenUsageTraceRepository;
        this.courseRepository = courseRepository;
        this.participantScoreService = participantScoreService;
        this.competencyProgressApi = competencyProgressApi;
        this.learnerProfileApi = learnerProfileApi;
        this.irisSettingsApi = irisSettingsApi;
        this.tutorialGroupApi = tutorialGroupApi;
        this.examRepositoryApi = examRepositoryApi;

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

            // Export course scores (aggregated student scores)
            exportCourseScores(courseId, studentDataDir, errors).ifPresent(exportedFiles::add);

            // Export exam scores (aggregated student scores per exam)
            exportExamScores(courseId, studentDataDir, errors).forEach(exportedFiles::add);

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
     * Exports aggregated course scores for all students.
     * Uses the ParticipantScoreService to calculate scores consistently with the UI.
     *
     * @param courseId  the course ID
     * @param outputDir the directory to write to
     * @param errors    list to collect errors
     * @return path to the exported file
     */
    private Optional<Path> exportCourseScores(long courseId, Path outputDir, List<String> errors) {
        try {
            Course course = courseRepository.findByIdWithEagerExercisesElseThrow(courseId);
            List<ScoreDTO> scores = participantScoreService.calculateCourseScores(course);

            if (scores.isEmpty()) {
                log.info("No course scores found for course {}", courseId);
                return Optional.empty();
            }

            List<String> lines = new ArrayList<>();
            lines.add("StudentId,StudentLogin,PointsAchieved,ScoreAchieved,RegularPointsAchievable");

            for (ScoreDTO score : scores) {
                lines.add(String.join(",", String.valueOf(score.studentId()), score.studentLogin(), String.valueOf(score.pointsAchieved()), String.valueOf(score.scoreAchieved()),
                        String.valueOf(score.regularPointsAchievable())));
            }

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
     * Exports aggregated exam scores for all exams in the course.
     * Uses the ParticipantScoreService to calculate scores consistently with the UI.
     *
     * @param courseId  the course ID
     * @param outputDir the directory to write to
     * @param errors    list to collect errors
     * @return list of paths to exported files (one per exam)
     */
    private List<Path> exportExamScores(long courseId, Path outputDir, List<String> errors) {
        List<Path> exportedFiles = new ArrayList<>();

        if (examRepositoryApi.isEmpty()) {
            return exportedFiles;
        }

        try {
            Set<Long> examIds = examRepositoryApi.get().findExamIdsByCourseId(courseId);

            for (Long examId : examIds) {
                try {
                    // Get exam with all required data for score calculation
                    Exam exam = examRepositoryApi.get().findByIdWithExamUsersExerciseGroupsAndExercisesElseThrow(examId);
                    List<ScoreDTO> scores = participantScoreService.calculateExamScores(exam);

                    if (scores.isEmpty()) {
                        log.info("No exam scores found for exam {} in course {}", examId, courseId);
                        continue;
                    }

                    List<String> lines = new ArrayList<>();
                    lines.add("StudentId,StudentLogin,PointsAchieved,ScoreAchieved,RegularPointsAchievable");

                    for (ScoreDTO score : scores) {
                        lines.add(String.join(",", String.valueOf(score.studentId()), score.studentLogin(), String.valueOf(score.pointsAchieved()),
                                String.valueOf(score.scoreAchieved()), String.valueOf(score.regularPointsAchievable())));
                    }

                    String sanitizedExamTitle = exam.getTitle().replaceAll("[^a-zA-Z0-9-_]", "_");
                    Path outputFile = outputDir.resolve("exam-scores-" + examId + "-" + sanitizedExamTitle + ".csv");
                    exportedFiles.add(writeLinesToFile(lines, outputFile));
                }
                catch (Exception e) {
                    String message = "Failed to export exam scores for exam " + examId + " in course " + courseId;
                    log.error(message, e);
                    errors.add(message);
                }
            }
        }
        catch (Exception e) {
            String message = "Failed to find exams for course " + courseId;
            log.error(message, e);
            errors.add(message);
        }

        return exportedFiles;
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
}
