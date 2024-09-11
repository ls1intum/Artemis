package de.tum.cit.aet.artemis.web.rest;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.ExampleSubmission;
import de.tum.cit.aet.artemis.assessment.domain.GradingCriterion;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.domain.TutorParticipation;
import de.tum.cit.aet.artemis.assessment.repository.ExampleSubmissionRepository;
import de.tum.cit.aet.artemis.assessment.repository.GradingCriterionRepository;
import de.tum.cit.aet.artemis.assessment.service.TutorParticipationService;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastInstructor;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastTutor;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.service.ExamAccessService;
import de.tum.cit.aet.artemis.exam.service.ExamDateService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.exercise.repository.ParticipationRepository;
import de.tum.cit.aet.artemis.exercise.service.ExerciseDeletionService;
import de.tum.cit.aet.artemis.exercise.service.ExerciseService;
import de.tum.cit.aet.artemis.iris.dto.IrisCombinedSettingsDTO;
import de.tum.cit.aet.artemis.iris.service.settings.IrisSettingsService;
import de.tum.cit.aet.artemis.plagiarism.service.PlagiarismCaseService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.hestia.ExerciseHint;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.service.hestia.ExerciseHintService;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.service.QuizBatchService;
import de.tum.cit.aet.artemis.service.ParticipationService;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorParticipationStatus;
import de.tum.cit.aet.artemis.web.rest.dto.ExerciseDetailsDTO;
import de.tum.cit.aet.artemis.web.rest.dto.StatsForDashboardDTO;
import de.tum.cit.aet.artemis.web.rest.dto.plagiarism.PlagiarismCaseInfoDTO;
import de.tum.cit.aet.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.cit.aet.artemis.web.rest.errors.BadRequestAlertException;

/**
 * REST controller for managing Exercise.
 */
@Profile(PROFILE_CORE)
@RestController
@RequestMapping("api/")
public class ExerciseResource {

    private static final Logger log = LoggerFactory.getLogger(ExerciseResource.class);

    private final ExerciseService exerciseService;

    private final ExerciseDeletionService exerciseDeletionService;

    private final ExerciseRepository exerciseRepository;

    private final UserRepository userRepository;

    private final ParticipationService participationService;

    private final AuthorizationCheckService authCheckService;

    private final TutorParticipationService tutorParticipationService;

    private final ExamDateService examDateService;

    private final GradingCriterionRepository gradingCriterionRepository;

    private final ExampleSubmissionRepository exampleSubmissionRepository;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final QuizBatchService quizBatchService;

    private final ParticipationRepository participationRepository;

    private final ExamAccessService examAccessService;

    private final Optional<IrisSettingsService> irisSettingsService;

    private final PlagiarismCaseService plagiarismCaseService;

    private final ExerciseHintService exerciseHintService;

    public ExerciseResource(ExerciseService exerciseService, ExerciseDeletionService exerciseDeletionService, ParticipationService participationService,
            UserRepository userRepository, ExamDateService examDateService, AuthorizationCheckService authCheckService, TutorParticipationService tutorParticipationService,
            ExampleSubmissionRepository exampleSubmissionRepository, ProgrammingExerciseRepository programmingExerciseRepository,
            GradingCriterionRepository gradingCriterionRepository, ExerciseRepository exerciseRepository, QuizBatchService quizBatchService,
            ParticipationRepository participationRepository, ExamAccessService examAccessService, Optional<IrisSettingsService> irisSettingsService,
            PlagiarismCaseService plagiarismCaseService, ExerciseHintService exerciseHintService) {
        this.exerciseService = exerciseService;
        this.exerciseDeletionService = exerciseDeletionService;
        this.participationService = participationService;
        this.userRepository = userRepository;
        this.authCheckService = authCheckService;
        this.tutorParticipationService = tutorParticipationService;
        this.exampleSubmissionRepository = exampleSubmissionRepository;
        this.gradingCriterionRepository = gradingCriterionRepository;
        this.examDateService = examDateService;
        this.exerciseRepository = exerciseRepository;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.quizBatchService = quizBatchService;
        this.participationRepository = participationRepository;
        this.examAccessService = examAccessService;
        this.irisSettingsService = irisSettingsService;
        this.plagiarismCaseService = plagiarismCaseService;
        this.exerciseHintService = exerciseHintService;
    }

