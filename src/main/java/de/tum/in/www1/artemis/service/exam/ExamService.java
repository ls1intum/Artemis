package de.tum.in.www1.artemis.service.exam;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;
import static de.tum.in.www1.artemis.service.util.RoundingUtil.roundScoreSpecifiedByCourseSettings;
import static java.time.ZonedDateTime.now;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Principal;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.audit.AuditEvent;
import org.springframework.boot.actuate.audit.AuditEventRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.BonusStrategy;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.FileUploadExercise;
import de.tum.in.www1.artemis.domain.FileUploadSubmission;
import de.tum.in.www1.artemis.domain.GradeStep;
import de.tum.in.www1.artemis.domain.GradingScale;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.Submission;
import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.domain.TextSubmission;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.ComplaintType;
import de.tum.in.www1.artemis.domain.enumeration.IncludedInOverallScore;
import de.tum.in.www1.artemis.domain.enumeration.NotificationType;
import de.tum.in.www1.artemis.domain.enumeration.SubmissionType;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.exam.ExerciseGroup;
import de.tum.in.www1.artemis.domain.exam.StudentExam;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismCase;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismVerdict;
import de.tum.in.www1.artemis.domain.quiz.QuizExercise;
import de.tum.in.www1.artemis.domain.quiz.QuizPool;
import de.tum.in.www1.artemis.domain.quiz.QuizSubmission;
import de.tum.in.www1.artemis.domain.quiz.QuizSubmittedAnswerCount;
import de.tum.in.www1.artemis.domain.submissionpolicy.LockRepositoryPolicy;
import de.tum.in.www1.artemis.repository.ComplaintRepository;
import de.tum.in.www1.artemis.repository.ComplaintResponseRepository;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.ExamRepository;
import de.tum.in.www1.artemis.repository.GradingScaleRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.QuizExerciseRepository;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.repository.StudentExamRepository;
import de.tum.in.www1.artemis.repository.StudentParticipationRepository;
import de.tum.in.www1.artemis.repository.SubmissionRepository;
import de.tum.in.www1.artemis.repository.SubmittedAnswerRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.repository.plagiarism.PlagiarismCaseRepository;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.BonusService;
import de.tum.in.www1.artemis.service.CourseScoreCalculationService;
import de.tum.in.www1.artemis.service.ExerciseDeletionService;
import de.tum.in.www1.artemis.service.TutorLeaderboardService;
import de.tum.in.www1.artemis.service.connectors.GitService;
import de.tum.in.www1.artemis.service.export.CourseExamExportService;
import de.tum.in.www1.artemis.service.messaging.InstanceMessageSendService;
import de.tum.in.www1.artemis.service.notifications.GroupNotificationService;
import de.tum.in.www1.artemis.service.plagiarism.PlagiarismCaseService.PlagiarismMapping;
import de.tum.in.www1.artemis.service.quiz.QuizPoolService;
import de.tum.in.www1.artemis.service.quiz.QuizResultService;
import de.tum.in.www1.artemis.service.util.TimeLogUtil;
import de.tum.in.www1.artemis.web.rest.dto.BonusExampleDTO;
import de.tum.in.www1.artemis.web.rest.dto.BonusResultDTO;
import de.tum.in.www1.artemis.web.rest.dto.BonusSourceResultDTO;
import de.tum.in.www1.artemis.web.rest.dto.DueDateStat;
import de.tum.in.www1.artemis.web.rest.dto.ExamChecklistDTO;
import de.tum.in.www1.artemis.web.rest.dto.ExamScoresDTO;
import de.tum.in.www1.artemis.web.rest.dto.SearchResultPageDTO;
import de.tum.in.www1.artemis.web.rest.dto.StatsForDashboardDTO;
import de.tum.in.www1.artemis.web.rest.dto.StudentExamWithGradeDTO;
import de.tum.in.www1.artemis.web.rest.dto.TutorLeaderboardDTO;
import de.tum.in.www1.artemis.web.rest.dto.pageablesearch.SearchTermPageableSearchDTO;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;
import de.tum.in.www1.artemis.web.rest.util.PageUtil;

/**
 * Service Implementation for managing exams.
 */
@Profile(PROFILE_CORE)
@Service
public class ExamService {

    private static final int EXAM_ACTIVE_DAYS = 7;

    private final QuizResultService quizResultService;

    @Value("${artemis.course-archives-path}")
    private Path examArchivesDirPath;

    private static final Logger log = LoggerFactory.getLogger(ExamService.class);

    private final UserRepository userRepository;

    private final StudentParticipationRepository studentParticipationRepository;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final QuizExerciseRepository quizExerciseRepository;

    private final ExamDateService examDateService;

    private final ExamLiveEventsService examLiveEventsService;

    private final InstanceMessageSendService instanceMessageSendService;

    private final ExamRepository examRepository;

    private final StudentExamRepository studentExamRepository;

    private final ComplaintRepository complaintRepository;

    private final ComplaintResponseRepository complaintResponseRepository;

    private final ResultRepository resultRepository;

    private final SubmissionRepository submissionRepository;

    private final TutorLeaderboardService tutorLeaderboardService;

    private final GitService gitService;

    private final CourseExamExportService courseExamExportService;

    private final GroupNotificationService groupNotificationService;

    private final GradingScaleRepository gradingScaleRepository;

    private final PlagiarismCaseRepository plagiarismCaseRepository;

    private final AuthorizationCheckService authorizationCheckService;

    private final BonusService bonusService;

    private final ExerciseDeletionService exerciseDeletionService;

    private final SubmittedAnswerRepository submittedAnswerRepository;

    private final AuditEventRepository auditEventRepository;

    private final CourseScoreCalculationService courseScoreCalculationService;

    private final CourseRepository courseRepository;

    private final QuizPoolService quizPoolService;

    private final ObjectMapper defaultObjectMapper;

    private static final boolean IS_TEST_RUN = false;

    private static final String NOT_ALLOWED_TO_ACCESS_THE_GRADE_SUMMARY = "You are not allowed to access the grade summary of a student exam ";

    public ExamService(ExamDateService examDateService, ExamRepository examRepository, StudentExamRepository studentExamRepository,
            InstanceMessageSendService instanceMessageSendService, TutorLeaderboardService tutorLeaderboardService, StudentParticipationRepository studentParticipationRepository,
            ComplaintRepository complaintRepository, ComplaintResponseRepository complaintResponseRepository, UserRepository userRepository,
            ProgrammingExerciseRepository programmingExerciseRepository, QuizExerciseRepository quizExerciseRepository, ExamLiveEventsService examLiveEventsService,
            ResultRepository resultRepository, SubmissionRepository submissionRepository, CourseExamExportService courseExamExportService, GitService gitService,
            GroupNotificationService groupNotificationService, GradingScaleRepository gradingScaleRepository, PlagiarismCaseRepository plagiarismCaseRepository,
            AuthorizationCheckService authorizationCheckService, BonusService bonusService, ExerciseDeletionService exerciseDeletionService,
            SubmittedAnswerRepository submittedAnswerRepository, AuditEventRepository auditEventRepository, CourseScoreCalculationService courseScoreCalculationService,
            CourseRepository courseRepository, QuizPoolService quizPoolService, QuizResultService quizResultService) {
        this.examDateService = examDateService;
        this.examRepository = examRepository;
        this.studentExamRepository = studentExamRepository;
        this.userRepository = userRepository;
        this.studentParticipationRepository = studentParticipationRepository;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.instanceMessageSendService = instanceMessageSendService;
        this.complaintRepository = complaintRepository;
        this.complaintResponseRepository = complaintResponseRepository;
        this.quizExerciseRepository = quizExerciseRepository;
        this.examLiveEventsService = examLiveEventsService;
        this.resultRepository = resultRepository;
        this.submissionRepository = submissionRepository;
        this.tutorLeaderboardService = tutorLeaderboardService;
        this.courseExamExportService = courseExamExportService;
        this.groupNotificationService = groupNotificationService;
        this.gitService = gitService;
        this.gradingScaleRepository = gradingScaleRepository;
        this.plagiarismCaseRepository = plagiarismCaseRepository;
        this.authorizationCheckService = authorizationCheckService;
        this.bonusService = bonusService;
        this.exerciseDeletionService = exerciseDeletionService;
        this.submittedAnswerRepository = submittedAnswerRepository;
        this.auditEventRepository = auditEventRepository;
        this.courseScoreCalculationService = courseScoreCalculationService;
        this.courseRepository = courseRepository;
        this.quizPoolService = quizPoolService;
        this.defaultObjectMapper = new ObjectMapper();
        this.quizResultService = quizResultService;
    }

    /**
     * Get one exam by id with exercise groups and exercises.
     * Also fetches the template and solution participation for programming exercises and questions for quiz exercises.
     *
     * @param examId      the id of the entity
     * @param withDetails determines whether additional parameters such as template and solution participation for programming exercises
     *                        and questions for the quiz should be loaded
     * @return the exam with exercise groups
     */
    @NotNull
    public Exam findByIdWithExerciseGroupsAndExercisesElseThrow(Long examId, boolean withDetails) {
        log.debug("Request to get exam {} with exercise groups (with details: {})", examId, withDetails);
        if (!withDetails) {
            return examRepository.findWithExerciseGroupsAndExercisesByIdOrElseThrow(examId);
        }
        else {
            return examRepository.findWithExerciseGroupsAndExercisesAndDetailsByIdOrElseThrow(examId);
        }
    }

