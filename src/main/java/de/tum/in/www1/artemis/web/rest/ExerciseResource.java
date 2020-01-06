package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.badRequest;
import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.forbidden;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

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
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.participation.TutorParticipation;
import de.tum.in.www1.artemis.repository.ComplaintRepository;
import de.tum.in.www1.artemis.repository.ExampleSubmissionRepository;
import de.tum.in.www1.artemis.service.*;
import de.tum.in.www1.artemis.service.feature.Feature;
import de.tum.in.www1.artemis.service.feature.FeatureToggle;
import de.tum.in.www1.artemis.web.rest.dto.StatsForInstructorDashboardDTO;
import de.tum.in.www1.artemis.web.rest.dto.TutorLeaderboardDTO;
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

    private final UserService userService;

    private final ParticipationService participationService;

    private final AuthorizationCheckService authCheckService;

    private final TutorParticipationService tutorParticipationService;

    private final ExampleSubmissionRepository exampleSubmissionRepository;

    private final ComplaintRepository complaintRepository;

    private final TextSubmissionService textSubmissionService;

    private final ModelingSubmissionService modelingSubmissionService;

    private final FileUploadSubmissionService fileUploadSubmissionService;

    private final ResultService resultService;

    private final TutorLeaderboardService tutorLeaderboardService;

    private final ProgrammingExerciseService programmingExerciseService;

    public ExerciseResource(ExerciseService exerciseService, ParticipationService participationService, UserService userService, AuthorizationCheckService authCheckService,
            TutorParticipationService tutorParticipationService, ExampleSubmissionRepository exampleSubmissionRepository, ComplaintRepository complaintRepository,
            TextSubmissionService textSubmissionService, ModelingSubmissionService modelingSubmissionService, ResultService resultService,
            FileUploadSubmissionService fileUploadSubmissionService, TutorLeaderboardService tutorLeaderboardService, ProgrammingExerciseService programmingExerciseService) {
        this.exerciseService = exerciseService;
        this.participationService = participationService;
        this.userService = userService;
        this.authCheckService = authCheckService;
        this.tutorParticipationService = tutorParticipationService;
        this.exampleSubmissionRepository = exampleSubmissionRepository;
        this.complaintRepository = complaintRepository;
        this.textSubmissionService = textSubmissionService;
        this.modelingSubmissionService = modelingSubmissionService;
        this.resultService = resultService;
        this.fileUploadSubmissionService = fileUploadSubmissionService;
        this.tutorLeaderboardService = tutorLeaderboardService;
        this.programmingExerciseService = programmingExerciseService;
    }

    /**
     * GET /exercises/:exerciseId : get the "exerciseId" exercise.
     *
     * @param exerciseId the exerciseId of the exercise to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the exercise, or with status 404 (Not Found)
     */
    @GetMapping("/exercises/{exerciseId}")
    @PreAuthorize("hasAnyRole('USER','TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Exercise> getExercise(@PathVariable Long exerciseId) {
        log.debug("REST request to get Exercise : {}", exerciseId);

        User user = userService.getUserWithGroupsAndAuthorities();
        Exercise exercise = exerciseService.findOneWithCategories(exerciseId);

        if (!authCheckService.isAllowedToSeeExercise(exercise, user)) {
            return forbidden();
        }

        if (!authCheckService.isAtLeastTeachingAssistantForExercise(exercise, user)) {
            exercise.filterSensitiveInformation();
        }

        return ResponseUtil.wrapOrNotFound(Optional.of(exercise));
    }

    /**
     * GET /exercises/:exerciseId : get the "exerciseId" exercise with data useful for tutors.
     *
     * @param exerciseId the exerciseId of the exercise to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the exercise, or with status 404 (Not Found)
     */
    @GetMapping("/exercises/{exerciseId}/for-tutor-dashboard")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Exercise> getExerciseForTutorDashboard(@PathVariable Long exerciseId) {
        log.debug("REST request to get Exercise for tutor dashboard : {}", exerciseId);
        Exercise exercise = exerciseService.findOneWithAdditionalElements(exerciseId);
        User user = userService.getUserWithGroupsAndAuthorities();

        if (!authCheckService.isAtLeastTeachingAssistantForExercise(exercise)) {
            return forbidden();
        }
        // Programming exercises with only automatic assessment should *NOT* be available on the tutor dashboard!
        if (exercise instanceof ProgrammingExercise && exercise.getAssessmentType().equals(AssessmentType.AUTOMATIC)) {
            return badRequest();
        }

        // TODO CZ: load results of submissions eagerly to prevent additional database calls
        List<ExampleSubmission> exampleSubmissions = this.exampleSubmissionRepository.findAllByExerciseId(exerciseId);
        // Do not provide example submissions without any assessment
        exampleSubmissions.removeIf(exampleSubmission -> exampleSubmission.getSubmission().getResult() == null);
        exercise.setExampleSubmissions(new HashSet<>(exampleSubmissions));

        TutorParticipation tutorParticipation = tutorParticipationService.findByExerciseAndTutor(exercise, user);
        if (exampleSubmissions.size() == 0 && tutorParticipation.getStatus().equals(TutorParticipationStatus.REVIEWED_INSTRUCTIONS)) {
            tutorParticipation.setStatus(TutorParticipationStatus.TRAINED);
        }
        exercise.setTutorParticipations(Collections.singleton(tutorParticipation));

        return ResponseUtil.wrapOrNotFound(Optional.of(exercise));
    }

    /**
     * GET /exercises/:exerciseId/stats-for-tutor-dashboard A collection of useful statistics for the tutor exercise dashboard of the exercise with the given exerciseId
     *
     * @param exerciseId the exerciseId of the exercise to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the stats, or with status 404 (Not Found)
     */
    @GetMapping("/exercises/{exerciseId}/stats-for-tutor-dashboard")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<StatsForInstructorDashboardDTO> getStatsForTutorExerciseDashboard(@PathVariable Long exerciseId) {
        log.debug("REST request to get exercise statistics for tutor dashboard : {}", exerciseId);
        Exercise exercise = exerciseService.findOneWithAdditionalElements(exerciseId);

        if (!authCheckService.isAtLeastTeachingAssistantForExercise(exercise)) {
            return forbidden();
        }

        StatsForInstructorDashboardDTO stats = populateCommonStatistics(exercise);

        return ResponseEntity.ok(stats);
    }

    /**
     * Given an exercise exerciseId, it creates an object node with numberOfSubmissions, numberOfAssessments, numberOfComplaints and numberOfMoreFeedbackRequests, that are used by both
     * stats for tutor dashboard and for instructor dashboard
     *
     * @param exercise - the exercise we are interested in
     * @return a object node with the stats
     */
    private StatsForInstructorDashboardDTO populateCommonStatistics(Exercise exercise) {
        Long exerciseId = exercise.getId();
        StatsForInstructorDashboardDTO stats = new StatsForInstructorDashboardDTO();

        long numberOfSubmissions = 0L;
        if (exercise instanceof TextExercise) {
            numberOfSubmissions = textSubmissionService.countSubmissionsToAssessByExerciseId(exercise.getId());
        }
        else if (exercise instanceof ModelingExercise) {
            numberOfSubmissions += modelingSubmissionService.countSubmissionsToAssessByExerciseId(exercise.getId());
        }
        else if (exercise instanceof FileUploadExercise) {
            numberOfSubmissions += fileUploadSubmissionService.countSubmissionsToAssessByExerciseId(exercise.getId());
        }
        else if (exercise instanceof ProgrammingExercise) {
            numberOfSubmissions += programmingExerciseService.countSubmissions(exercise.getId());
        }
        stats.setNumberOfSubmissions(numberOfSubmissions);

        Long numberOfAssessments = resultService.countNumberOfFinishedAssessmentsForExercise(exerciseId);
        stats.setNumberOfAssessments(numberOfAssessments);

        Long numberOfAutomaticAssistedAssessments = resultService.countNumberOfAutomaticAssistedAssessmentsForExercise(exerciseId);
        stats.setNumberOfAutomaticAssistedAssessments(numberOfAutomaticAssistedAssessments);

        Long numberOfMoreFeedbackRequests = complaintRepository.countByResult_Participation_Exercise_IdAndComplaintType(exerciseId, ComplaintType.MORE_FEEDBACK);
        stats.setNumberOfMoreFeedbackRequests(numberOfMoreFeedbackRequests);

        Long numberOfComplaints = complaintRepository.countByResult_Participation_Exercise_IdAndComplaintType(exerciseId, ComplaintType.COMPLAINT);
        stats.setNumberOfComplaints(numberOfComplaints);

        List<TutorLeaderboardDTO> leaderboardEntries = tutorLeaderboardService.getExerciseLeaderboard(exercise);
        stats.setTutorLeaderboardEntries(leaderboardEntries);

        return stats;
    }

    /**
     * GET /exercises/:exerciseId/stats-for-instructor-dashboard A collection of useful statistics for the instructor exercise dashboard of the exercise with the given exerciseId
     *
     * @param exerciseId the exerciseId of the exercise to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the stats, or with status 404 (Not Found)
     */
    @GetMapping("/exercises/{exerciseId}/stats-for-instructor-dashboard")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<StatsForInstructorDashboardDTO> getStatsForInstructorExerciseDashboard(@PathVariable Long exerciseId) {
        log.debug("REST request to get exercise statistics for instructor dashboard : {}", exerciseId);
        Exercise exercise = exerciseService.findOneWithAdditionalElements(exerciseId);

        if (!authCheckService.isAtLeastInstructorForExercise(exercise)) {
            return forbidden();
        }

        StatsForInstructorDashboardDTO stats = populateCommonStatistics(exercise);
        long numberOfOpenComplaints = complaintRepository.countByResult_Participation_Exercise_IdAndComplaintType(exerciseId, ComplaintType.COMPLAINT);
        stats.setNumberOfOpenComplaints(numberOfOpenComplaints);

        long numberOfOpenMoreFeedbackRequests = complaintRepository.countByResult_Participation_Exercise_Course_IdAndComplaintType(exerciseId, ComplaintType.MORE_FEEDBACK);
        stats.setNumberOfOpenMoreFeedbackRequests(numberOfOpenMoreFeedbackRequests);

        return ResponseEntity.ok(stats);
    }

    /**
     * Reset the exercise by deleting all its partcipations /exercises/:exerciseId/reset This can be used by all exercise types, however they can also provide custom implementations
     *
     * @param exerciseId exercise to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping(value = "/exercises/{exerciseId}/reset")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Void> reset(@PathVariable Long exerciseId) {
        log.debug("REST request to reset Exercise : {}", exerciseId);
        Exercise exercise = exerciseService.findOne(exerciseId);
        if (!authCheckService.isAtLeastInstructorForExercise(exercise)) {
            return forbidden();
        }
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
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    @FeatureToggle(Feature.PROGRAMMING_EXERCISES)
    public ResponseEntity<Resource> cleanup(@PathVariable Long exerciseId, @RequestParam(defaultValue = "false") boolean deleteRepositories) {
        log.info("Start to cleanup build plans for Exercise: {}, delete repositories: {}", exerciseId, deleteRepositories);
        Exercise exercise = exerciseService.findOneWithAdditionalElements(exerciseId);
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
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Exercise> getExerciseDetails(@PathVariable Long exerciseId) {
        // TODO: refactor this and load
        // * the exercise (without the course, no template / solution participations)
        // * all submissions (with their result) of the user (to be displayed in the result history)
        // * the student questions
        // * the hints
        // also see exercise.service.ts and course-exercise-details.component.ts
        long start = System.currentTimeMillis();
        User user = userService.getUserWithGroupsAndAuthorities();
        log.debug(user.getLogin() + " requested access for exercise with exerciseId " + exerciseId, exerciseId);

        Exercise exercise = exerciseService.findOne(exerciseId);
        // if exercise is not yet released to the students they should not have any access to it
        if (!authCheckService.isAllowedToSeeExercise(exercise, user)) {
            return forbidden();
        }

        if (exercise != null) {
            List<StudentParticipation> participations = participationService.findByExerciseIdAndStudentIdWithEagerResultsAndSubmissions(exercise.getId(), user.getId());

            exercise.setStudentParticipations(new HashSet<>());

            for (StudentParticipation participation : participations) {

                participation.setResults(exercise.findResultsFilteredForStudents(participation));
                // By filtering the results available yet, they can become null for the exercise.
                if (participation.getResults() != null) {
                    participation.getResults().forEach(r -> r.setAssessor(null));
                }
                exercise.addParticipation(participation);
            }

            // TODO: we should also check that the submissions do not contain sensitive data

            // remove sensitive information for students
            if (!authCheckService.isAtLeastTeachingAssistantForExercise(exercise, user)) {
                exercise.filterSensitiveInformation();
            }
        }

        log.debug("getResultsForCurrentUser took " + (System.currentTimeMillis() - start) + "ms");

        return ResponseUtil.wrapOrNotFound(Optional.ofNullable(exercise));
    }
}