    /**
     * GET /exercises/:exerciseId : get the "exerciseId" exercise.
     *
     * @param exerciseId the exerciseId of the exercise to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the exercise, or with status 404 (Not Found)
     */
    @GetMapping("exercises/{exerciseId}")
    @EnforceAtLeastStudent
    public ResponseEntity<Exercise> getExercise(@PathVariable Long exerciseId) {

        log.debug("REST request to get Exercise : {}", exerciseId);

        User user = userRepository.getUserWithGroupsAndAuthorities();
        Exercise exercise = exerciseRepository.findByIdWithCategoriesAndTeamAssignmentConfigElseThrow(exerciseId);

        // Exam exercise
        if (exercise.isExamExercise()) {
            Exam exam = exercise.getExerciseGroup().getExam();
            if (authCheckService.isAtLeastEditorForExercise(exercise, user)) {
                // instructors editors and admins should always be able to see exam exercises
                // continue
            }
            else if (authCheckService.isAtLeastTeachingAssistantForExercise(exercise, user)) {
                // tutors should only be able to see exam exercises when the exercise has finished
                ZonedDateTime latestIndividualExamEndDate = examDateService.getLatestIndividualExamEndDate(exam);
                if (latestIndividualExamEndDate == null || latestIndividualExamEndDate.isAfter(ZonedDateTime.now())) {
                    // When there is no due date or the due date is in the future, we return forbidden here
                    throw new AccessForbiddenException();
                }
            }
            else {
                // Students should never access exercises
                throw new AccessForbiddenException();
            }
        }
        // Normal exercise
        else {
            if (!authCheckService.isAllowedToSeeExercise(exercise, user)) {
                throw new AccessForbiddenException();
            }
            if (!authCheckService.isAtLeastTeachingAssistantForExercise(exercise, user)) {
                exercise.filterSensitiveInformation();
            }
        }

        Set<GradingCriterion> gradingCriteria = gradingCriterionRepository.findByExerciseIdWithEagerGradingCriteria(exerciseId);
        exercise.setGradingCriteria(gradingCriteria);
        return ResponseEntity.ok(exercise);
    }

    /**
     * GET /exercises/:exerciseId/example-solution : get the exercise with example solution without sensitive fields
     * if the user is allowed to access the example solution and if the example solution is published.
     *
     * @param exerciseId the exerciseId of the exercise with the example solution
     * @return the ResponseEntity with status 200 (OK) and with the body of the exercise with its example solution after filtering sensitive data, or with
     *         status 404 (Not Found) if the exercise is not found, or with status 403 (Forbidden) if the current user does not have access to the example solution.
     */
    @GetMapping("exercises/{exerciseId}/example-solution")
    @EnforceAtLeastStudent
    public ResponseEntity<Exercise> getExerciseForExampleSolution(@PathVariable Long exerciseId) {

        log.debug("REST request to get exercise with example solution: {}", exerciseId);

        User user = userRepository.getUserWithGroupsAndAuthorities();
        Exercise exercise = exerciseRepository.findByIdElseThrow(exerciseId);

        if (exercise.isExamExercise()) {
            examAccessService.checkExamExerciseForExampleSolutionAccessElseThrow(exercise);
        }
        else {
            // Course exercise
            if (!authCheckService.isAllowedToSeeExercise(exercise, user)) {
                throw new AccessForbiddenException("You are not allowed to see this exercise!");
            }
            if (!exercise.isExampleSolutionPublished()) {
                throw new AccessForbiddenException("Example solution for exercise is not published yet!");
            }
        }

        exercise.filterSensitiveInformation();
        return ResponseEntity.ok(exercise);
    }

    /**
     * GET /exercises/:exerciseId/for-assessment-dashboard : get the "exerciseId" exercise with data useful for tutors.
     *
     * @param exerciseId the exerciseId of the exercise to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the exercise, or with status 404 (Not Found)
     */
    @GetMapping("exercises/{exerciseId}/for-assessment-dashboard")
    @EnforceAtLeastTutor
    public ResponseEntity<Exercise> getExerciseForAssessmentDashboard(@PathVariable Long exerciseId) {
        Exercise exercise = exerciseRepository.findByIdElseThrow(exerciseId);
        User user = userRepository.getUserWithGroupsAndAuthorities();
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.TEACHING_ASSISTANT, exercise, user);