    /**
     * Puts students, result, exerciseGroups, bonus and related plagiarism verdicts together for ExamScoresDTO
     * Also calculates the scores of the related bonus source course or exam if present.
     *
     * @param examId the id of the exam
     * @return return ExamScoresDTO with students, scores, exerciseGroups, bonus and related plagiarism verdicts for the exam
     */
    public ExamScoresDTO calculateExamScores(Long examId) {
        Exam exam = examRepository.findWithExerciseGroupsAndExercisesByIdOrElseThrow(examId);

        List<StudentParticipation> studentParticipations = studentParticipationRepository.findByExamIdWithSubmissionRelevantResult(examId); // without test run participations
        log.info("Try to find quiz submitted answer counts");
        List<QuizSubmittedAnswerCount> submittedAnswerCounts = studentParticipationRepository.findSubmittedAnswerCountForQuizzesInExam(examId);
        log.info("Found {} quiz submitted answer counts", submittedAnswerCounts.size());

        // Counts how many participants each exercise has
        Map<Long, Long> exerciseIdToNumberParticipations = studentParticipations.stream()
                .collect(Collectors.groupingBy(studentParticipation -> studentParticipation.getExercise().getId(), Collectors.counting()));

        List<PlagiarismCase> plagiarismCasesForStudent = plagiarismCaseRepository.findByExamId(exam.getId());
        var plagiarismMapping = PlagiarismMapping.createFromPlagiarismCases(plagiarismCasesForStudent);

        var exerciseGroups = new ArrayList<ExamScoresDTO.ExerciseGroup>();

        // Adding exercise group information to DTO
        for (ExerciseGroup exerciseGroup : exam.getExerciseGroups()) {
            // Find the maximum points for this exercise group
            OptionalDouble optionalMaxPointsGroup = exerciseGroup.getExercises().stream().mapToDouble(Exercise::getMaxPoints).max();
            Double maxPointsGroup = optionalMaxPointsGroup.orElse(0);

            // Counter for exerciseGroup participations. Is calculated by summing up the number of exercise participations
            long numberOfExerciseGroupParticipants = 0;
            var containedExercises = new ArrayList<ExamScoresDTO.ExerciseGroup.ExerciseInfo>();
            // Add information about exercise groups and exercises

            for (Exercise exercise : exerciseGroup.getExercises()) {
                Long participantsForExercise = exerciseIdToNumberParticipations.get(exercise.getId());
                // If no participation exists for an exercise then no entry exists in the map
                if (participantsForExercise == null) {
                    participantsForExercise = 0L;
                }
                numberOfExerciseGroupParticipants += participantsForExercise;
                containedExercises.add(new ExamScoresDTO.ExerciseGroup.ExerciseInfo(exercise.getId(), exercise.getTitle(), exercise.getMaxPoints(), participantsForExercise,
                        exercise.getClass().getSimpleName()));
            }
            var exerciseGroupDTO = new ExamScoresDTO.ExerciseGroup(exerciseGroup.getId(), exerciseGroup.getTitle(), maxPointsGroup, numberOfExerciseGroupParticipants,
                    containedExercises);
            exerciseGroups.add(exerciseGroupDTO);
        }

        // Adding registered student information to DTO
        Set<StudentExam> studentExams = studentExamRepository.findByExamId(examId); // fetched without test runs
        Optional<GradingScale> gradingScale = gradingScaleRepository.findByExamIdWithBonusFrom(examId);
        List<Long> studentIds = studentExams.stream().map(studentExam -> studentExam.getUser().getId()).toList();
        ExamBonusCalculator examBonusCalculator = createExamBonusCalculator(gradingScale, studentIds);

        var studentResults = new ArrayList<ExamScoresDTO.StudentResult>();

        for (StudentExam studentExam : studentExams) {
            // Adding student results information to DTO
            List<StudentParticipation> participationsOfStudent = studentParticipations.stream()
                    .filter(studentParticipation -> studentParticipation.getStudent().orElseThrow().getId().equals(studentExam.getUser().getId())).toList();
            var studentResult = calculateStudentResultWithGrade(studentExam, participationsOfStudent, exam, gradingScale, true, submittedAnswerCounts, plagiarismMapping,
                    examBonusCalculator);
            studentResults.add(studentResult);
        }

        // Updating exam information in DTO
        int numberOfStudentResults = studentResults.size();
        var averagePointsAchieved = 0.0;
        if (numberOfStudentResults != 0) {
            double sumOverallPoints = studentResults.stream().mapToDouble(ExamScoresDTO.StudentResult::overallPointsAchieved).sum();
            averagePointsAchieved = sumOverallPoints / numberOfStudentResults;
        }

        // the second correction has started if it is enabled in the exam and at least one exercise was started
        var hasSecondCorrectionAndStarted = exam.getNumberOfCorrectionRoundsInExam() > 1 && isSecondCorrectionEnabled(exam);
        return new ExamScoresDTO(exam.getId(), exam.getTitle(), exam.getExamMaxPoints(), averagePointsAchieved, hasSecondCorrectionAndStarted, exerciseGroups, studentResults);
    }

    private static boolean isSecondCorrectionEnabled(Exam exam) {
        return exam.getExerciseGroups().stream().flatMap(exerciseGroup -> exerciseGroup.getExercises().stream()).anyMatch(Exercise::getSecondCorrectionEnabled);
    }

    /**
     * Calculates max points, max bonus points and achieved points per exercise if the given studentExam is assessed.
     * Includes the corresponding grade and grade type as well if a GradingScale is set for the relevant exam.
     *
     * @param studentExam             a StudentExam instance that will have its points and grades calculated if it is assessed
     * @param participationsOfStudent StudentParticipation list for the given studentExam
     * @return Student Exam results with exam grade calculated if applicable
     */
    @NotNull
    public StudentExamWithGradeDTO calculateStudentResultWithGradeAndPoints(StudentExam studentExam, List<StudentParticipation> participationsOfStudent) {
        // load again from the database because the exam object of the student exam might not have all the properties we need
        var exam = examRepository.findByIdElseThrow(studentExam.getExam().getId());
        var gradingScale = gradingScaleRepository.findByExamIdWithBonusFrom(exam.getId());
        Long studentId = studentExam.getUser().getId();
        List<PlagiarismCase> plagiarismCasesForStudent = plagiarismCaseRepository.findByExamIdAndStudentId(exam.getId(), studentId);
        var plagiarismMapping = PlagiarismMapping.createFromPlagiarismCases(plagiarismCasesForStudent);
        ExamBonusCalculator examBonusCalculator = createExamBonusCalculator(gradingScale, List.of(studentId));
        var studentResult = calculateStudentResultWithGrade(studentExam, participationsOfStudent, exam, gradingScale, false, null, plagiarismMapping, examBonusCalculator);
        var exercises = studentExam.getExercises().stream().filter(Objects::nonNull).toList();
        var maxPoints = calculateMaxPointsSum(exercises, exam.getCourse());
        var maxBonusPoints = calculateMaxBonusPointsSum(exercises, exam.getCourse());
        var gradingType = gradingScale.map(GradingScale::getGradeType).orElse(null);
        var achievedPointsPerExercise = calculateAchievedPointsForExercises(participationsOfStudent, exam.getCourse(), plagiarismMapping);
        return new StudentExamWithGradeDTO(maxPoints, maxBonusPoints, gradingType, studentExam, studentResult, achievedPointsPerExercise);
    }

    @Nullable
    private ExamBonusCalculator createExamBonusCalculator(Optional<GradingScale> gradingScale, Collection<Long> studentIds) {
        if (gradingScale.isEmpty() || gradingScale.get().getBonusFrom().isEmpty()) {
            return null;
        }

        GradingScale bonusToGradingScale = gradingScale.get();
        var bonus = bonusToGradingScale.getBonusFrom().stream().findAny().orElseThrow();
        GradingScale sourceGradingScale = bonus.getSourceGradingScale();

        Map<Long, BonusSourceResultDTO> scoresMap = calculateBonusSourceStudentPoints(sourceGradingScale, studentIds);
        String bonusFromTitle = bonus.getSourceGradingScale().getTitle();
        BonusStrategy bonusStrategy = bonus.getBonusToGradingScale().getBonusStrategy();

        double tempSourceReachablePoints = sourceGradingScale.getMaxPoints();
        if (sourceGradingScale.getExam() == null && sourceGradingScale.getCourse() != null) {
            // fetch course with exercises to calculate reachable points
            Course course = courseRepository.findWithEagerExercisesById(sourceGradingScale.getCourse().getId());
            tempSourceReachablePoints = courseScoreCalculationService.calculateReachablePoints(sourceGradingScale, course.getExercises());
        }
        final double sourceReachablePoints = tempSourceReachablePoints;

        return (studentId, bonusToAchievedPoints) -> {
            BonusSourceResultDTO result = scoresMap != null ? scoresMap.get(studentId) : null;
            Double sourceAchievedPoints = 0.0;
            PlagiarismVerdict verdict = null;
            Integer presentationScoreThreshold = null;
            Double achievedPresentationScore = null;
            if (result != null) {
                sourceAchievedPoints = result.achievedPoints();
                verdict = result.mostSeverePlagiarismVerdict();
                achievedPresentationScore = result.achievedPresentationScore();
                presentationScoreThreshold = result.presentationScoreThreshold();
            }
            BonusExampleDTO bonusExample = bonusService.calculateGradeWithBonus(bonus, bonusToAchievedPoints, sourceAchievedPoints, sourceReachablePoints);
            String bonusGrade = null;
            if (result == null || !result.hasParticipated()) {
                bonusGrade = bonus.getSourceGradingScale().getNoParticipationGradeOrDefault();
            }
            else if (verdict == PlagiarismVerdict.PLAGIARISM) {
                bonusGrade = bonus.getSourceGradingScale().getPlagiarismGradeOrDefault();
            }
            else if (bonusExample.bonusGrade() != null) {
                bonusGrade = bonusExample.bonusGrade().toString();
            }

            return new BonusResultDTO(bonusStrategy, bonusFromTitle, bonusExample.studentPointsOfBonusSource(), bonusGrade, bonusExample.finalPoints(), bonusExample.finalGrade(),
                    verdict, achievedPresentationScore, presentationScoreThreshold);
        };
    }

