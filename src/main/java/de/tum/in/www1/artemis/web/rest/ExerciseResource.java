package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.forbidden;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.ComplaintType;
import de.tum.in.www1.artemis.domain.enumeration.TutorParticipationStatus;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.*;
import de.tum.in.www1.artemis.web.rest.dto.StatsForInstructorDashboardDTO;
import de.tum.in.www1.artemis.web.rest.dto.TutorLeaderboardDTO;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;
import io.github.jhipster.web.util.ResponseUtil;

/**
 * REST controller for managing Exercise.
 */
@RestController
@RequestMapping({ "/api", "/api_basic" })
@PreAuthorize("hasRole('ADMIN')")
public class ExerciseResource {

    private final Logger log = LoggerFactory.getLogger(ExerciseResource.class);

    private static final String ENTITY_NAME = "exercise";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final ExerciseService exerciseService;

    private final UserService userService;

    private final CourseService courseService;

    private final ParticipationService participationService;

    private final AuthorizationCheckService authCheckService;

    private final TutorParticipationService tutorParticipationService;

    private final ExampleSubmissionRepository exampleSubmissionRepository;

    private final ObjectMapper objectMapper;

    private final ComplaintRepository complaintRepository;

    private final TextSubmissionService textSubmissionService;

    private final ModelingSubmissionService modelingSubmissionService;

    private final ResultService resultService;

    private final TutorLeaderboardService tutorLeaderboardService;

    public ExerciseResource(ExerciseService exerciseService, ParticipationService participationService, UserService userService, CourseService courseService,
            AuthorizationCheckService authCheckService, TutorParticipationService tutorParticipationService, ExampleSubmissionRepository exampleSubmissionRepository,
            ObjectMapper objectMapper, ComplaintRepository complaintRepository, TextSubmissionService textSubmissionService, ModelingSubmissionService modelingSubmissionService,
            ResultService resultService, TutorLeaderboardService tutorLeaderboardService) {
        this.exerciseService = exerciseService;
        this.participationService = participationService;
        this.userService = userService;
        this.courseService = courseService;
        this.authCheckService = authCheckService;
        this.tutorParticipationService = tutorParticipationService;
        this.exampleSubmissionRepository = exampleSubmissionRepository;
        this.objectMapper = objectMapper;
        this.complaintRepository = complaintRepository;
        this.textSubmissionService = textSubmissionService;
        this.modelingSubmissionService = modelingSubmissionService;
        this.resultService = resultService;
        this.tutorLeaderboardService = tutorLeaderboardService;
    }

    /**
     * GET /exercises/:id : get the "id" exercise.
     *
     * @param exerciseId the id of the exercise to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the exercise, or with status 404 (Not Found)
     */
    @GetMapping("/exercises/{exerciseId}")
    @PreAuthorize("hasAnyRole('USER','TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Exercise> getExercise(@PathVariable Long exerciseId) {
        log.debug("REST request to get Exercise : {}", exerciseId);

        User student = userService.getUserWithGroupsAndAuthorities();
        Exercise exercise = exerciseService.findOne(exerciseId);

        if (!authCheckService.isAllowedToSeeExercise(exercise, student))
            return forbidden();

        boolean isStudent = !authCheckService.isAtLeastTeachingAssistantForExercise(exercise, student);
        if (isStudent) {
            exercise.filterSensitiveInformation();
        }

        return ResponseUtil.wrapOrNotFound(Optional.ofNullable(exercise));
    }

