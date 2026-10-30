package de.tum.cit.aet.artemis.exercise.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.ExampleSubmission;
import de.tum.cit.aet.artemis.assessment.domain.GradingCriterion;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.domain.TutorParticipation;
import de.tum.cit.aet.artemis.assessment.repository.GradingCriterionRepository;
import de.tum.cit.aet.artemis.assessment.service.TutorParticipationService;
import de.tum.cit.aet.artemis.core.domain.FeatureInteraction;
import de.tum.cit.aet.artemis.core.dto.StatsForDashboardDTO;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import de.tum.cit.aet.artemis.core.security.allowedTools.AllowedTools;
import de.tum.cit.aet.artemis.core.security.allowedTools.ToolTokenType;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceAtLeastStudentInCourse;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceAtLeastTutorInCourse;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInExercise.EnforceAtLeastEditorInExercise;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInExercise.EnforceAtLeastInstructorInExercise;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInExercise.EnforceAtLeastStudentInExercise;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInExercise.EnforceAtLeastTutorInExercise;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.service.featureusage.FeatureUsage;
import de.tum.cit.aet.artemis.core.service.featureusage.UsageInteraction;
import de.tum.cit.aet.artemis.core.service.featureusage.UserFeature;
import de.tum.cit.aet.artemis.course.repository.CourseAthenaConfigRepository;
import de.tum.cit.aet.artemis.exam.api.ExamAccessApi;
import de.tum.cit.aet.artemis.exam.api.ExamDateApi;
import de.tum.cit.aet.artemis.exam.config.ExamApiNotPresentException;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.dto.ExerciseDeletionSummaryDTO;
import de.tum.cit.aet.artemis.exercise.dto.ExerciseDetailsDTO;
import de.tum.cit.aet.artemis.exercise.dto.ExerciseDetailsExerciseDTO;
import de.tum.cit.aet.artemis.exercise.dto.ExerciseResponseDTO;
import de.tum.cit.aet.artemis.exercise.dto.ExerciseTitleDTO;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.exercise.repository.ParticipationRepository;
import de.tum.cit.aet.artemis.exercise.repository.TeamAssignmentConfigRepository;
import de.tum.cit.aet.artemis.exercise.service.ExerciseDateService;
import de.tum.cit.aet.artemis.exercise.service.ExerciseDeletionService;
import de.tum.cit.aet.artemis.exercise.service.ExerciseService;
import de.tum.cit.aet.artemis.exercise.service.ExerciseVersionService;
import de.tum.cit.aet.artemis.exercise.service.ParticipationService;
import de.tum.cit.aet.artemis.plagiarism.api.PlagiarismCaseApi;
import de.tum.cit.aet.artemis.plagiarism.dto.PlagiarismCaseInfoDTO;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.service.QuizBatchService;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorParticipationStatus;

/**
 * REST controller for managing Exercise.
 */
@Profile(PROFILE_CORE)
@Lazy
@FeatureUsage(UserFeature.EXERCISE_MANAGEMENT)
@RestController
@RequestMapping("api/exercise/")
public class ExerciseResource {

    private static final Logger log = LoggerFactory.getLogger(ExerciseResource.class);

    private final ExerciseService exerciseService;

    private final ExerciseDeletionService exerciseDeletionService;

    private final ExerciseRepository exerciseRepository;

    private final UserRepository userRepository;

    private final ParticipationService participationService;

    private final AuthorizationCheckService authCheckService;

    private final TutorParticipationService tutorParticipationService;

    private final Optional<ExamDateApi> examDateApi;

    private final GradingCriterionRepository gradingCriterionRepository;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final QuizBatchService quizBatchService;

    private final ParticipationRepository participationRepository;

    private final ExerciseVersionService exerciseVersionService;

    private final Optional<ExamAccessApi> examAccessApi;

    private final Optional<PlagiarismCaseApi> plagiarismCaseApi;

    private final CourseAthenaConfigRepository courseAthenaConfigRepository;