    @Nullable
    private Map<Long, BonusSourceResultDTO> calculateBonusSourceStudentPoints(GradingScale sourceGradingScale, Collection<Long> studentIds) {
        try {
            if (sourceGradingScale.getCourse() != null) {
                return courseScoreCalculationService.calculateCourseScoresForExamBonusSource(sourceGradingScale.getCourse(), sourceGradingScale, studentIds);
            }
            else {
                return calculateExamScoresAsBonusSource(sourceGradingScale.getExam().getId(), studentIds);
            }
        }
        catch (AccessForbiddenException e) {
            // TODO: this is not a good implementation, we should check before if the user has access
            // The current user does not have access to the bonus exam or course, so they should see the grade without bonus.
            return null;
        }
    }

    private Map<Long, BonusSourceResultDTO> calculateExamScoresAsBonusSource(Long examId, Collection<Long> studentIds) {
        if (studentIds.size() == 1) {  // Optimize single student case by filtering in the database.
            Long studentId = studentIds.iterator().next();
            User targetUser = userRepository.findByIdWithGroupsAndAuthoritiesElseThrow(studentId);
            StudentExam studentExam = studentExamRepository.findWithExercisesByUserIdAndExamId(targetUser.getId(), examId, IS_TEST_RUN)
                    .orElseThrow(() -> new EntityNotFoundException("No student exam found for examId " + examId + " and userId " + studentId));

            StudentExamWithGradeDTO studentExamWithGradeDTO = getStudentExamGradesForSummary(targetUser, studentExam,
                    authorizationCheckService.isAtLeastInstructorInCourse(studentExam.getExam().getCourse(), targetUser));
            var studentResult = studentExamWithGradeDTO.studentResult();
            return Map.of(studentId, new BonusSourceResultDTO(studentResult.overallPointsAchieved(), studentResult.mostSeverePlagiarismVerdict(), null, null,
                    Boolean.TRUE.equals(studentResult.submitted())));
        }
        var scores = calculateExamScores(examId);
        var studentIdSet = new HashSet<>(studentIds);
        return scores.studentResults().stream().filter(studentResult -> studentIdSet.contains(studentResult.userId()))
                .collect(Collectors.toMap(ExamScoresDTO.StudentResult::userId, studentResult -> new BonusSourceResultDTO(studentResult.overallPointsAchieved(),
                        studentResult.mostSeverePlagiarismVerdict(), null, null, Boolean.TRUE.equals(studentResult.submitted()))));

    }

    /**
     * Return student exam result, aggregate points, assessment result for a student exam and grade calculations
     * if the exam is assessed.
     * <p>
     * See {@link StudentExamWithGradeDTO} for more explanation.
     *
     * @param targetUser                       the user who submitted the studentExam
     * @param studentExam                      the student exam to be evaluated
     * @param accessingUserIsAtLeastInstructor is passed to decide the access (e.g. for test runs access will be needed regardless of submission or published dates)
     * @return the student exam result with points and grade
     */
    public StudentExamWithGradeDTO getStudentExamGradesForSummary(User targetUser, StudentExam studentExam, boolean accessingUserIsAtLeastInstructor) {
        loadQuizExercisesForStudentExam(studentExam);
        boolean accessToSummaryAlwaysAllowed = studentExam.isTestRun() || accessingUserIsAtLeastInstructor;
        // check that the studentExam has been submitted, otherwise /student-exams/conduction should be used
        if (!Boolean.TRUE.equals(studentExam.isSubmitted()) && !accessToSummaryAlwaysAllowed) {
            throw new AccessForbiddenException(NOT_ALLOWED_TO_ACCESS_THE_GRADE_SUMMARY + "which was NOT submitted!");
        }
        if (!studentExam.areResultsPublishedYet() && !accessToSummaryAlwaysAllowed) {
            throw new AccessForbiddenException(NOT_ALLOWED_TO_ACCESS_THE_GRADE_SUMMARY + "before the release date of results");
        }

        // fetch participations, submissions and results and connect them to the studentExam
        fetchParticipationsSubmissionsAndResultsForExam(studentExam, targetUser);

        List<StudentParticipation> participations = studentExam.getExercises().stream().flatMap(exercise -> exercise.getStudentParticipations().stream()).toList();
        // fetch all submitted answers for quizzes
        submittedAnswerRepository.loadQuizSubmissionsSubmittedAnswers(participations);

        return calculateStudentResultWithGradeAndPoints(studentExam, participations);
    }

    /**
     * retrieves/calculates all the necessary grade information for the given student exam used in the data export
     *
     * @param studentExam the student exam for which the grade should be calculated
     * @return the student exam result with points and grade
     */
    public StudentExamWithGradeDTO getStudentExamGradeForDataExport(StudentExam studentExam) {
        loadQuizExercisesForStudentExam(studentExam);

        // fetch participations, submissions and results and connect them to the studentExam
        fetchParticipationsSubmissionsAndResultsForExam(studentExam, studentExam.getUser());

        List<StudentParticipation> participations = studentExam.getExercises().stream().filter(Objects::nonNull).flatMap(exercise -> exercise.getStudentParticipations().stream())
                .toList();
        // fetch all submitted answers for quizzes
        submittedAnswerRepository.loadQuizSubmissionsSubmittedAnswers(participations);
        return calculateStudentResultWithGradeAndPoints(studentExam, participations);
    }

    /**
     * Loads the quiz questions as is not possible to load them in a generic way with the entity graph used.
     * See {@link StudentParticipationRepository#findByStudentExamWithEagerSubmissionsResult}
     *
     * @param studentExam the studentExam for which to load exercises
     */
    public void loadQuizExercisesForStudentExam(StudentExam studentExam) {
        for (int i = 0; i < studentExam.getExercises().size(); i++) {
            var exercise = studentExam.getExercises().get(i);
            if (exercise instanceof QuizExercise) {
                // reload and replace the quiz exercise
                var quizExercise = quizExerciseRepository.findByIdWithQuestionsElseThrow(exercise.getId());
                // filter quiz solutions when the publish result date is not set (or when set before the publish result date)
                if (!(studentExam.areResultsPublishedYet() || studentExam.isTestRun())) {
                    quizExercise.filterForStudentsDuringQuiz();
                }
                studentExam.getExercises().set(i, quizExercise);
            }
        }
    }

    /**
     * For all exercises from the student exam, fetch participation, submissions & result for the current user.
     *
     * @param studentExam the student exam in question
     * @param currentUser logged-in user with groups and authorities
     */
    public void fetchParticipationsSubmissionsAndResultsForExam(StudentExam studentExam, User currentUser) {

        // 1st: fetch participations, submissions and results (a distinction for test runs, real exams and test exams is done within the following method)
        var participations = studentParticipationRepository.findByStudentExamWithEagerSubmissionsResult(studentExam, false);

        // 2nd: fetch all submitted answers for quizzes
        submittedAnswerRepository.loadQuizSubmissionsSubmittedAnswers(participations);

        boolean isAtLeastInstructor = authorizationCheckService.isAtLeastInstructorInCourse(studentExam.getExam().getCourse(), currentUser);

        // 3rd: connect & filter the exercises and student participations including the latest submission and results where necessary, connect all relevant associations
        for (Exercise exercise : studentExam.getExercises()) {
            // exercises can be null if multiple student exams exist for the same student/exam combination
            if (exercise != null) {
                filterParticipationForExercise(studentExam, exercise, participations, isAtLeastInstructor);
            }
        }
    }