    /**
     * GET /exercises/:id : get the "id" exercise with data useful for tutors.
     *
     * @param id the id of the exercise to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the exercise, or with status 404 (Not Found)
     */
    @GetMapping("/exercises/{id}/for-tutor-dashboard")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Exercise> getExerciseForTutorDashboard(@PathVariable Long id) {
        log.debug("REST request to get Exercise for tutor dashboard : {}", id);
        Exercise exercise = exerciseService.findOne(id);
        User user = userService.getUserWithGroupsAndAuthorities();

        if (!authCheckService.isAtLeastTeachingAssistantForExercise(exercise)) {
            return forbidden();
        }

        // TODO CZ: load results of submissions eagerly to prevent additional database calls
        List<ExampleSubmission> exampleSubmissions = this.exampleSubmissionRepository.findAllByExerciseId(id);
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
     * GET /exercises/:id/stats-for-tutor-dashboard A collection of useful statistics for the tutor exercise dashboard of the exercise with the given id
     *
     * @param exerciseId the id of the exercise to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the stats, or with status 404 (Not Found)
     */
    @GetMapping("/exercises/{exerciseId}/stats-for-tutor-dashboard")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<StatsForInstructorDashboardDTO> getStatsForTutorExerciseDashboard(@PathVariable Long exerciseId) {
        log.debug("REST request to get exercise statistics for tutor dashboard : {}", exerciseId);
        Exercise exercise = exerciseService.findOne(exerciseId);

        if (!authCheckService.isAtLeastTeachingAssistantForExercise(exercise)) {
            return forbidden();
        }

        StatsForInstructorDashboardDTO stats = populateCommonStatistics(exercise);

        return ResponseEntity.ok(stats);
    }

    /**
     * Given an exercise id, it creates an object node with numberOfSubmissions, numberOfAssessments, numberOfComplaints and numberOfMoreFeedbackRequests, that are used by both
     * stats for tutor dashboard and for instructor dashboard
     *
     * @param exercise - the exercise we are interested in
     * @return a object node with the stats
     */
    private StatsForInstructorDashboardDTO populateCommonStatistics(Exercise exercise) {
        Long exerciseId = exercise.getId();
        StatsForInstructorDashboardDTO stats = new StatsForInstructorDashboardDTO();

        Long numberOfSubmissions = textSubmissionService.countSubmissionsToAssessByExerciseId(exerciseId)
                + modelingSubmissionService.countSubmissionsToAssessByExerciseId(exerciseId);
        stats.setNumberOfSubmissions(numberOfSubmissions);

        Long numberOfAssessments = resultService.countNumberOfAssessmentsForExercise(exerciseId);
        stats.setNumberOfAssessments(numberOfAssessments);

        Long numberOfMoreFeedbackRequests = complaintRepository.countByResult_Participation_Exercise_IdAndComplaintType(exerciseId, ComplaintType.MORE_FEEDBACK);
        stats.setNumberOfMoreFeedbackRequests(numberOfMoreFeedbackRequests);

        Long numberOfComplaints = complaintRepository.countByResult_Participation_Exercise_IdAndComplaintType(exerciseId, ComplaintType.COMPLAINT);
        stats.setNumberOfComplaints(numberOfComplaints);

        List<TutorLeaderboardDTO> leaderboardEntries = tutorLeaderboardService.getExerciseLeaderboard(exercise);
        stats.setTutorLeaderboardEntries(leaderboardEntries);

        return stats;
    }

    /**
     * GET /exercises/:id/stats-for-instructor-dashboard A collection of useful statistics for the instructor exercise dashboard of the exercise with the given id
     *
     * @param exerciseId the id of the exercise to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the stats, or with status 404 (Not Found)
     */
    @GetMapping("/exercises/{exerciseId}/stats-for-instructor-dashboard")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<StatsForInstructorDashboardDTO> getStatsForInstructorExerciseDashboard(@PathVariable Long exerciseId) {
        log.debug("REST request to get exercise statistics for instructor dashboard : {}", exerciseId);
        Exercise exercise = exerciseService.findOne(exerciseId);

        if (!authCheckService.isAtLeastTeachingAssistantForExercise(exercise)) {
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
     * DELETE /exercises/:id : delete the "id" exercise.
     *
     * @param exerciseId the id of the exercise to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("/exercises/{exerciseId}")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long exerciseId) {
        log.debug("REST request to delete Exercise : {}", exerciseId);
        Exercise exercise = exerciseService.findOne(exerciseId);
        if (Optional.ofNullable(exercise).isPresent()) {
            if (!authCheckService.isAtLeastInstructorForExercise(exercise))
                return forbidden();
            exerciseService.delete(exercise, true, false);
        }
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, exerciseId.toString())).build();
    }

    /**
     * Reset the exercise by deleting all its partcipations /exercises/:id/reset This can be used by all exercise types, however they can also provide custom implementations
     *
     * @param id the id of the exercise to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping(value = "/exercises/{id}/reset")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Void> reset(@PathVariable Long id) {
        log.debug("REST request to reset Exercise : {}", id);
        Exercise exercise = exerciseService.findOneLoadParticipations(id);
        if (!authCheckService.isAtLeastInstructorForExercise(exercise))
            return forbidden();
        exerciseService.reset(exercise);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, "exercise", id.toString())).build();
    }

    /**
     * DELETE /exercises/:id/cleanup : delete all build plans (except BASE) of all participations belonging to this exercise. Optionally delete and archive all repositories
     *
     * @param id                 the id of the exercise to delete build plans for
     * @param deleteRepositories whether repositories should be deleted or not
     * @return ResponseEntity with status
     */
    @DeleteMapping(value = "/exercises/{id}/cleanup")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Resource> cleanup(@PathVariable Long id, @RequestParam(defaultValue = "false") boolean deleteRepositories) {
        log.info("Start to cleanup build plans for Exercise: {}, delete repositories: {}", id, deleteRepositories);
        Exercise exercise = exerciseService.findOne(id);
        if (!authCheckService.isAtLeastInstructorForExercise(exercise))
            return forbidden();
        exerciseService.cleanup(id, deleteRepositories);
        log.info("Cleanup build plans was successful for Exercise : {}", id);
        return ResponseEntity.ok().headers(HeaderUtil.createAlert(applicationName, "Cleanup was successful. Repositories have been deleted: " + deleteRepositories, "")).build();
    }

