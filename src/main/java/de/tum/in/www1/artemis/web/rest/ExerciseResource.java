package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.*;

import java.time.ZonedDateTime;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.ComplaintType;
import de.tum.in.www1.artemis.domain.enumeration.TutorParticipationStatus;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.participation.TutorParticipation;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.*;
import de.tum.in.www1.artemis.service.exam.ExamDateService;
import de.tum.in.www1.artemis.service.feature.Feature;
import de.tum.in.www1.artemis.service.feature.FeatureToggle;
import de.tum.in.www1.artemis.web.rest.dto.StatsForDashboardDTO;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;
import io.github.jhipster.web.util.ResponseUtil;

/**
 * REST controller for managing Exercise.
 */
@RestController
@RequestMapping("/api")
@PreAuthorize("hasRole('ADMIN')")
public class ExerciseResource {

    private final Logger log = LoggerFactory.getLogger(ExerciseResource.class);

    private static final String ENTITY_NAME = "exercise";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final ExerciseService exerciseService;

    private final ExerciseRepository exerciseRepository;

    private final UserRepository userRepository;

    private final ParticipationService participationService;

    private final AuthorizationCheckService authCheckService;

    private final TutorParticipationService tutorParticipationService;

    private final ExamDateService examDateService;

    private final GradingCriterionRepository gradingCriterionRepository;

    private final ComplaintRepository complaintRepository;

