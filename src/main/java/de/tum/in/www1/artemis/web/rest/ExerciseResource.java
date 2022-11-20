package de.tum.in.www1.artemis.web.rest;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.TutorParticipationStatus;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.participation.TutorParticipation;
import de.tum.in.www1.artemis.domain.quiz.QuizExercise;
import de.tum.in.www1.artemis.domain.submissionpolicy.SubmissionPolicy;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.*;
import de.tum.in.www1.artemis.service.exam.ExamDateService;
import de.tum.in.www1.artemis.service.feature.Feature;
import de.tum.in.www1.artemis.service.feature.FeatureToggle;
import de.tum.in.www1.artemis.web.rest.dto.StatsForDashboardDTO;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;

/**
 * REST controller for managing Exercise.
 */
@RestController
@RequestMapping("/api")
@PreAuthorize("hasRole('ADMIN')")
public class ExerciseResource {

    private final Logger log = LoggerFactory.getLogger(ExerciseResource.class);

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

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

    public ExerciseResource(ExerciseService exerciseService, ExerciseDeletionService exerciseDeletionService, ParticipationService participationService,
            UserRepository userRepository, ExamDateService examDateService, AuthorizationCheckService authCheckService, TutorParticipationService tutorParticipationService,
            ExampleSubmissionRepository exampleSubmissionRepository, ProgrammingExerciseRepository programmingExerciseRepository,
            GradingCriterionRepository gradingCriterionRepository, ExerciseRepository exerciseRepository, QuizBatchService quizBatchService,
            ParticipationRepository participationRepository) {
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
    }

    /**
     * GET /exercises/:exerciseId : get the "exerciseId" exercise.
     *
     * @param exerciseId the exerciseId of the exercise to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the exercise, or with status 404 (Not Found)
     */
    @GetMapping("/exercises/{exerciseId}")
    @PreAuthorize("hasRole('USER')")
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