    /**
     * Finds the participation in participations that belongs to the given exercise and filters all unnecessary and sensitive information.
     * This ensures all relevant associations are available.
     * Handles setting the participation results using {@link #setResultIfNecessary(StudentExam, StudentParticipation, boolean)}.
     * Filters sensitive information using {@link Exercise#filterSensitiveInformation()} and {@link QuizSubmission#filterForExam(boolean, boolean)} for quiz exercises.
     *
     * @param studentExam         the given student exam
     * @param exercise            the exercise for which the user participation should be filtered
     * @param participations      the set of participations, wherein to search for the relevant participation
     * @param isAtLeastInstructor flag for instructor access privileges
     */
    public void filterParticipationForExercise(StudentExam studentExam, Exercise exercise, List<StudentParticipation> participations, boolean isAtLeastInstructor) {
        // remove the unnecessary inner course attribute
        exercise.setCourse(null);
        if (!(exercise instanceof QuizExercise)) {
            // Note: quiz exercises are filtered below
            exercise.filterSensitiveInformation();
        }

        if (!isAtLeastInstructor) {
            // If the exerciseGroup (and the exam) will be filtered out, move example solution publication date to the exercise to preserve this information.
            exercise.setExampleSolutionPublicationDate(exercise.getExerciseGroup().getExam().getExampleSolutionPublicationDate());
            exercise.getExerciseGroup().setExercises(null);
            exercise.getExerciseGroup().setExam(null);
        }

        if (exercise instanceof ProgrammingExercise programmingExercise) {
            programmingExercise.setTestRepositoryUri(null);
        }

        // get user's participation for the exercise
        StudentParticipation participation = participations != null ? exercise.findParticipation(participations) : null;

        // add relevant submission (relevancy depends on InitializationState) with its result to participation
        if (participation != null) {

            // we might need this information for programming exercises with submission policy
            participation.setSubmissionCount(participation.getSubmissions().size());

            // set the locked property of the participation properly
            if (participation instanceof ProgrammingExerciseStudentParticipation programmingExerciseStudentParticipation
                    && exercise instanceof ProgrammingExercise programmingExercise) {
                var submissionPolicy = programmingExercise.getSubmissionPolicy();
                // in the unlikely case the student exam was already submitted, set all participations to locked
                if (Boolean.TRUE.equals(studentExam.isSubmitted()) || Boolean.TRUE.equals(studentExam.isEnded())) {
                    programmingExerciseStudentParticipation.setLocked(true);
                }
                else if (submissionPolicy != null && Boolean.TRUE.equals(submissionPolicy.isActive()) && submissionPolicy instanceof LockRepositoryPolicy) {
                    programmingExerciseStudentParticipation.setLocked(programmingExerciseStudentParticipation.getSubmissionCount() >= submissionPolicy.getSubmissionLimit());
                }
                else {
                    programmingExerciseStudentParticipation.setLocked(false);
                }
            }

            // only include the latest submission
            Optional<Submission> optionalLatestSubmission = participation.findLatestLegalOrIllegalSubmission();
            if (optionalLatestSubmission.isPresent()) {
                Submission latestSubmission = optionalLatestSubmission.get();
                latestSubmission.setParticipation(null);
                participation.setSubmissions(Set.of(latestSubmission));
                setResultIfNecessary(studentExam, participation, isAtLeastInstructor);

                if (exercise instanceof QuizExercise && latestSubmission instanceof QuizSubmission quizSubmission) {
                    // filter quiz solutions when the publishing result date is not set (or when set before the publish result date)
                    quizSubmission.filterForExam(studentExam.areResultsPublishedYet(), isAtLeastInstructor);
                }
            }
            // add participation into an array
            exercise.setStudentParticipations(Set.of(participation));
        }
        else {
            // To prevent LazyInitializationException.
            exercise.setStudentParticipations(Set.of());
        }
    }

    /**
     * Determines whether the student should see the result of the exam.
     * This is the case if the exam is started and not ended yet or if the results are already published.
     *
     * @param studentExam   The student exam
     * @param participation The participation of the student
     * @return true if the student should see the result, false otherwise
     */
    public static boolean shouldStudentSeeResult(StudentExam studentExam, StudentParticipation participation) {
        return (studentExam.getExam().isStarted() && !studentExam.isEnded() && participation instanceof ProgrammingExerciseStudentParticipation)
                || studentExam.areResultsPublishedYet();
    }

    /**
     * Helper method which attaches the result to its participation.
     * For direct automatic feedback during the exam conduction for {@link ProgrammingExercise}, we need to attach the results.
     * We also attach the result if the results are already published for the exam.
     * If no suitable Result is found for StudentParticipation, an empty Result set is assigned to prevent LazyInitializationException on future reads.
     * See {@link StudentExam#areResultsPublishedYet}
     *
     * @param studentExam         the given studentExam
     * @param participation       the given participation of the student
     * @param isAtLeastInstructor flag for instructor access privileges
     */
    private static void setResultIfNecessary(StudentExam studentExam, StudentParticipation participation, boolean isAtLeastInstructor) {
        // Only set the result during the exam for programming exercises (for direct automatic feedback) or after publishing the results
        boolean isStudentAllowedToSeeResult = shouldStudentSeeResult(studentExam, participation);
        Optional<Submission> latestSubmission = participation.findLatestSubmission();

        if (latestSubmission.isPresent()) {
            var lastSubmission = latestSubmission.get();
            if (isStudentAllowedToSeeResult || isAtLeastInstructor) {
                // Also set the latest result into the participation as the client expects it there for programming exercises
                Result latestResult = lastSubmission.getLastResult();
                if (latestResult != null) {
                    latestResult.setSubmission(lastSubmission);
                    latestResult.filterSensitiveInformation();
                }
                participation.setSubmissions(Set.of(lastSubmission));
            }
            lastSubmission.setResults(null);
        }
    }

    /**
     * Generates a StudentResult from the given studentExam and participations of the student by aggregating scores and points
     * achieved per exercise by the relevant student if the given studentExam is assessed.
     * Calculates the corresponding grade if a GradingScale is given.
     *
     * @param studentExam                    a StudentExam instance that will have its points and grades calculated if it is assessed
     * @param participationsOfStudent        StudentParticipation list for the given studentExam
     * @param exam                           the relevant exam
     * @param gradingScale                   optional GradingScale that will be used to set the grade type and the achieved grade if present
     * @param calculateFirstCorrectionPoints flag to determine whether to calculate the first correction results or not
     * @return exam result for a student who participated in the exam
     */
    private ExamScoresDTO.StudentResult calculateStudentResultWithGrade(StudentExam studentExam, List<StudentParticipation> participationsOfStudent, Exam exam,
            Optional<GradingScale> gradingScale, boolean calculateFirstCorrectionPoints, List<QuizSubmittedAnswerCount> quizSubmittedAnswerCounts,
            PlagiarismMapping plagiarismMapping, ExamBonusCalculator examBonusCalculator) {
        User user = studentExam.getUser();

        if (!Boolean.TRUE.equals(studentExam.isSubmitted())) {
            String noParticipationGrade = gradingScale.map(GradingScale::getNoParticipationGradeOrDefault).orElse(GradingScale.DEFAULT_NO_PARTICIPATION_GRADE);
            return new ExamScoresDTO.StudentResult(user.getId(), user.getName(), user.getEmail(), user.getLogin(), user.getRegistrationNumber(), studentExam.isSubmitted(), 0.0,
                    0.0, noParticipationGrade, noParticipationGrade, false, 0.0, null, null, null);
        }
        else if (plagiarismMapping.studentHasVerdict(user.getId(), PlagiarismVerdict.PLAGIARISM)) {
            String plagiarismGrade = gradingScale.map(GradingScale::getPlagiarismGradeOrDefault).orElse(GradingScale.DEFAULT_PLAGIARISM_GRADE);
            return new ExamScoresDTO.StudentResult(user.getId(), user.getName(), user.getEmail(), user.getLogin(), user.getRegistrationNumber(), studentExam.isSubmitted(), 0.0,
                    0.0, plagiarismGrade, plagiarismGrade, false, 0.0, null, null, PlagiarismVerdict.PLAGIARISM);
        }

        var overallPointsAchieved = 0.0;
        var overallScoreAchieved = 0.0;
        var overallPointsAchievedInFirstCorrection = 0.0;
        Map<Long, ExamScoresDTO.ExerciseResult> exerciseGroupIdToExerciseResult = new HashMap<>();
        var plagiarismCasesForStudent = plagiarismMapping.getPlagiarismCasesForStudent(user.getId());
        for (StudentParticipation studentParticipation : participationsOfStudent) {
            Exercise exercise = studentParticipation.getExercise();

            if (exercise == null) {
                continue;
            }
            // Relevant Result is already calculated
            if (studentParticipation.getResults() != null && !studentParticipation.getResults().isEmpty()) {
                Result relevantResult = studentParticipation.getResults().iterator().next();
                PlagiarismCase plagiarismCase = plagiarismCasesForStudent.get(exercise.getId());
                double plagiarismPointDeductionPercentage = plagiarismCase != null ? plagiarismCase.getVerdictPointDeduction() : 0.0;
                double achievedPoints = calculateAchievedPoints(exercise, relevantResult, exam.getCourse(), plagiarismPointDeductionPercentage);

                // points earned in NOT_INCLUDED exercises do not count towards the students result in the exam
                if (!exercise.getIncludedInOverallScore().equals(IncludedInOverallScore.NOT_INCLUDED)) {
                    overallPointsAchieved += achievedPoints;
                }

                // Collect points of first correction, if a second correction exists
                if (calculateFirstCorrectionPoints && exam.getNumberOfCorrectionRoundsInExam() == 2
                        && !exercise.getIncludedInOverallScore().equals(IncludedInOverallScore.NOT_INCLUDED)) {
                    var latestSubmission = studentParticipation.findLatestSubmission();
                    if (latestSubmission.isPresent()) {
                        Submission submission = latestSubmission.get();
                        // Check if second correction already started
                        if (submission.getManualResults().size() > 1) {
                            Result firstManualResult = submission.getFirstManualResult();
                            double achievedPointsInFirstCorrection = 0.0;
                            if (firstManualResult != null) {
                                achievedPointsInFirstCorrection = calculateAchievedPoints(exercise, firstManualResult, exam.getCourse(), plagiarismPointDeductionPercentage);
                            }
                            overallPointsAchievedInFirstCorrection += achievedPointsInFirstCorrection;
                        }
                    }
                }

                // Check whether the student attempted to solve the exercise
                boolean hasNonEmptySubmission = hasNonEmptySubmission(studentParticipation.getSubmissions(), exercise);
                // special handling for quizzes to avoid performance issues
                if (exercise instanceof QuizExercise && quizSubmittedAnswerCounts != null) {
                    hasNonEmptySubmission = hasNonEmptySubmissionInQuiz(studentParticipation, quizSubmittedAnswerCounts);
                }
                exerciseGroupIdToExerciseResult.put(exercise.getExerciseGroup().getId(), new ExamScoresDTO.ExerciseResult(exercise.getId(), exercise.getTitle(),
                        exercise.getMaxPoints(), relevantResult.getScore(), achievedPoints, hasNonEmptySubmission));
            }
        }

        // Round the points again to prevent floating point issues that might occur when summing up the exercise points (e.g. 0.3 + 0.3 + 0.3 = 0.8999999999999999)
        overallPointsAchieved = roundScoreSpecifiedByCourseSettings(overallPointsAchieved, exam.getCourse());

        var overallGrade = "";
        var overallGradeInFirstCorrection = "";
        var hasPassed = false;
        BonusResultDTO gradeWithBonus = null;

        if (exam.getExamMaxPoints() > 0) {
            overallScoreAchieved = (overallPointsAchieved / exam.getExamMaxPoints()) * 100.0;
            if (gradingScale.isPresent()) {
                // Calculate current student grade
                GradeStep studentGrade = gradingScaleRepository.matchPercentageToGradeStep(overallScoreAchieved, gradingScale.get().getId());
                var overallScoreAchievedInFirstCorrection = (overallPointsAchievedInFirstCorrection / exam.getExamMaxPoints()) * 100.0;
                GradeStep studentGradeInFirstCorrection = gradingScaleRepository.matchPercentageToGradeStep(overallScoreAchievedInFirstCorrection, gradingScale.get().getId());
                overallGrade = studentGrade.getGradeName();
                overallGradeInFirstCorrection = studentGradeInFirstCorrection.getGradeName();
                hasPassed = studentGrade.getIsPassingGrade();
                if (examBonusCalculator != null) {
                    gradeWithBonus = examBonusCalculator.calculateStudentGradesWithBonus(user.getId(), overallPointsAchieved);
                }
            }
        }
        PlagiarismVerdict mostSevereVerdict = null;
        if (!plagiarismCasesForStudent.isEmpty()) {
            var studentVerdictsFromExercises = plagiarismCasesForStudent.values().stream().map(PlagiarismCase::getVerdict).toList();
            mostSevereVerdict = PlagiarismVerdict.findMostSevereVerdict(studentVerdictsFromExercises);
        }
        return new ExamScoresDTO.StudentResult(user.getId(), user.getName(), user.getEmail(), user.getLogin(), user.getRegistrationNumber(), studentExam.isSubmitted(),
                overallPointsAchieved, overallScoreAchieved, overallGrade, overallGradeInFirstCorrection, hasPassed, overallPointsAchievedInFirstCorrection, gradeWithBonus,
                exerciseGroupIdToExerciseResult, mostSevereVerdict);
    }