    private final TeamAssignmentConfigRepository teamAssignmentConfigRepository;

    public ExerciseResource(ExerciseService exerciseService, ExerciseDeletionService exerciseDeletionService, ParticipationService participationService,
            UserRepository userRepository, Optional<ExamDateApi> examDateApi, AuthorizationCheckService authCheckService, TutorParticipationService tutorParticipationService,
            ProgrammingExerciseRepository programmingExerciseRepository, GradingCriterionRepository gradingCriterionRepository, ExerciseRepository exerciseRepository,
            QuizBatchService quizBatchService, ParticipationRepository participationRepository, ExerciseVersionService exerciseVersionService,
            Optional<ExamAccessApi> examAccessApi, Optional<PlagiarismCaseApi> plagiarismCaseApi, CourseAthenaConfigRepository courseAthenaConfigRepository,
            TeamAssignmentConfigRepository teamAssignmentConfigRepository) {
        this.courseAthenaConfigRepository = courseAthenaConfigRepository;
        this.teamAssignmentConfigRepository = teamAssignmentConfigRepository;
        this.exerciseService = exerciseService;
        this.exerciseDeletionService = exerciseDeletionService;
        this.participationService = participationService;
        this.userRepository = userRepository;
        this.authCheckService = authCheckService;
        this.tutorParticipationService = tutorParticipationService;
        this.gradingCriterionRepository = gradingCriterionRepository;
        this.examDateApi = examDateApi;
        this.exerciseRepository = exerciseRepository;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.quizBatchService = quizBatchService;
        this.participationRepository = participationRepository;
        this.exerciseVersionService = exerciseVersionService;
        this.examAccessApi = examAccessApi;
        this.plagiarismCaseApi = plagiarismCaseApi;
    }

    /**
     * GET /courses/{courseId}/exercise-titles : the id, title and type of the course exercises visible to the user.
     * <p>
     * For callers that only have to name exercises rather than show them, such as the Iris chat context picker, which
     * previously loaded the full course exercise payload — participations, submissions, results and scores included —
     * to fill a dropdown with these three fields.
     * <p>
     * Visibility matches the course overview's release and online-course LTI-launch rules. The repository evaluates the
     * rules while projecting the three returned scalars, so neither course nor exercise entities are hydrated.
     *
     * @param courseId the id of the course
     * @return the exercises of the course the user may see, as id, title and type
     */
    @GetMapping("courses/{courseId}/exercise-titles")
    @EnforceAtLeastStudentInCourse
    public ResponseEntity<Set<ExerciseTitleDTO>> getExerciseTitlesForCourse(@PathVariable long courseId) {
        log.debug("REST request to get the exercise titles of course {}", courseId);
        return getExerciseTitles(courseId, false);
    }

    /**
     * Returns the course catalogue including exercises that are not released yet.
     *
     * @param courseId the course whose exercise titles to load
     * @return the staff exercise title catalogue
     */
    @GetMapping("courses/{courseId}/exercise-titles/for-staff")
    @EnforceAtLeastTutorInCourse
    public ResponseEntity<Set<ExerciseTitleDTO>> getExerciseTitlesForStaff(@PathVariable long courseId) {
        return getExerciseTitles(courseId, true);
    }

    private ResponseEntity<Set<ExerciseTitleDTO>> getExerciseTitles(long courseId, boolean seesUnreleased) {
        String userLogin = SecurityUtils.getCurrentUserLogin().orElseThrow();
        Set<ExerciseTitleDTO> titles = exerciseRepository.findTitlesVisibleToUser(courseId, ZonedDateTime.now(), seesUnreleased, userLogin);
        return ResponseEntity.ok(titles);
    }