        List<GradingCriterion> gradingCriteria = gradingCriterionRepository.findByExerciseIdWithEagerGradingCriteria(exerciseId);
        exercise.setGradingCriteria(gradingCriteria);
        return ResponseEntity.ok(exercise);
    }

    /**
     * GET /exercises/:exerciseId : get the "exerciseId" exercise with data useful for tutors.
     *
     * @param exerciseId the exerciseId of the exercise to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the exercise, or with status 404 (Not Found)
     */
    @GetMapping("/exercises/{exerciseId}/for-assessment-dashboard")
    @PreAuthorize("hasRole('TA')")
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

        List<GradingCriterion> gradingCriteria = gradingCriterionRepository.findByExerciseIdWithEagerGradingCriteria(exerciseId);
        exercise.setGradingCriteria(gradingCriteria);

        TutorParticipation tutorParticipation = tutorParticipationService.findByExerciseAndTutor(exercise, user);
        if (exampleSubmissions.isEmpty() && tutorParticipation.getStatus().equals(TutorParticipationStatus.REVIEWED_INSTRUCTIONS)) {
            tutorParticipation.setStatus(TutorParticipationStatus.TRAINED);
        }
        exercise.setTutorParticipations(Collections.singleton(tutorParticipation));
        return ResponseEntity.ok(exercise);
    }

    /**
     * GET /exercises/upcoming : Find all exercises that have an upcoming due date.
     *
     * @return the ResponseEntity with status 200 (OK) and a list of exercises.
     */
    @GetMapping("/exercises/upcoming")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Set<Exercise>> getUpcomingExercises() {
        log.debug("REST request to get all upcoming exercises");
        authCheckService.checkIsAdminElseThrow(null);
        Set<Exercise> upcomingExercises = exerciseRepository.findAllExercisesWithCurrentOrUpcomingDueDate();
        return ResponseEntity.ok(upcomingExercises);
    }

    /**
     * GET /exercises/:exerciseId/title : Returns the title of the exercise with the given id
     *
     * @param exerciseId the id of the exercise
     * @return the title of the exercise wrapped in an ResponseEntity or 404 Not Found if no exercise with that id exists
     */
    @GetMapping("/exercises/{exerciseId}/title")
    @PreAuthorize("hasRole('USER')")
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
    @GetMapping("/exercises/{exerciseId}/stats-for-assessment-dashboard")
    @PreAuthorize("hasRole('TA')")
    public ResponseEntity<StatsForDashboardDTO> getStatsForExerciseAssessmentDashboard(@PathVariable Long exerciseId) {
        Exercise exercise = exerciseRepository.findByIdElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.TEACHING_ASSISTANT, exercise, null);
        StatsForDashboardDTO stats = exerciseService.populateCommonStatistics(exercise, exercise.isExamExercise());
        return ResponseEntity.ok(stats);
    }

    /**
     * Reset the exercise by deleting all its participations /exercises/:exerciseId/reset This can be used by all exercise types, however they can also provide custom implementations
     *
     * @param exerciseId exercise to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("/exercises/{exerciseId}/reset")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<Void> reset(@PathVariable Long exerciseId) {
        log.debug("REST request to reset Exercise : {}", exerciseId);
        Exercise exercise = exerciseRepository.findByIdElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.INSTRUCTOR, exercise, null);
        exerciseDeletionService.reset(exercise);
        return ResponseEntity.ok().build();
    }

    /**
     * DELETE /exercises/:exerciseId/cleanup : delete all build plans (except BASE) of all participations belonging to this exercise. Optionally delete and archive all repositories
     *
     * @param exerciseId         exercise to delete build plans for
     * @param deleteRepositories whether repositories should be deleted or not
     * @return ResponseEntity with status
     */
    @DeleteMapping("/exercises/{exerciseId}/cleanup")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    @FeatureToggle(Feature.ProgrammingExercises)
    public ResponseEntity<Resource> cleanup(@PathVariable Long exerciseId, @RequestParam(defaultValue = "false") boolean deleteRepositories) {
        log.info("Start to cleanup build plans for Exercise: {}, delete repositories: {}", exerciseId, deleteRepositories);
        var exercise = exerciseRepository.findByIdElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.INSTRUCTOR, exercise, null);
        exerciseDeletionService.cleanup(exerciseId, deleteRepositories);
        log.info("Cleanup build plans was successful for Exercise : {}", exerciseId);
        return ResponseEntity.ok().build();
    }

    /**
     * GET /exercises/:exerciseId/details : sends exercise details including all results for the currently logged-in user
     *
     * @param exerciseId the exerciseId of the exercise to get the repos from
     * @return the ResponseEntity with status 200 (OK) and with body the exercise, or with status 404 (Not Found)
     */
    @GetMapping("/exercises/{exerciseId}/details")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Exercise> getExerciseDetails(@PathVariable Long exerciseId) {
        User user = userRepository.getUserWithGroupsAndAuthorities();

        Exercise exercise = exerciseService.findOneWithDetailsForStudents(exerciseId, user);

        // TODO: Create alternative route so that instructors and admins can access the exercise details
        // The users are not allowed to access the exercise details over this route if the exercise belongs to an exam
        if (exercise.isExamExercise()) {
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
                participation.getResults().forEach(r -> r.setAssessor(null));
            }
            exercise.addParticipation(participation);
        }

        if (exercise instanceof QuizExercise quizExercise) {
            quizExercise.setQuizBatches(null);
            quizExercise.setQuizBatches(quizBatchService.getQuizBatchForStudentByLogin(quizExercise, user.getLogin()).stream().collect(Collectors.toSet()));
        }
        if (exercise instanceof ProgrammingExercise programmingExercise) {
            // TODO: instead fetch the policy without programming exercise, should be faster
            SubmissionPolicy policy = programmingExerciseRepository.findWithSubmissionPolicyById(programmingExercise.getId()).get().getSubmissionPolicy();
            programmingExercise.setSubmissionPolicy(policy);
            programmingExercise.checksAndSetsIfProgrammingExerciseIsLocalSimulation();
        }
        // TODO: we should also check that the submissions do not contain sensitive data

        // remove sensitive information for students
        if (!authCheckService.isAtLeastTeachingAssistantForExercise(exercise, user)) {
            exercise.filterSensitiveInformation();
        }

        return ResponseEntity.ok(exercise);
    }

    /**
     * PUT /exercises/:exerciseId/toggle-second-correction
     *
     * @param exerciseId the exerciseId of the exercise to toggle the second correction
     * @return the ResponseEntity with status 200 (OK) and new state of the correction toggle state
     */
    @PutMapping("/exercises/{exerciseId}/toggle-second-correction")
    @PreAuthorize("hasRole('INSTRUCTOR')")
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
    @GetMapping("/exercises/{exerciseId}/latest-due-date")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ZonedDateTime> getLatestDueDate(@PathVariable Long exerciseId) {
        log.debug("getLatestDueDate for exercise with id: {}", exerciseId);
        Exercise exercise = exerciseRepository.findByIdElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.STUDENT, exercise, null);
        return ResponseEntity.ok(participationRepository.findLatestIndividualDueDate(exerciseId).orElse(exercise.getDueDate()));
    }
}