    private boolean hasNonEmptySubmissionInQuiz(StudentParticipation studentParticipation, List<QuizSubmittedAnswerCount> quizSubmittedAnswerCounts) {
        // If an entry is NOT available, it means the quiz submission is empty, i.e.
        // If the participation is not contained in the list, it is empty, i.e. hasNonEmptySubmission is true when the participation is contained

        for (var quizSubmittedAnswerCount : quizSubmittedAnswerCounts) {
            if (quizSubmittedAnswerCount.participationId() == studentParticipation.getId()) {
                return true;
            }
        }
        return false;
    }

    /**
     * First rounds max points for each exercise according to their {@link IncludedInOverallScore} value and sums them up.
     *
     * @param exercises exercises to sum their max points, intended use case is passing all exercises in a {@link StudentExam}
     * @param course    supplies the rounding accuracy of scores
     * @return sum of rounded max points if exercises are given, else 0.0
     */
    private double calculateMaxPointsSum(List<Exercise> exercises, Course course) {
        if (exercises != null) {
            var exercisesIncluded = exercises.stream().filter(exercise -> exercise.getIncludedInOverallScore() == IncludedInOverallScore.INCLUDED_COMPLETELY);
            return roundScoreSpecifiedByCourseSettings(exercisesIncluded.map(Exercise::getMaxPoints).reduce(0.0, Double::sum), course);
        }
        return 0.0;
    }

    /**
     * First rounds max bonus points for each exercise according to their {@link IncludedInOverallScore} value and sums them up.
     *
     * @param exercises exercises to sum their bonus points, intended use case is passing all exercises in a {@link StudentExam}
     * @param course    supplies the rounding accuracy of scores
     * @return sum of rounded max bonus points if exercises are given, else 0.0
     */
    private double calculateMaxBonusPointsSum(List<Exercise> exercises, Course course) {
        if (exercises != null) {
            return roundScoreSpecifiedByCourseSettings(exercises.stream().map(this::calculateMaxBonusPoints).reduce(0.0, Double::sum), course);
        }
        return 0.0;
    }

    /**
     * Gets max bonus points for the given exercise.
     * - If the exercise is included completely, returns max bonus points
     * - If the exercise is included as a bonus, returns max points
     * - If the exercise is not included, returns 0.0
     *
     * @param exercise the exercise that the points will be read from
     * @return max bonus points for the exercise retrieved according to the conditions above
     */
    private double calculateMaxBonusPoints(Exercise exercise) {
        return switch (exercise.getIncludedInOverallScore()) {
            case INCLUDED_COMPLETELY -> exercise.getBonusPoints();
            case INCLUDED_AS_BONUS -> exercise.getMaxPoints();
            case NOT_INCLUDED -> 0.0;
        };
    }

    /**
     * Calculates and rounds the points achieved by a student for a given exercise with the given result.
     * <p>
     * Note: It is important that we round on the individual exercise level first and then sum up.
     * This is necessary so that the student arrives at the same overall result when doing their own recalculation.
     * Let's assume that the student achieved 1.05 points in each of 5 exercises.
     * In the client, these are now displayed rounded as 1.1 points.
     * If the student adds up the displayed points, they get a total of 5.5 points.
     * In order to get the same total result as the student, we have to round before summing.
     *
     * @param exercise the relevant exercise
     * @param result   the result for the given exercise
     * @param course   course to specify number of decimal places to round
     * @return the rounded points according to the student's achieved score and max points of the exercise
     */
    private double calculateAchievedPoints(Exercise exercise, Result result, Course course, double plagiarismPointDeductionPercentage) {
        if (result != null && result.getScore() != null) {
            double achievedPoints = roundScoreSpecifiedByCourseSettings(exercise.getMaxPoints() * result.getScore() / 100.0, course);
            if (plagiarismPointDeductionPercentage > 0.0) {
                achievedPoints = roundScoreSpecifiedByCourseSettings(achievedPoints * (100.0 - plagiarismPointDeductionPercentage) / 100.0, course);
            }
            return achievedPoints;
        }
        return 0.0;
    }

    private Map<Long, Double> calculateAchievedPointsForExercises(List<StudentParticipation> participationsOfStudent, Course course, PlagiarismMapping plagiarismMapping) {
        return participationsOfStudent.stream().collect(Collectors.toMap(participation -> participation.getExercise().getId(), participation -> {
            PlagiarismCase plagiarismCase = plagiarismMapping.getPlagiarismCase(participation.getStudent().orElseThrow().getId(), participation.getExercise().getId());
            double plagiarismPointDeductionPercentage = plagiarismCase != null ? plagiarismCase.getVerdictPointDeduction() : 0.0;

            return calculateAchievedPoints(participation.getExercise(), participation.getSubmissions().stream().flatMap(sub -> sub.getResults().stream()).findFirst().orElse(null),
                    course, plagiarismPointDeductionPercentage);
        }));
    }

    /**
     * Checks whether one of the submissions is not empty
     *
     * @param submissions Submissions to check
     * @param exercise    Exercise of the submissions
     * @return true if at least one submission is not empty else false
     */
    private boolean hasNonEmptySubmission(Set<Submission> submissions, Exercise exercise) {
        if (exercise instanceof ProgrammingExercise) {
            return submissions.stream().anyMatch(submission -> submission.getType() == SubmissionType.MANUAL);
        }
        else if (exercise instanceof FileUploadExercise) {
            FileUploadSubmission textSubmission = (FileUploadSubmission) submissions.iterator().next();
            return textSubmission.getFilePath() != null && !textSubmission.getFilePath().isEmpty();
        }
        else if (exercise instanceof TextExercise) {
            TextSubmission textSubmission = (TextSubmission) submissions.iterator().next();
            return textSubmission.getText() != null && !textSubmission.getText().isBlank();
        }
        else if (exercise instanceof ModelingExercise) {
            ModelingSubmission modelingSubmission = (ModelingSubmission) submissions.iterator().next();
            try {
                return !modelingSubmission.isEmpty(this.defaultObjectMapper);
            }
            catch (Exception e) {
                // Then the student most likely submitted something which breaks the model, if parsing fails
                return true;
            }
        }
        else if (exercise instanceof QuizExercise) {
            // NOTE: due to performance concerns, this is handled differently, search for quizSubmittedAnswerCounts to find out more
            return true;
        }
        else {
            throw new IllegalArgumentException("The exercise type of the exercise with id " + exercise.getId() + " is not supported");
        }
    }