        if (exercise instanceof ProgrammingExercise) {
            // Programming exercises with only automatic assessment should *NOT* be available on the assessment dashboard!
            if (exercise.getAssessmentType() == AssessmentType.AUTOMATIC && !exercise.getAllowComplaintsForAutomaticAssessments()) {
                throw new BadRequestAlertException("Programming exercises with only automatic assessment should NOT be available on the assessment dashboard", "Exercise",
                        "programmingExerciseWithOnlyAutomaticAssessment");
            }
            exercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesElseThrow(exerciseId);
        }

        Set<ExampleSubmission> exampleSubmissions = exampleSubmissionRepository.findAllWithResultByExerciseId(exerciseId);
        // Do not provide example submissions without any assessment
        exampleSubmissions.removeIf(exampleSubmission -> exampleSubmission.getSubmission().getLatestResult() == null);
        exercise.setExampleSubmissions(exampleSubmissions);

        Set<GradingCriterion> gradingCriteria = gradingCriterionRepository.findByExerciseIdWithEagerGradingCriteria(exerciseId);
        exercise.setGradingCriteria(gradingCriteria);

        TutorParticipation tutorParticipation = tutorParticipationService.findByExerciseAndTutor(exercise, user);
        if (exampleSubmissions.isEmpty() && tutorParticipation.getStatus().equals(TutorParticipationStatus.REVIEWED_INSTRUCTIONS)) {
            tutorParticipation.setStatus(TutorParticipationStatus.TRAINED);
        }
        exercise.setTutorParticipations(Collections.singleton(tutorParticipation));
        return ResponseEntity.ok(exercise);
    }

    /**
     * GET /exercises/:exerciseId/title : Returns the title of the exercise with the given id
     *
     * @param exerciseId the id of the exercise
     * @return the title of the exercise wrapped in an ResponseEntity or 404 Not Found if no exercise with that id exists
     */
    @GetMapping("exercises/{exerciseId}/title")
    @EnforceAtLeastStudent
    public ResponseEntity<String> getExerciseTitle(@PathVariable Long exerciseId) {
        final var title = exerciseRepository.getExerciseTitle(exerciseId);
        return title == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(title);
    }

    /**
     * GET /exercises/:exerciseId/stats-for-assessment-dashboard A collection of useful statistics for the tutor exercise dashboard of the exercise with the given exerciseId
     *
     * @param exerciseId the exerciseId of the exercise to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the stats, or with status 404 (Not Found)
     */
    @GetMapping("exercises/{exerciseId}/stats-for-assessment-dashboard")
    @EnforceAtLeastTutor
    public ResponseEntity<StatsForDashboardDTO> getStatsForExerciseAssessmentDashboard(@PathVariable Long exerciseId) {
        Exercise exercise = exerciseRepository.findByIdElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.TEACHING_ASSISTANT, exercise, null);
        StatsForDashboardDTO stats = exerciseService.populateCommonStatistics(exercise, exercise.isExamExercise());
        return ResponseEntity.ok(stats);
    }

    /**
     * Reset the exercise by deleting all its participations /exercises/:exerciseId/reset This can be used by all exercise types, however they can also provide custom
     * implementations
     *
     * @param exerciseId exercise to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("exercises/{exerciseId}/reset")
    @EnforceAtLeastInstructor
    public ResponseEntity<Void> reset(@PathVariable Long exerciseId) {
        log.debug("REST request to reset Exercise : {}", exerciseId);
        Exercise exercise = exerciseRepository.findByIdElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.INSTRUCTOR, exercise, null);
        exerciseDeletionService.reset(exercise);
        return ResponseEntity.ok().build();
    }

    /**
     * GET /exercises/:exerciseId/details : sends exercise details including all results for the currently logged-in user
     *
     * @param exerciseId the exerciseId of the exercise to get the repos from
     * @return the ResponseEntity with status 200 (OK) and with body the exercise, or with status 404 (Not Found)
     */
    @GetMapping("exercises/{exerciseId}/details")
    @EnforceAtLeastStudent
    public ResponseEntity<ExerciseDetailsDTO> getExerciseDetails(@PathVariable Long exerciseId) {
        User user = userRepository.getUserWithGroupsAndAuthorities();
        Exercise exercise = exerciseService.findOneWithDetailsForStudents(exerciseId, user);

        final boolean isAtLeastTAForExercise = authCheckService.isAtLeastTeachingAssistantForExercise(exercise, user);

        // TODO: Create alternative route so that instructors and admins can access the exercise details
        // The users are not allowed to access the exercise details over this route if the exercise belongs to an exam
        if (exercise.isExamExercise() && !isAtLeastTAForExercise) {
            throw new AccessForbiddenException();
        }

        // if exercise is not yet released to the students they should not have any access to it
        if (!authCheckService.isAllowedToSeeExercise(exercise, user)) {
            throw new AccessForbiddenException();
        }

        List<StudentParticipation> participations = participationService.findByExerciseAndStudentIdWithEagerResultsAndSubmissions(exercise, user.getId());
        exercise.setStudentParticipations(new HashSet<>());
        for (StudentParticipation participation : participations) {
            participation.setResults(exercise.findResultsFilteredForStudents(participation));
            // By filtering the results available yet, they can become null for the exercise.
            if (participation.getResults() != null) {
                participation.getResults().forEach(Result::filterSensitiveInformation);
            }
            exercise.addParticipation(participation);
        }

        if (exercise instanceof QuizExercise quizExercise) {
            quizExercise.setQuizBatches(null);
            quizExercise.setQuizBatches(quizBatchService.getQuizBatchForStudentByLogin(quizExercise, user.getLogin()).stream().collect(Collectors.toSet()));
        }
        // TODO: we should also check that the submissions do not contain sensitive data

        // remove sensitive information for students
        if (!isAtLeastTAForExercise) {
            exercise.filterSensitiveInformation();
        }

        IrisCombinedSettingsDTO irisSettings = irisSettingsService.map(service -> service.getCombinedIrisSettingsFor(exercise, service.shouldShowMinimalSettings(exercise, user)))
                .orElse(null);
        PlagiarismCaseInfoDTO plagiarismCaseInfo = plagiarismCaseService.getPlagiarismCaseInfoForExerciseAndUser(exercise.getId(), user.getId()).orElse(null);

        if (exercise instanceof ProgrammingExercise programmingExercise) {
            Set<ExerciseHint> activatedExerciseHints = exerciseHintService.getActivatedExerciseHints(programmingExercise, user);
            Set<ExerciseHint> availableExerciseHints = exerciseHintService.getAvailableExerciseHints(programmingExercise, user);
            return ResponseEntity.ok(new ExerciseDetailsDTO(exercise, irisSettings, plagiarismCaseInfo, availableExerciseHints, activatedExerciseHints));
        }

        return ResponseEntity.ok(new ExerciseDetailsDTO(exercise, irisSettings, plagiarismCaseInfo, null, null));
    }

    /**
     * PUT /exercises/:exerciseId/toggle-second-correction
     *
     * @param exerciseId the exerciseId of the exercise to toggle the second correction
     * @return the ResponseEntity with status 200 (OK) and new state of the correction toggle state
     */
    @PutMapping("exercises/{exerciseId}/toggle-second-correction")
    @EnforceAtLeastInstructor
    public ResponseEntity<Boolean> toggleSecondCorrectionEnabled(@PathVariable Long exerciseId) {
        log.debug("toggleSecondCorrectionEnabled for exercise with id: {}", exerciseId);
        Exercise exercise = exerciseRepository.findByIdElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.INSTRUCTOR, exercise, null);
        return ResponseEntity.ok(exerciseRepository.toggleSecondCorrection(exercise));
    }

    /**
     * GET /exercises/{exerciseId}/latest-due-date
     *
     * @param exerciseId the exerciseId of the exercise to get the latest due date from
     * @return the ResponseEntity with status 200 (OK) and the latest due date
     */
    @GetMapping("exercises/{exerciseId}/latest-due-date")
    @EnforceAtLeastStudent
    public ResponseEntity<ZonedDateTime> getLatestDueDate(@PathVariable Long exerciseId) {
        log.debug("getLatestDueDate for exercise with id: {}", exerciseId);
        Exercise exercise = exerciseRepository.findByIdElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.STUDENT, exercise, null);
        return ResponseEntity.ok(participationRepository.findLatestIndividualDueDate(exerciseId).orElse(exercise.getDueDate()));
    }
}