    /**
     * GET /exercises/:id/archive : archive all repositories (except BASE) of all participations belonging to this exercise into a zip file and provide a downloadable link.
     *
     * @param id the id of the exercise to delete and archive the repositories
     * @return ResponseEntity with status
     * @throws IOException if repositories can't be archived
     */
    @GetMapping(value = "/exercises/{id}/archive")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Resource> archiveRepositories(@PathVariable Long id) throws IOException {
        log.info("Start to archive repositories for Exercise : {}", id);
        Exercise exercise = exerciseService.findOne(id);
        if (!authCheckService.isAtLeastInstructorForExercise(exercise))
            return forbidden();
        File zipFile = exerciseService.archive(id);
        if (zipFile == null) {
            return ResponseEntity.noContent().headers(HeaderUtil.createAlert(applicationName,
                    "There was an error on the server and the zip file could not be created, possibly because all repositories have already been deleted or this is not a programming exercise.",
                    "")).build();
        }
        InputStreamResource resource = new InputStreamResource(new FileInputStream(zipFile));
        log.info("Archive repositories was successful for Exercise : {}", id);
        return ResponseEntity.ok().contentLength(zipFile.length()).contentType(MediaType.APPLICATION_OCTET_STREAM).header("filename", zipFile.getName()).body(resource);
    }

    /**
     * GET /exercises/:exerciseId/participations/:studentIds : sends all submissions from studentlist as zip
     *
     * @param exerciseId the id of the exercise to get the repos from
     * @param studentIds the studentIds seperated via semicolon to get their submissions
     * @return ResponseEntity with status
     * @throws IOException if submissions can't be zipped
     */
    @GetMapping(value = "/exercises/{exerciseId}/participations/{studentIds}")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Resource> exportSubmissions(@PathVariable Long exerciseId, @PathVariable String studentIds) throws IOException {
        studentIds = studentIds.replaceAll(" ", "");
        Exercise exercise = exerciseService.findOne(exerciseId);

        // TODO: allow multiple options:
        // - one boolean flag per stager task (see exportParticipations)
        // - one boolean flag that all student submissions should be downloaded

        if (!authCheckService.isAtLeastTeachingAssistantForExercise(exercise))
            return forbidden();

        List<String> studentList = Arrays.asList(studentIds.split("\\s*,\\s*"));
        if (studentList.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).headers(HeaderUtil.createAlert(applicationName, "Given studentlist for export was empty or malformed", ""))
                    .build();
        }

        File zipFile = exerciseService.exportParticipations(exerciseId, studentList);
        if (zipFile == null) {
            return ResponseEntity.noContent().headers(HeaderUtil.createAlert(applicationName, "There was an error on the server and the zip file could not be created", ""))
                    .build();
        }
        InputStreamResource resource = new InputStreamResource(new FileInputStream(zipFile));

        return ResponseEntity.ok().contentLength(zipFile.length()).contentType(MediaType.APPLICATION_OCTET_STREAM).header("filename", zipFile.getName()).body(resource);
    }

    /**
     * GET /exercises/:exerciseId/results : sends all results for a exercise and the logged in user
     *
     * @param exerciseId the id of the exercise to get the repos from
     * @return the ResponseEntity with status 200 (OK) and with body the exercise, or with status 404 (Not Found)
     */
    @GetMapping(value = "/exercises/{exerciseId}/results")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Exercise> getResultsForCurrentUser(@PathVariable Long exerciseId) {
        long start = System.currentTimeMillis();
        User student = userService.getUserWithGroupsAndAuthorities();
        log.debug(student.getLogin() + " requested access for exercise with id " + exerciseId, exerciseId);

        Exercise exercise = exerciseService.findOne(exerciseId);
        // if exercise is not yet released to the students they should not have any access to it
        if (!authCheckService.isAllowedToSeeExercise(exercise, student)) {
            return forbidden();
        }

        if (exercise != null) {
            List<StudentParticipation> participations = participationService.findByExerciseIdAndStudentIdWithEagerResults(exercise.getId(), student.getId());

            exercise.setParticipations(new HashSet<>());

            for (StudentParticipation participation : participations) {

                participation.setResults(exercise.findResultsFilteredForStudents(participation));
                // By filtering the results available yet, they can become null for the exercise.
                if (participation.getResults() != null) {
                    participation.getResults().forEach(r -> r.setAssessor(null));
                }
                exercise.addParticipation(participation);
            }

            // remove sensitive information for students
            boolean isStudent = !authCheckService.isAtLeastTeachingAssistantForExercise(exercise, student);
            if (isStudent) {
                exercise.filterSensitiveInformation();
            }
        }

        log.debug("getResultsForCurrentUser took " + (System.currentTimeMillis() - start) + "ms");

        return ResponseUtil.wrapOrNotFound(Optional.ofNullable(exercise));
    }
}