    /**
     * Validates exercise settings.
     *
     * @param exam exam which is validated
     * @throws BadRequestAlertException an exception if the exam is not configured correctly
     */
    public void validateForStudentExamGeneration(Exam exam) throws BadRequestAlertException {
        List<ExerciseGroup> exerciseGroups = exam.getExerciseGroups();
        long numberOfExercises = exam.getNumberOfExercisesInExam() != null ? exam.getNumberOfExercisesInExam() : 0;
        long numberOfOptionalExercises = numberOfExercises - exerciseGroups.stream().filter(ExerciseGroup::getIsMandatory).count();

        // Ensure that all exercise groups have at least one exercise
        for (ExerciseGroup exerciseGroup : exam.getExerciseGroups()) {
            if (exerciseGroup.getExercises().isEmpty()) {
                throw new BadRequestAlertException("All exercise groups must have at least one exercise", "Exam", "artemisApp.exam.validation.atLeastOneExercisePerExerciseGroup");
            }
        }

        // Check that numberOfExercisesInExam is set
        if (exam.getNumberOfExercisesInExam() == null) {
            throw new BadRequestAlertException("The number of exercises in the exam is not set.", "Exam", "artemisApp.exam.validation.numberOfExercisesInExamNotSet");
        }

        // Check that there are enough exercise groups
        if (exam.getExerciseGroups().size() < exam.getNumberOfExercisesInExam()) {
            throw new BadRequestAlertException("The number of exercise groups is too small", "Exam", "artemisApp.exam.validation.tooFewExerciseGroups");
        }

        // Check that there are not too much mandatory exercise groups
        if (numberOfOptionalExercises < 0) {
            throw new BadRequestAlertException("The number of mandatory exercise groups is too large", "Exam", "artemisApp.exam.validation.tooManyMandatoryExerciseGroups");
        }

        // Ensure that all exercises in an exercise group have the same meaning for the exam score calculation
        for (ExerciseGroup exerciseGroup : exam.getExerciseGroups()) {
            Set<IncludedInOverallScore> meaningsForScoreCalculation = exerciseGroup.getExercises().stream().map(Exercise::getIncludedInOverallScore).collect(Collectors.toSet());
            if (meaningsForScoreCalculation.size() > 1) {
                throw new BadRequestAlertException("All exercises in an exercise group must have the same meaning for the exam score", "Exam",
                        "artemisApp.exam.validation.allExercisesInExerciseGroupOfSameIncludedType");
            }
        }

        // Check that the exam max points is set
        if (exam.getExamMaxPoints() == 0) {
            throw new BadRequestAlertException("The exam max points can not be 0.", "Exam", "artemisApp.exam.validation.maxPointsNotSet");
        }

        // Ensure that all exercises in an exercise group have the same amount of max points and max bonus points
        for (ExerciseGroup exerciseGroup : exam.getExerciseGroups()) {
            Set<Double> allMaxPoints = exerciseGroup.getExercises().stream().map(Exercise::getMaxPoints).collect(Collectors.toSet());
            Set<Double> allBonusPoints = exerciseGroup.getExercises().stream().map(Exercise::getBonusPoints).collect(Collectors.toSet());

            if (allMaxPoints.size() > 1 || allBonusPoints.size() > 1) {
                throw new BadRequestAlertException("All exercises in an exercise group need to give the same amount of points", "Exam",
                        "artemisApp.exam.validation.allExercisesInExerciseGroupGiveSameNumberOfPoints");
            }
        }

        // Ensure that the sum of all max points of mandatory exercise groups is not bigger than the max points set in the exam
        // At this point we are already sure that each exercise group has at least one exercise, all exercises in the group have the same no of points
        // and all are of the same calculation type, therefore we can just use any as representation for the group here
        Double pointsReachableByMandatoryExercises = 0.0;
        Set<ExerciseGroup> mandatoryExerciseGroups = exam.getExerciseGroups().stream().filter(ExerciseGroup::getIsMandatory).collect(Collectors.toSet());
        for (ExerciseGroup exerciseGroup : mandatoryExerciseGroups) {
            Exercise groupRepresentativeExercise = exerciseGroup.getExercises().stream().findAny().orElseThrow();
            if (groupRepresentativeExercise.getIncludedInOverallScore().equals(IncludedInOverallScore.INCLUDED_COMPLETELY)) {
                pointsReachableByMandatoryExercises += groupRepresentativeExercise.getMaxPoints();
            }
        }
        if (pointsReachableByMandatoryExercises > exam.getExamMaxPoints()) {
            throw new BadRequestAlertException("Check that you set the exam max points correctly! The max points a student can earn in the mandatory exercise groups is too big",
                    "Exam", "artemisApp.exam.validation.tooManyMaxPoints");
        }

        // Ensure that the sum of all max points of all exercise groups is at least as big as the max points set in the exam
        Double pointsReachable = 0.0;
        for (ExerciseGroup exerciseGroup : exam.getExerciseGroups()) {
            Exercise groupRepresentativeExercise = exerciseGroup.getExercises().stream().findAny().orElseThrow();
            if (groupRepresentativeExercise.getIncludedInOverallScore().equals(IncludedInOverallScore.INCLUDED_COMPLETELY)) {
                pointsReachable += groupRepresentativeExercise.getMaxPoints();
            }
        }
        if (pointsReachable < exam.getExamMaxPoints()) {
            throw new BadRequestAlertException("Check that you set the exam max points correctly! The max points a student can earn in the exercise groups is too low", "Exam",
                    "artemisApp.exam.validation.tooFewMaxPoints");
        }
    }

    /**
     * Gets all statistics for the instructor checklist regarding an exam
     *
     * @param exam         the exam for which to get statistics for
     * @param isInstructor flag indicating if the requesting user is instructor
     * @return a examStatisticsDTO filled with all statistics regarding the exam
     */
    public ExamChecklistDTO getStatsForChecklist(Exam exam, boolean isInstructor) {
        log.info("getStatsForChecklist invoked for exam {}", exam.getId());
        int numberOfCorrectionRoundsInExam = exam.getNumberOfCorrectionRoundsInExam();
        long start = System.nanoTime();

        List<Long> numberOfComplaintsOpenByExercise = new ArrayList<>();
        List<Long> numberOfComplaintResponsesByExercise = new ArrayList<>();
        List<DueDateStat[]> numberOfAssessmentsFinishedOfCorrectionRoundsByExercise = new ArrayList<>();
        List<Long> numberOfParticipationsGeneratedByExercise = new ArrayList<>();
        List<Long> numberOfParticipationsForAssessmentGeneratedByExercise = new ArrayList<>();

        // loop over all exercises and retrieve all needed counts for the properties at once
        var exercises = getAllExercisesForExam(exam);
        exercises.forEach(exercise -> {
            // number of complaints open
            numberOfComplaintsOpenByExercise
                    .add(complaintRepository.countByResultSubmissionParticipationExerciseIdAndComplaintTypeIgnoreTestRuns(exercise.getId(), ComplaintType.COMPLAINT));

            if (log.isDebugEnabled()) {
                log.debug("StatsTimeLog: number of complaints open done in {} for exercise {}", TimeLogUtil.formatDurationFrom(start), exercise.getId());
            }
            // number of complaints finished
            numberOfComplaintResponsesByExercise
                    .add(complaintResponseRepository.countComplaintResponseByExerciseIdAndComplaintTypeAndSubmittedTimeIsNotNull(exercise.getId(), ComplaintType.COMPLAINT));

            if (log.isDebugEnabled()) {
                log.debug("StatsTimeLog: number of complaints finished done in {} for exercise {}", TimeLogUtil.formatDurationFrom(start), exercise.getId());
            }
            // number of assessments done
            if (numberOfCorrectionRoundsInExam > 0) {
                numberOfAssessmentsFinishedOfCorrectionRoundsByExercise
                        .add(resultRepository.countNumberOfFinishedAssessmentsForExamExerciseForCorrectionRounds(exercise, numberOfCorrectionRoundsInExam));

                if (log.isDebugEnabled()) {
                    log.debug("StatsTimeLog: number of assessments done in {} for exercise {}", TimeLogUtil.formatDurationFrom(start), exercise.getId());
                }
            }

            // get number of all generated participations
            numberOfParticipationsGeneratedByExercise.add(studentParticipationRepository.countParticipationsByExerciseIdAndTestRun(exercise.getId(), false));
            if (log.isDebugEnabled()) {
                log.debug("StatsTimeLog: number of generated participations in {} for exercise {}", TimeLogUtil.formatDurationFrom(start), exercise.getId());
            }
            if (!(exercise instanceof QuizExercise || AssessmentType.AUTOMATIC == exercise.getAssessmentType())) {
                numberOfParticipationsForAssessmentGeneratedByExercise.add(submissionRepository.countByExerciseIdSubmittedBeforeDueDateIgnoreTestRuns(exercise.getId()));
            }
        });

        long totalNumberOfComplaints = 0;
        long totalNumberOfComplaintResponse = 0;
        Long[] totalNumberOfAssessmentsFinished = new Long[numberOfCorrectionRoundsInExam];
        long totalNumberOfParticipationsGenerated = 0;
        long totalNumberOfParticipationsForAssessment = 0;

        if (isInstructor) {
            // sum up all counts for the different properties
            for (Long numberOfParticipations : numberOfParticipationsGeneratedByExercise) {
                totalNumberOfParticipationsGenerated += numberOfParticipations != null ? numberOfParticipations : 0;
            }
        }
        // sum up all counts for the different properties
        for (Long numberOfParticipationsForAssessment : numberOfParticipationsForAssessmentGeneratedByExercise) {
            totalNumberOfParticipationsForAssessment += numberOfParticipationsForAssessment != null ? numberOfParticipationsForAssessment : 0;
        }

        for (DueDateStat[] dateStats : numberOfAssessmentsFinishedOfCorrectionRoundsByExercise) {
            for (int i = 0; i < numberOfCorrectionRoundsInExam; i++) {
                if (totalNumberOfAssessmentsFinished[i] == null) {
                    totalNumberOfAssessmentsFinished[i] = 0L;
                }
                totalNumberOfAssessmentsFinished[i] += dateStats[i].inTime();
            }
        }
        for (Long numberOfComplaints : numberOfComplaintsOpenByExercise) {
            totalNumberOfComplaints += numberOfComplaints;
        }
        for (Long numberOfComplaintResponse : numberOfComplaintResponsesByExercise) {
            totalNumberOfComplaintResponse += numberOfComplaintResponse;
        }

        if (isInstructor) {
            // set number of student exams that have been generated
            long numberOfGeneratedStudentExams = examRepository.countGeneratedStudentExamsByExamWithoutTestRuns(exam.getId());

            if (log.isDebugEnabled()) {
                log.debug("StatsTimeLog: number of generated student exams done in {}", TimeLogUtil.formatDurationFrom(start));
            }

            // set number of test runs
            long numberOfTestRuns = studentExamRepository.countTestRunsByExamId(exam.getId());
            if (log.isDebugEnabled()) {
                log.debug("StatsTimeLog: number of test runs done in {}", TimeLogUtil.formatDurationFrom(start));
            }

            // check if all exercises have been prepared for all students;
            boolean exercisesPrepared = numberOfGeneratedStudentExams != 0
                    && (exam.getNumberOfExercisesInExam() * numberOfGeneratedStudentExams) == totalNumberOfParticipationsGenerated;

            // set started and submitted exam properties
            long numberOfStudentExamsStarted = studentExamRepository.countStudentExamsStartedByExamIdIgnoreTestRuns(exam.getId());
            if (log.isDebugEnabled()) {
                log.debug("StatsTimeLog: number of student exams started done in {}", TimeLogUtil.formatDurationFrom(start));
            }
            long numberOfStudentExamsSubmitted = studentExamRepository.countStudentExamsSubmittedByExamIdIgnoreTestRuns(exam.getId());
            if (log.isDebugEnabled()) {
                log.debug("StatsTimeLog: number of student exams submitted done in {}", TimeLogUtil.formatDurationFrom(start));
            }

            return new ExamChecklistDTO(numberOfGeneratedStudentExams, numberOfTestRuns, totalNumberOfAssessmentsFinished, totalNumberOfParticipationsForAssessment,
                    numberOfStudentExamsSubmitted, numberOfStudentExamsStarted, totalNumberOfComplaints, totalNumberOfComplaintResponse, exercisesPrepared, null, null);

        }

        boolean existsUnassessedQuizzes = submissionRepository.existsUnassessedQuizzesByExamId(exam.getId());
        boolean existsUnsubmittedExercises = submissionRepository.existsUnsubmittedExercisesByExamId(exam.getId());

        // For non-instructors, consider what limited information they should receive and adjust accordingly
        return new ExamChecklistDTO(totalNumberOfAssessmentsFinished, totalNumberOfParticipationsForAssessment, existsUnassessedQuizzes, existsUnsubmittedExercises);
    }