    /**
     * GET /exercises/:exerciseId : get the "exerciseId" exercise.
     *
     * @param exerciseId the exerciseId of the exercise to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the exercise,
     *         or with status 404 (Not Found)
     */
    @FeatureUsage(UserFeature.EXERCISE_DETAILS)
    @GetMapping("exercises/{exerciseId}")
    @EnforceAtLeastStudentInExercise
    @AllowedTools(ToolTokenType.SCORPIO)
    public ResponseEntity<ExerciseResponseDTO> getExercise(@PathVariable Long exerciseId) {
        Exercise exercise = loadExerciseForKind(exerciseId, false);
        if (!exercise.isVisibleToStudents()) {
            throw new AccessForbiddenException();
        }
        teamAssignmentConfigRepository.attachTo(exercise);
        exercise.filterSensitiveInformation();
        return ResponseEntity.ok(ExerciseResponseDTO.of(exercise));
    }

    /**
     * Returns the full details of a course exercise for staff.
     *
     * @param exerciseId the course exercise to load
     * @return the course exercise with staff details
     */
    @GetMapping("exercises/{exerciseId}/for-staff")
    @EnforceAtLeastTutorInExercise
    public ResponseEntity<ExerciseResponseDTO> getExerciseForStaff(@PathVariable Long exerciseId) {
        return exerciseForStaff(loadExerciseForKind(exerciseId, false));
    }

    /**
     * Returns an exam exercise for tutor training after the last individual working period.
     *
     * @param exerciseId the exam exercise to load
     * @return the exam exercise with assessment details
     */
    @GetMapping("exercises/{exerciseId}/for-exam-assessment")
    @EnforceAtLeastTutorInExercise
    public ResponseEntity<ExerciseResponseDTO> getExerciseForExamAssessment(@PathVariable Long exerciseId) {
        Exercise exercise = loadExerciseForKind(exerciseId, true);
        ExamDateApi api = examDateApi.orElseThrow(() -> new ExamApiNotPresentException(ExamDateApi.class));
        ZonedDateTime latestEnd = api.getLatestIndividualExamEndDate(exercise.getExam());
        if (latestEnd == null || latestEnd.isAfter(ZonedDateTime.now())) {
            throw new AccessForbiddenException();
        }
        return exerciseForStaff(exercise);
    }

    /**
     * Returns an exam exercise for editing, independently of the exam working period.
     *
     * @param exerciseId the exam exercise to load
     * @return the exam exercise with management details
     */
    @GetMapping("exercises/{exerciseId}/for-exam-management")
    @EnforceAtLeastEditorInExercise
    public ResponseEntity<ExerciseResponseDTO> getExerciseForExamManagement(@PathVariable Long exerciseId) {
        return exerciseForStaff(loadExerciseForKind(exerciseId, true));
    }

    private Exercise loadExerciseForKind(Long exerciseId, boolean examExercise) {
        Exercise exercise = exerciseRepository.findByIdWithCategoriesElseThrow(exerciseId);
        if (examExercise ? !exercise.isExamExercise() : !exercise.isCourseExercise()) {
            throw new AccessForbiddenException();
        }
        return exercise;
    }

    private ResponseEntity<ExerciseResponseDTO> exerciseForStaff(Exercise exercise) {
        teamAssignmentConfigRepository.attachTo(exercise);
        exercise.setGradingCriteria(gradingCriterionRepository.findByExerciseIdWithEagerGradingCriteria(exercise.getId()));
        return ResponseEntity.ok(ExerciseResponseDTO.of(exercise));
    }