    private final ExampleSubmissionRepository exampleSubmissionRepository;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    public ExerciseResource(ExerciseService exerciseService, ParticipationService participationService, UserRepository userRepository, ExamDateService examDateService,
            AuthorizationCheckService authCheckService, TutorParticipationService tutorParticipationService, ExampleSubmissionRepository exampleSubmissionRepository,
            ComplaintRepository complaintRepository, SubmissionRepository submissionRepository, TutorLeaderboardService tutorLeaderboardService,
            ComplaintResponseRepository complaintResponseRepository, ProgrammingExerciseRepository programmingExerciseRepository,
            GradingCriterionRepository gradingCriterionRepository, ExerciseRepository exerciseRepository, ResultRepository resultRepository) {
        this.exerciseService = exerciseService;
        this.participationService = participationService;
        this.userRepository = userRepository;
        this.authCheckService = authCheckService;
        this.tutorParticipationService = tutorParticipationService;
        this.exampleSubmissionRepository = exampleSubmissionRepository;
        this.complaintRepository = complaintRepository;
        this.gradingCriterionRepository = gradingCriterionRepository;
        this.examDateService = examDateService;
        this.exerciseRepository = exerciseRepository;
        this.programmingExerciseRepository = programmingExerciseRepository;
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
                    return forbidden();
                }
            }
            else {
                // Students should never access exercises
                return forbidden();
            }
        }
        // Normal exercise
        else {
            if (!authCheckService.isAllowedToSeeExercise(exercise, user)) {
                return forbidden();
            }
            if (!authCheckService.isAtLeastTeachingAssistantForExercise(exercise, user)) {
                exercise.filterSensitiveInformation();
            }
        }

        List<GradingCriterion> gradingCriteria = gradingCriterionRepository.findByExerciseIdWithEagerGradingCriteria(exerciseId);
        exercise.setGradingCriteria(gradingCriteria);
        return ResponseUtil.wrapOrNotFound(Optional.of(exercise));
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
                return badRequest();
            }
            exercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesElseThrow(exerciseId);
        }

        Set<ExampleSubmission> exampleSubmissions = this.exampleSubmissionRepository.findAllWithResultByExerciseId(exerciseId);
        // Do not provide example submissions without any assessment
        exampleSubmissions.removeIf(exampleSubmission -> exampleSubmission.getSubmission().getLatestResult() == null);
        exercise.setExampleSubmissions(exampleSubmissions);

        List<GradingCriterion> gradingCriteria = gradingCriterionRepository.findByExerciseIdWithEagerGradingCriteria(exerciseId);
        exercise.setGradingCriteria(gradingCriteria);

        TutorParticipation tutorParticipation = tutorParticipationService.findByExerciseAndTutor(exercise, user);
        if (exampleSubmissions.size() == 0 && tutorParticipation.getStatus().equals(TutorParticipationStatus.REVIEWED_INSTRUCTIONS)) {
            tutorParticipation.setStatus(TutorParticipationStatus.TRAINED);
        }
        exercise.setTutorParticipations(Collections.singleton(tutorParticipation));

        return ResponseUtil.wrapOrNotFound(Optional.of(exercise));
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

        if (!authCheckService.isAdmin()) {
            return forbidden();
        }

        Set<Exercise> upcomingExercises = exerciseRepository.findAllExercisesWithCurrentOrUpcomingDueDate();
        return ResponseEntity.ok(upcomingExercises);
    }

    /**
     * GET /exercises/:exerciseId/title : Returns the title of the exercise with the given id
     *
     * @param exerciseId the id of the exercise
     * @return the title of the exercise wrapped in an ResponseEntity or 404 Not Found if no exercise with that id exists
     */
    @GetMapping(value = "/exercises/{exerciseId}/title")
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

        if (!authCheckService.isAtLeastTeachingAssistantForExercise(exercise)) {
            return forbidden();
        }

        StatsForDashboardDTO stats = exerciseService.populateCommonStatistics(exercise, exercise.isExamExercise());

        return ResponseEntity.ok(stats);
    }

    /**
     * GET /exercises/:exerciseId/stats-for-instructor-dashboard A collection of useful statistics for the instructor exercise dashboard of the exercise with the given exerciseId
     *
     * @param exerciseId the exerciseId of the exercise to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the stats, or with status 404 (Not Found)
     */
    @GetMapping("/exercises/{exerciseId}/stats-for-instructor-dashboard")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<StatsForDashboardDTO> getStatsForInstructorExerciseDashboard(@PathVariable Long exerciseId) {
        log.debug("REST request to get exercise statistics for instructor dashboard : {}", exerciseId);
        Exercise exercise = exerciseRepository.findByIdElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.INSTRUCTOR, exercise, null);

        StatsForDashboardDTO stats = exerciseService.populateCommonStatistics(exercise, exercise.isExamExercise());
        long numberOfOpenComplaints = complaintRepository.countComplaintsByExerciseIdAndComplaintType(exerciseId, ComplaintType.COMPLAINT);
        stats.setNumberOfOpenComplaints(numberOfOpenComplaints);

        long numberOfOpenMoreFeedbackRequests = complaintRepository.countByResult_Participation_Exercise_Course_IdAndComplaintType(exerciseId, ComplaintType.MORE_FEEDBACK);
        stats.setNumberOfOpenMoreFeedbackRequests(numberOfOpenMoreFeedbackRequests);
        return ResponseEntity.ok(stats);
    }

    /**
     * Reset the exercise by deleting all its participations /exercises/:exerciseId/reset This can be used by all exercise types, however they can also provide custom implementations
     *
     * @param exerciseId exercise to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping(value = "/exercises/{exerciseId}/reset")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<Void> reset(@PathVariable Long exerciseId) {
        log.debug("REST request to reset Exercise : {}", exerciseId);
        Exercise exercise = exerciseRepository.findByIdElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.INSTRUCTOR, exercise, null);
        exerciseService.reset(exercise);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, "exercise", exerciseId.toString())).build();
    }

    /**
     * DELETE /exercises/:exerciseId/cleanup : delete all build plans (except BASE) of all participations belonging to this exercise. Optionally delete and archive all repositories
     *
     * @param exerciseId         exercise to delete build plans for
     * @param deleteRepositories whether repositories should be deleted or not
     * @return ResponseEntity with status
     */
    @DeleteMapping(value = "/exercises/{exerciseId}/cleanup")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    @FeatureToggle(Feature.PROGRAMMING_EXERCISES)
    public ResponseEntity<Resource> cleanup(@PathVariable Long exerciseId, @RequestParam(defaultValue = "false") boolean deleteRepositories) {
        log.info("Start to cleanup build plans for Exercise: {}, delete repositories: {}", exerciseId, deleteRepositories);
        Exercise exercise = exerciseRepository.findByIdElseThrow(exerciseId);
        if (!authCheckService.isAtLeastInstructorForExercise(exercise)) {
            return forbidden();
        }
        exerciseService.cleanup(exerciseId, deleteRepositories);
        log.info("Cleanup build plans was successful for Exercise : {}", exerciseId);
        return ResponseEntity.ok().build();
    }

    /**
     * GET /exercises/:exerciseId/details : sends exercise details including all results for the currently logged in user
     *
     * @param exerciseId the exerciseId of the exercise to get the repos from
     * @return the ResponseEntity with status 200 (OK) and with body the exercise, or with status 404 (Not Found)
     */
    @GetMapping(value = "/exercises/{exerciseId}/details")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Exercise> getExerciseDetails(@PathVariable Long exerciseId) {
        long start = System.currentTimeMillis();
        User user = userRepository.getUserWithGroupsAndAuthorities();
        log.debug("{} requested access for exercise with exerciseId {}", user.getLogin(), exerciseId);

        Exercise exercise = exerciseService.findOneWithDetailsForStudents(exerciseId, user);

        // TODO: Create alternative route so that instructors and admins can access the exercise details
        // The users are not allowed to access the exercise details over this route if the exercise belongs to an exam
        if (exercise.isExamExercise()) {
            return forbidden();
        }

        // if exercise is not yet released to the students they should not have any access to it
        if (!authCheckService.isAllowedToSeeExercise(exercise, user)) {
            return forbidden();
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

        if (exercise instanceof ProgrammingExercise) {
            ((ProgrammingExercise) exercise).checksAndSetsIfProgrammingExerciseIsLocalSimulation();
        }
        // TODO: we should also check that the submissions do not contain sensitive data

        // remove sensitive information for students
        if (!authCheckService.isAtLeastTeachingAssistantForExercise(exercise, user)) {
            exercise.filterSensitiveInformation();
        }

        log.debug("getResultsForCurrentUser took {}ms", System.currentTimeMillis() - start);

        return ResponseUtil.wrapOrNotFound(Optional.of(exercise));
    }

    /**
     * GET /exercises/:exerciseId/toggle-second-correction
     *
     * @param exerciseId the exerciseId of the exercise to get the repos from
     * @return the ResponseEntity with status 200 (OK) and with body the exercise, or with status 404 (Not Found)
     */
    @PutMapping(value = "/exercises/{exerciseId}/toggle-second-correction")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<Boolean> toggleSecondCorrectionEnabled(@PathVariable Long exerciseId) {
        log.debug("toggleSecondCorrectionEnabled for exercise with id: {}", exerciseId);
        Exercise exercise = exerciseRepository.findByIdElseThrow(exerciseId);
        if (exercise == null) {
            throw new EntityNotFoundException("Exercise not found with id " + exerciseId);
        }
        if (!authCheckService.isAtLeastInstructorForExercise(exercise)) {
            return forbidden();
        }
        return ResponseEntity.ok(exerciseRepository.toggleSecondCorrection(exercise));
    }

}