    /**
     * Evaluates all the quiz exercises of an exam (which must be loaded from database with exercise groups and exercises)
     *
     * @param exam the exam for which the quiz exercises should be evaluated (including exercise groups and exercises)
     * @return number of evaluated exercises
     */
    public Integer evaluateQuizExercises(Exam exam) {

        // Collect all quiz exercises for the given exam
        var quizExercises = getAllExercisesForExamByType(exam, QuizExercise.class);

        long start = System.nanoTime();
        log.debug("Evaluating {} quiz exercises in exam {}", quizExercises.size(), exam.getId());
        // Evaluate all quizzes for that exercise
        quizExercises.stream().map(Exercise::getId).forEach(quizResultService::evaluateQuizAndUpdateStatistics);
        if (log.isDebugEnabled()) {
            log.debug("Evaluated {} quiz exercises in exam {} in {}", quizExercises.size(), exam.getId(), TimeLogUtil.formatDurationFrom(start));
        }
        return quizExercises.size();
    }

    /**
     * Unlocks all repositories of an exam (only for external version control services)
     *
     * @param examId id of the exam for which the repositories should be unlocked
     * @return number of exercises for which the repositories are unlocked
     */
    public Integer unlockAllRepositories(Long examId) {
        var programmingExercises = getAllExercisesForExamByType(examId, ProgrammingExercise.class);
        // Run the runnable immediately so that the repositories are unlocked as fast as possible
        programmingExercises.stream().map(Exercise::getId).forEach(instanceMessageSendService::sendUnlockAllStudentRepositories);
        return programmingExercises.size();
    }

    private <T extends Exercise> Set<T> getAllExercisesForExamByType(Long examId, Class<T> exerciseType) {
        var exam = examRepository.findWithExerciseGroupsAndExercisesByIdOrElseThrow(examId);
        return getAllExercisesForExamByType(exam, exerciseType);
    }

    private static Set<Exercise> getAllExercisesForExam(Exam exam) {
        return getAllExercisesForExamByType(exam, Exercise.class);
    }

    private static <T extends Exercise> Set<T> getAllExercisesForExamByType(Exam exam, Class<T> exerciseType) {
        return exam.getExerciseGroups().stream().flatMap(exerciseGroup -> exerciseGroup.getExercises().stream())
                // this also filters potential null values
                .filter(exerciseType::isInstance).map(exerciseType::cast).collect(Collectors.toSet());
    }

    /**
     * Locks all repositories of an exam (only for external version control services)
     *
     * @param examId id of the exam for which the repositories should be locked
     * @return number of exercises for which the repositories are locked
     */
    public Integer lockAllRepositories(Long examId) {
        var programmingExercises = getAllExercisesForExamByType(examId, ProgrammingExercise.class);
        // Run the runnable immediately so that the repositories are locked as fast as possible
        programmingExercises.stream().map(Exercise::getId).forEach(instanceMessageSendService::sendLockAllStudentRepositories);
        return programmingExercises.size();
    }

    /**
     * Sets exam transient properties for different exercise types
     *
     * @param exam - the exam for which we set the properties
     */
    public void setExamProperties(Exam exam) {
        exam.getExerciseGroups().forEach(exerciseGroup -> {
            exerciseGroup.getExercises().forEach(exercise -> {
                // Set transient property for quiz exam exercise if test runs exist
                if (exercise instanceof QuizExercise) {
                    studentParticipationRepository.checkTestRunsExist(exercise);
                }
            });
            // set transient number of participations for each exercise
            studentParticipationRepository.addNumberOfExamExerciseParticipations(exerciseGroup);
        });
        // set transient number of registered users
        examRepository.setNumberOfExamUsersForExams(Collections.singletonList(exam));
    }

    /**
     * Set properties for quiz exercises in exam
     *
     * @param exam The exam for which to set the properties
     */
    public void setQuizExamProperties(Exam exam) {
        Optional<QuizPool> optionalQuizPool = quizPoolService.findByExamId(exam.getId());
        if (optionalQuizPool.isPresent()) {
            QuizPool quizPool = optionalQuizPool.get();
            exam.setQuizExamMaxPoints(quizPool.getMaxPoints());
        }
    }

    /**
     * Gets a collection of useful statistics for the tutor exam-assessment-dashboard, including: - number of submissions to the course - number of
     * assessments - number of assessments assessed by the tutor - number of complaints
     *
     * @param course - the course of the exam
     * @param examId - the id of the exam to retrieve stats from
     * @return data about an exam including all exercises, plus some data for the tutor as tutor status for assessment
     */
    public StatsForDashboardDTO getStatsForExamAssessmentDashboard(Course course, Long examId) {
        Exam exam = examRepository.findById(examId).orElseThrow();
        StatsForDashboardDTO stats = new StatsForDashboardDTO();

        final long numberOfSubmissions = submissionRepository.countByExamIdSubmittedSubmissionsIgnoreTestRuns(examId)
                + programmingExerciseRepository.countLegalSubmissionsByExamIdSubmitted(examId);
        stats.setNumberOfSubmissions(new DueDateStat(numberOfSubmissions, 0));

        DueDateStat[] numberOfAssessmentsOfCorrectionRounds = resultRepository.countNumberOfFinishedAssessmentsForExamForCorrectionRounds(examId,
                exam.getNumberOfCorrectionRoundsInExam());
        stats.setNumberOfAssessmentsOfCorrectionRounds(numberOfAssessmentsOfCorrectionRounds);

        final long numberOfComplaints = complaintRepository.countByResult_Submission_Participation_Exercise_ExerciseGroup_Exam_IdAndComplaintType(examId, ComplaintType.COMPLAINT);
        stats.setNumberOfComplaints(numberOfComplaints);

        final long numberOfComplaintResponses = complaintResponseRepository
                .countByComplaint_Result_Submission_Participation_Exercise_ExerciseGroup_Exam_Id_AndComplaint_ComplaintType_AndSubmittedTimeIsNotNull(examId,
                        ComplaintType.COMPLAINT);
        stats.setNumberOfOpenComplaints(numberOfComplaints - numberOfComplaintResponses);

        final long numberOfAssessmentLocks = submissionRepository.countLockedSubmissionsByUserIdAndExamId(userRepository.getUserWithGroupsAndAuthorities().getId(), examId);
        stats.setNumberOfAssessmentLocks(numberOfAssessmentLocks);

        final long totalNumberOfAssessmentLocks = submissionRepository.countLockedSubmissionsByExamId(examId);
        stats.setTotalNumberOfAssessmentLocks(totalNumberOfAssessmentLocks);

        List<TutorLeaderboardDTO> leaderboardEntries = tutorLeaderboardService.getExamLeaderboard(course, exam);
        stats.setTutorLeaderboardEntries(leaderboardEntries);
        return stats;
    }