    /**
     * GET /exercises/:exerciseId/example-solution : get the exercise with example
     * solution without sensitive fields
     * if the user is allowed to access the example solution and if the example
     * solution is published.
     *
     * @param exerciseId the exerciseId of the exercise with the example solution
     * @return the ResponseEntity with status 200 (OK) and with the body of the
     *         exercise with its example solution after filtering sensitive data, or
     *         with
     *         status 404 (Not Found) if the exercise is not found, or with status
     *         403 (Forbidden) if the current user does not have access to the
     *         example solution.
     */
    @FeatureUsage(UserFeature.EXERCISE_DETAILS)
    @GetMapping("exercises/{exerciseId}/example-solution")
    @EnforceAtLeastStudent
    public ResponseEntity<ExerciseResponseDTO> getExerciseForExampleSolution(@PathVariable Long exerciseId) {

        log.debug("REST request to get exercise with example solution: {}", exerciseId);

        User user = userRepository.getUserWithAuthorities();
        Exercise exercise = exerciseRepository.findByIdElseThrow(exerciseId);

        if (exercise.isExamExercise()) {
            ExamAccessApi api = examAccessApi.orElseThrow(() -> new ExamApiNotPresentException(ExamAccessApi.class));
            api.checkExamExerciseForExampleSolutionAccessElseThrow(exercise);
        }
        else {
            // Course exercise
            if (!authCheckService.isAllowedToSeeCourseExercise(exercise, user)) {
                throw new AccessForbiddenException("You are not allowed to see this exercise!");
            }
            if (!exercise.isExampleSolutionPublished()) {
                throw new AccessForbiddenException("Example solution for exercise is not published yet!");
            }
        }

        exercise.filterSensitiveInformation();
        return ResponseEntity.ok(ExerciseResponseDTO.of(exercise));
    }

    /**
     * GET /exercises/:exerciseId/for-assessment-dashboard : get the "exerciseId"
     * exercise with data useful for tutors.
     *
     * @param exerciseId the exerciseId of the exercise to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the exercise,
     *         or with status 404 (Not Found)
     */
    @FeatureUsage(UserFeature.ASSESSMENT_DASHBOARD)
    @GetMapping("exercises/{exerciseId}/for-assessment-dashboard")
    @EnforceAtLeastTutorInExercise
    public ResponseEntity<ExerciseResponseDTO> getExerciseForAssessmentDashboard(@PathVariable Long exerciseId) {
        User user = userRepository.getUserWithAuthorities();
        Exercise exercise = exerciseRepository.findByIdElseThrow(exerciseId);

        if (exercise instanceof ProgrammingExercise) {
            // Programming exercises with only automatic assessment should *NOT* be
            // available on the assessment dashboard!
            if (exercise.getAssessmentType() == AssessmentType.AUTOMATIC && !exercise.getAllowComplaintsForAutomaticAssessments()) {
                throw new BadRequestAlertException("Programming exercises with only automatic assessment should NOT be available on the assessment dashboard", "Exercise",
                        "programmingExerciseWithOnlyAutomaticAssessment");
            }
            exercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationCategoriesElseThrow(exerciseId);
            teamAssignmentConfigRepository.attachTo(exercise);
        }
        // after the reload above, which answers from its own persistence context and carries no configuration
        courseAthenaConfigRepository.attachToCourseOf(exercise);

        if (exercise.isExamExercise()) {
            // let the client explain why assessment is not possible yet and from when on it is, instead of running into a 403
            ExamDateApi api = examDateApi.orElseThrow(() -> new ExamApiNotPresentException(ExamDateApi.class));
            var exam = exercise.getExam();
            var examAssessmentDates = ExerciseDateService.computeExamAssessmentDates(exercise, exam, api.getLatestIndividualExamEndDate(exam));
            if (examAssessmentDates != null) {
                exercise.setLatestExamEndDate(examAssessmentDates.latestExamEndDate());
                exercise.setAssessmentPossibleFrom(examAssessmentDates.assessmentPossibleFrom());
            }
        }

        Set<ExampleSubmission> exampleSubmissions = exerciseService.findExampleSubmissionsForExercise(exercise);
        // Do not provide example submissions without any assessment
        exampleSubmissions.removeIf(exampleSubmission -> exampleSubmission.getSubmission().getLatestResult() == null);
        exercise.setExampleSubmissions(exampleSubmissions);

        Set<GradingCriterion> gradingCriteria = gradingCriterionRepository.findByExerciseIdWithEagerGradingCriteria(exerciseId);
        exercise.setGradingCriteria(gradingCriteria);

        TutorParticipation tutorParticipation = tutorParticipationService.findByExerciseAndTutor(exercise, user);
        if (exampleSubmissions.isEmpty() && tutorParticipation.getStatus().equals(TutorParticipationStatus.REVIEWED_INSTRUCTIONS)) {
            tutorParticipation.setStatus(TutorParticipationStatus.TRAINED);
        }
        exercise.setTutorParticipations(Set.of(tutorParticipation));
        return ResponseEntity.ok(ExerciseResponseDTO.of(exercise));
    }