    /**
     * Archives the exam by creating a zip file with student submissions for
     * exercises of the exam.
     *
     * @param exam the exam to archive
     */
    @Async
    public void archiveExam(Exam exam) {
        long start = System.nanoTime();
        SecurityUtils.setAuthorizationObject();

        // Archiving an exam is only possible after the exam is over
        if (ZonedDateTime.now().isBefore(exam.getEndDate())) {
            return;
        }

        // This contains possible errors encountered during the archive process
        List<String> exportErrors = Collections.synchronizedList(new ArrayList<>());

        groupNotificationService.notifyInstructorGroupAboutExamArchiveState(exam, NotificationType.EXAM_ARCHIVE_STARTED, exportErrors);

        try {
            // Create exam archives directory if it doesn't exist
            Files.createDirectories(examArchivesDirPath);
            log.info("Created the exam archives directory at {} because it didn't exist.", examArchivesDirPath);

            // Export the exam to the archives directory.
            var archivedExamPath = courseExamExportService.exportExam(exam, examArchivesDirPath, exportErrors);

            // Attach the path to the archive to the exam and save it in the database
            if (archivedExamPath.isPresent()) {
                exam.setExamArchivePath(archivedExamPath.get().getFileName().toString());
                examRepository.saveAndFlush(exam);
            }
            else {
                groupNotificationService.notifyInstructorGroupAboutExamArchiveState(exam, NotificationType.EXAM_ARCHIVE_FAILED, exportErrors);
                return;
            }
        }
        catch (IOException e) {
            var error = "Failed to create exam archives directory " + examArchivesDirPath + ": " + e.getMessage();
            exportErrors.add(error);
            log.info(error);
        }

        groupNotificationService.notifyInstructorGroupAboutExamArchiveState(exam, NotificationType.EXAM_ARCHIVE_FINISHED, exportErrors);
        log.info("archive exam took {}", TimeLogUtil.formatDurationFrom(start));
    }

    /**
     * Combines the template commits of all programming exercises in the exam.
     * This is executed before the individual student exams are generated.
     *
     * @param exam - the exam which template commits should be combined
     */
    public void combineTemplateCommitsOfAllProgrammingExercisesInExam(Exam exam) {
        var programmingExercises = getAllExercisesForExamByType(exam, ProgrammingExercise.class);
        programmingExercises.forEach(exercise -> {
            try {
                var programmingExercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(exercise.getId());
                gitService.combineAllCommitsOfRepositoryIntoOne(programmingExercise.getTemplateParticipation().getVcsRepositoryUri());
                log.debug("Finished combination of template commits for programming exercise {}", programmingExercise);
            }
            catch (GitAPIException e) {
                log.error("An error occurred when trying to combine template commits for exam {}.", exam.getId(), e);
            }
        });
    }

    /**
     * Schedules all modeling exercises
     * This is executed when exam is updated or individual working times are updated
     *
     * @param exam - the exam whose modeling exercises will be scheduled
     */
    public void scheduleModelingExercises(Exam exam) {
        var modelingExercises = getAllExercisesForExamByType(exam, ModelingExercise.class);
        // for all modeling exercises in the exam, send their ids for scheduling
        modelingExercises.stream().map(Exercise::getId).forEach(instanceMessageSendService::sendModelingExerciseSchedule);
    }

    /**
     * Search for all exams fitting a {@link SearchTermPageableSearchDTO search query}. The result is paged,
     * meaning that there is only a predefined portion of the result returned to the user, so that the server doesn't
     * have to send hundreds/thousands of exams if there are that many in Artemis.
     *
     * @param search        The search query defining the search term and the size of the returned page
     * @param user          The user for whom to fetch all available exercises
     * @param withExercises If only exams with exercises should be searched
     * @return A wrapper object containing a list of all found exercises and the total number of pages
     */
    public SearchResultPageDTO<Exam> getAllOnPageWithSize(final SearchTermPageableSearchDTO<String> search, final User user, final boolean withExercises) {
        final var pageable = PageUtil.createDefaultPageRequest(search, PageUtil.ColumnMapping.EXAM);
        final var searchTerm = search.getSearchTerm();
        final Page<Exam> examPage;
        if (authorizationCheckService.isAdmin(user)) {
            if (withExercises) {
                examPage = examRepository.queryNonEmptyBySearchTermInAllCourses(searchTerm, pageable);
            }
            else {
                examPage = examRepository.queryBySearchTermInAllCourses(searchTerm, pageable);
            }
        }
        else {
            if (withExercises) {
                examPage = examRepository.queryNonEmptyBySearchTermInCoursesWhereInstructor(searchTerm, user.getGroups(), pageable);
            }
            else {
                examPage = examRepository.queryBySearchTermInCoursesWhereInstructor(searchTerm, user.getGroups(), pageable);
            }
        }
        return new SearchResultPageDTO<>(examPage.getContent(), examPage.getTotalPages());
    }

    /**
     * Get all exams of the user. The result is paged
     *
     * @param pageable The search query defining the search term and the size of the returned page
     * @param user     The user for whom to fetch all available exercises
     * @return exam page
     */
    public Page<Exam> getAllActiveExams(final Pageable pageable, final User user) {
        // active exam means that exam has visible date in the past 7 days or next 7 days.
        return examRepository.findAllActiveExamsInCoursesWhereInstructor(user.getGroups(), pageable, ZonedDateTime.now().minusDays(EXAM_ACTIVE_DAYS),
                ZonedDateTime.now().plusDays(EXAM_ACTIVE_DAYS));
    }

    /**
     * Cleans up an exam by cleaning up all exercises from that course. This deletes all student
     * repositories and build plans. Note that an exam has to be archived first before being cleaned up.
     *
     * @param examId    The id of the exam to clean up
     * @param principal the user that wants to cleanup the exam
     */
    public void cleanupExam(Long examId, Principal principal) {
        final var auditEvent = new AuditEvent(principal.getName(), Constants.CLEANUP_EXAM, "exam=" + examId);
        auditEventRepository.add(auditEvent);

        var programmingExercises = getAllExercisesForExamByType(examId, ProgrammingExercise.class);
        programmingExercises.forEach(exercise -> exerciseDeletionService.cleanup(exercise.getId(), true));
        log.info("The exam {} has been cleaned up!", examId);
    }

    /**
     * Updates the working times for student exams based on a given change in working time and reschedules exercises accordingly.
     * This method considers any existing time extensions for individual students and adjusts their working times relative to the original exam duration and the specified change.
     * After updating the working times, it saves the changes and, if the exam is already visible, notifies both the students and relevant instances about the update.
     * Additionally, if the current time is before the latest individual exam end date, it potentially triggers a rescheduling of the clustering of modeling submissions,
     * considering the use of Compass.
     *
     * @param exam                 The exam entity for which the student exams and exercises need to be updated and rescheduled. The student exams must be already loaded.
     * @param originalExamDuration The original duration of the exam, in minutes, before any changes.
     * @param workingTimeChange    The amount of time, in minutes, to add or subtract from the exam's original duration and the student's working time. This value can be positive
     *                                 (to extend time) or negative (to reduce time).
     */
    public void updateStudentExamsAndRescheduleExercises(Exam exam, Integer originalExamDuration, Integer workingTimeChange) {
        var now = now();
        User instructor = userRepository.getUser();

        var studentExams = exam.getStudentExams();
        for (var studentExam : studentExams) {
            Integer originalStudentWorkingTime = studentExam.getWorkingTime();
            int originalTimeExtension = originalStudentWorkingTime - originalExamDuration;
            // NOTE: take the original working time extensions into account
            if (originalTimeExtension == 0) {
                studentExam.setWorkingTime(originalStudentWorkingTime + workingTimeChange);
            }
            else {
                double relativeTimeExtension = (double) originalTimeExtension / (double) originalExamDuration;
                int newNormalWorkingTime = originalExamDuration + workingTimeChange;
                int timeAdjustment = Math.toIntExact(Math.round(newNormalWorkingTime * relativeTimeExtension));
                int adjustedWorkingTime = Math.max(newNormalWorkingTime + timeAdjustment, 0);
                studentExam.setWorkingTime(adjustedWorkingTime);
            }

            // NOTE: if the exam is already visible, notify the student about the working time change
            if (now.isAfter(exam.getVisibleDate())) {
                examLiveEventsService.createAndSendWorkingTimeUpdateEvent(studentExam, studentExam.getWorkingTime(), originalStudentWorkingTime, true, instructor);
            }
        }
        studentExamRepository.saveAll(studentExams);

        // NOTE: if the exam is already visible, notify instances about the working time change
        if (now.isAfter(exam.getVisibleDate())) {
            instanceMessageSendService.sendRescheduleAllStudentExams(exam.getId());
        }

        // NOTE: potentially re-schedule clustering of modeling submissions (in case Compass is active)
        if (now.isBefore(examDateService.getLatestIndividualExamEndDate(exam))) {
            scheduleModelingExercises(exam);
        }
    }

    /**
     * A specialized BiFunction<Long, Double, BonusResultDTO> functional interface to provide a simple interface
     * for passing the data dependencies needed for a Bonus calculation (like source course/exam results).
     */
    @FunctionalInterface
    private interface ExamBonusCalculator {

        BonusResultDTO calculateStudentGradesWithBonus(Long studentId, Double bonusToAchievedPoints);
    }
}