    /**
     * GET /exercises/:exerciseId/title : Returns the title of the exercise with the
     * given id
     *
     * @param exerciseId the id of the exercise
     * @return the title of the exercise wrapped in an ResponseEntity or 404 Not
     *         Found if no exercise with that id exists
     */
    @FeatureUsage(UserFeature.EXERCISE_DETAILS)
    @UsageInteraction(FeatureInteraction.AUTOMATIC)
    @GetMapping("exercises/{exerciseId}/title")
    @EnforceAtLeastStudent
    public ResponseEntity<String> getExerciseTitle(@PathVariable Long exerciseId) {
        final var title = exerciseRepository.getExerciseTitle(exerciseId);
        return title == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(title);
    }

    /**
     * GET /exercises/:exerciseId/deletion-summary : Get a summary for the deletion of an exercise.
     *
     * @param exerciseId the id of the exercise
     * @return the {@link ResponseEntity} with status {@code 200} and with body a summary of the deletion of the exercise
     */
    @GetMapping("exercises/{exerciseId}/deletion-summary")
    @EnforceAtLeastInstructorInExercise
    public ResponseEntity<ExerciseDeletionSummaryDTO> getDeletionSummary(@PathVariable long exerciseId) {
        log.debug("REST request to get deletion summary for exercise : {}", exerciseId);
        return ResponseEntity.ok(exerciseDeletionService.getDeletionSummary(exerciseId));
    }

    /**
     * GET /exercises/:exerciseId/stats-for-assessment-dashboard A collection of
     * useful statistics for the tutor exercise dashboard of the exercise with the
     * given exerciseId
     *
     * @param exerciseId the exerciseId of the exercise to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the stats, or
     *         with status 404 (Not Found)
     */
    @FeatureUsage(UserFeature.ASSESSMENT_DASHBOARD)
    @GetMapping("exercises/{exerciseId}/stats-for-assessment-dashboard")
    @EnforceAtLeastTutorInExercise
    public ResponseEntity<StatsForDashboardDTO> getStatsForExerciseAssessmentDashboard(@PathVariable Long exerciseId) {
        Exercise exercise = exerciseRepository.findByIdElseThrow(exerciseId);
        StatsForDashboardDTO stats = exerciseService.populateCommonStatistics(exercise, exercise.isExamExercise());
        return ResponseEntity.ok(stats);
    }

    /**
     * Reset the exercise by deleting all its participations
     * /exercises/:exerciseId/reset This can be used by all exercise types, however
     * they can also provide custom
     * implementations
     *
     * @param exerciseId exercise to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("exercises/{exerciseId}/reset")
    @EnforceAtLeastInstructorInExercise
    public ResponseEntity<Void> reset(@PathVariable Long exerciseId) {
        log.debug("REST request to reset Exercise : {}", exerciseId);
        exerciseDeletionService.reset(exerciseId);
        return ResponseEntity.ok().build();
    }

    /**
     * GET /exercises/:exerciseId/details : sends exercise details including up to
     * the latest 20 results for the currently logged-in user
     * NOTE: this should only be used for course exercises, not for exam exercises
     *
     * @param exerciseId the exerciseId of the exercise to get the repos from
     * @return the ResponseEntity with status 200 (OK) and with body the exercise,
     *         or with status 404 (Not Found)
     */
    @FeatureUsage(UserFeature.EXERCISE_DETAILS)
    @GetMapping("exercises/{exerciseId}/details")
    @EnforceAtLeastStudent
    @AllowedTools(ToolTokenType.SCORPIO)
    public ResponseEntity<ExerciseDetailsDTO> getExerciseDetails(@PathVariable Long exerciseId) {
        User user = userRepository.getUserWithAuthorities();
        Exercise exercise = exerciseService.findOneWithDetailsForStudents(exerciseId, user);
        if (exercise.isExamExercise()) {
            throw new AccessForbiddenException();
        }

        // if exercise is not yet released to the students they should not have any
        // access to it, but course staff still open it from the course overview, which lists unreleased exercises for them
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.STUDENT, exercise, user);
        if (!exercise.isVisibleToStudents() && !authCheckService.isAtLeastTeachingAssistantForExercise(exercise, user)) {
            throw new AccessForbiddenException();
        }
        // the page offers the AI feedback request based on these, and the config is lazy
        courseAthenaConfigRepository.attachToCourseOf(exercise);

        List<StudentParticipation> participations = participationService.findByExerciseAndStudentIdWithSubmissionsAndResults(exercise, user.getId());
        // normally we only have one participation here (only in case of practice mode,
        // there could be two)
        exercise.setStudentParticipations(new HashSet<>());
        for (StudentParticipation participation : participations) {
            // By filtering the results available yet, they can become null for the
            // exercise.
            exercise.filterResultsForStudents(participation);
            participation.getSubmissions().stream().flatMap(submission -> submission.getResults().stream().filter(Objects::nonNull)).forEach(Result::filterSensitiveInformation);
            exercise.addParticipation(participation);
        }

        if (exercise instanceof QuizExercise quizExercise) {
            quizExercise.setQuizBatches(null);
            quizExercise.setQuizBatches(quizBatchService.getQuizBatchForStudentByLogin(quizExercise, user.getLogin()).stream().collect(Collectors.toSet()));
        }
        // TODO: we should also check that the submissions do not contain sensitive data

        // remove sensitive information for students
        exercise.filterSensitiveInformation();

        PlagiarismCaseInfoDTO plagiarismCaseInfo = plagiarismCaseApi.flatMap(api -> api.getPlagiarismCaseInfoForExerciseAndUser(exercise.getId(), user.getId())).orElse(null);

        return ResponseEntity.ok(new ExerciseDetailsDTO(ExerciseDetailsExerciseDTO.of(exercise), plagiarismCaseInfo));
    }

    /**
     * PUT /exercises/:exerciseId/toggle-second-correction
     *
     * @param exerciseId the exerciseId of the exercise to toggle the second
     *                       correction
     * @return the ResponseEntity with status 200 (OK) and new state of the
     *         correction toggle state
     */
    @FeatureUsage(UserFeature.ASSESSMENT_DASHBOARD)
    @PutMapping("exercises/{exerciseId}/toggle-second-correction")
    @EnforceAtLeastInstructorInExercise
    public ResponseEntity<Boolean> toggleSecondCorrectionEnabled(@PathVariable Long exerciseId) {
        log.debug("toggleSecondCorrectionEnabled for exercise with id: {}", exerciseId);
        Exercise exercise = exerciseRepository.findByIdElseThrow(exerciseId);
        boolean updatedExercise = exerciseRepository.toggleSecondCorrection(exercise);
        exerciseVersionService.createExerciseVersion(exercise);
        return ResponseEntity.ok(updatedExercise);
    }

    /**
     * GET /exercises/{exerciseId}/latest-due-date
     *
     * @param exerciseId the exerciseId of the exercise to get the latest due date
     *                       from
     * @return the ResponseEntity with status 200 (OK) and the latest due date
     */
    @FeatureUsage(UserFeature.EXERCISE_DETAILS)
    @GetMapping("exercises/{exerciseId}/latest-due-date")
    @EnforceAtLeastStudent
    public ResponseEntity<ZonedDateTime> getLatestDueDate(@PathVariable Long exerciseId) {
        log.debug("getLatestDueDate for exercise with id: {}", exerciseId);
        Exercise exercise = exerciseRepository.findByIdElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.STUDENT, exercise, null);
        return ResponseEntity.ok(participationRepository.findLatestIndividualDueDate(exerciseId).orElse(exercise.getDueDate()));
    }
}
