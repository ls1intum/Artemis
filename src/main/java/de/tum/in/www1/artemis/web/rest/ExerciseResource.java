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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.TutorParticipationStatus;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.*;
import de.tum.in.www1.artemis.web.rest.dto.StatsTutorLeaderboardDTO;
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

    private final ExerciseRepository exerciseRepository;

    private final ExerciseService exerciseService;

    private final UserService userService;

    private final CourseService courseService;

    private final ParticipationService participationService;

    private final AuthorizationCheckService authCheckService;

    private final TutorParticipationService tutorParticipationService;

    private final ExampleSubmissionRepository exampleSubmissionRepository;

    private final ObjectMapper objectMapper;

    private final TextAssessmentService textAssessmentService;

    private final ComplaintRepository complaintRepository;

    private final TextSubmissionService textSubmissionService;

    private final ModelingSubmissionService modelingSubmissionService;

    private final ResultService resultService;

    public ExerciseResource(ExerciseService exerciseService, ParticipationService participationService, UserService userService, CourseService courseService,
            AuthorizationCheckService authCheckService, TutorParticipationService tutorParticipationService, ExampleSubmissionRepository exampleSubmissionRepository,
            ObjectMapper objectMapper, TextAssessmentService textAssessmentService, ComplaintRepository complaintRepository, TextSubmissionService textSubmissionService,
            ModelingSubmissionService modelingSubmissionService, ResultService resultService) {
        this.exerciseService = exerciseService;
        this.participationService = participationService;
        this.userService = userService;
        this.courseService = courseService;
        this.authCheckService = authCheckService;
        this.tutorParticipationService = tutorParticipationService;
        this.exampleSubmissionRepository = exampleSubmissionRepository;
        this.objectMapper = objectMapper;
        this.textAssessmentService = textAssessmentService;
        this.complaintRepository = complaintRepository;
        this.textSubmissionService = textSubmissionService;
        this.modelingSubmissionService = modelingSubmissionService;
        this.resultService = resultService;
    }

    /**
     * GET /courses/:courseId/exercises : get all exercises for the given course
     *
     * @param courseId the course for which to retrieve all exercises
     * @return the ResponseEntity with status 200 (OK) and the list of exercises in body
     */
    @GetMapping(value = "/courses/{courseId}/exercises")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Collection<Exercise>> getExercisesForCourse(@PathVariable Long courseId) {
        log.debug("REST request to get Exercises for Course : {}", courseId);

        Course course = courseService.findOne(courseId);
        User user = userService.getUserWithGroupsAndAuthorities();
        if (!authCheckService.isStudentInCourse(course, user) && !authCheckService.isTeachingAssistantInCourse(course, user) && !authCheckService.isInstructorInCourse(course, user)
                && !authCheckService.isAdmin()) {
            return forbidden();
        }

        List<Exercise> result = exerciseService.findAllExercisesByCourseId(course, user);
        // remove sensitive information for students
        if (!authCheckService.isAtLeastTeachingAssistantInCourse(course, user)) {
            result.forEach(Exercise::filterSensitiveInformation);
        }

        return ResponseEntity.ok(result);
    }

    /**
     * GET /exercises/:id : get the "id" exercise.
     *
     * @param id the id of the exercise to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the exercise, or with status 404 (Not Found)
     */
    @GetMapping("/exercises/{id}")
    @PreAuthorize("hasAnyRole('USER','TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Exercise> getExercise(@PathVariable Long id) {
        log.debug("REST request to get Exercise : {}", id);

        User student = userService.getUserWithGroupsAndAuthorities();
        Exercise exercise = exerciseService.findOne(id);

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
     * @param id the id of the exercise to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the stats, or with status 404 (Not Found)
     */
    @GetMapping("/exercises/{id}/stats-for-tutor-dashboard")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<JsonNode> getStatsForTutorExerciseDashboard(@PathVariable Long id) {
        log.debug("REST request to get exercise statistics for tutor dashboard : {}", id);
        Exercise exercise = exerciseService.findOne(id);
        User user = userService.getUserWithGroupsAndAuthorities();

        if (!authCheckService.isAtLeastTeachingAssistantForExercise(exercise)) {
            return forbidden();
        }

        ObjectNode data = populateCommonStatistics(id);
        long numberOfTutorAssessments = resultService.countNumberOfAssessmentsForTutorInExercise(id, user.getId());
        data.set("numberOfTutorAssessments", objectMapper.valueToTree(numberOfTutorAssessments));

        long numberOfTutorComplaints = complaintRepository.countByResult_Participation_Exercise_IdAndResult_Assessor_Id(id, user.getId());
        data.set("numberOfTutorComplaints", objectMapper.valueToTree(numberOfTutorComplaints));

        return ResponseEntity.ok(data);
    }

    /**
     * Given an exercise id, it creates an object node with numberOfSubmissions, numberOfAssessments and numberOfComplaints, that are used by both stats for tutor dashboard and for
     * instructor dashboard
     *
     * @param exerciseId - the exercise we are interested in
     * @return a object node with the stats
     */
    private ObjectNode populateCommonStatistics(@PathVariable Long exerciseId) {
        ObjectNode data = objectMapper.createObjectNode();

        long numberOfSubmissions = textSubmissionService.countSubmissionsToAssessByExerciseId(exerciseId);
        numberOfSubmissions += modelingSubmissionService.countSubmissionsToAssessByExerciseId(exerciseId);
        data.set("numberOfSubmissions", objectMapper.valueToTree(numberOfSubmissions));

        long numberOfAssessments = resultService.countNumberOfAssessmentsForExercise(exerciseId);
        data.set("numberOfAssessments", objectMapper.valueToTree(numberOfAssessments));

        long numberOfComplaints = complaintRepository.countByResult_Participation_Exercise_Id(exerciseId);
        data.set("numberOfComplaints", objectMapper.valueToTree(numberOfComplaints));

        return data;
    }

    /**
     * GET /exercises/:id/stats-for-instructor-dashboard A collection of useful statistics for the instructor exercise dashboard of the exercise with the given id
     *
     * @param id the id of the exercise to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the stats, or with status 404 (Not Found)
     */
    @GetMapping("/exercises/{id}/stats-for-instructor-dashboard")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<JsonNode> getStatsForInstructorExerciseDashboard(@PathVariable Long id) {
        log.debug("REST request to get exercise statistics for instructor dashboard : {}", id);
        Exercise exercise = exerciseService.findOne(id);

        if (!authCheckService.isAtLeastTeachingAssistantForExercise(exercise)) {
            return forbidden();
        }

        ObjectNode data = populateCommonStatistics(id);
        long numberOfOpenComplaints = complaintRepository.countByResult_Participation_Exercise_Id(id);
        data.set("numberOfOpenComplaints", objectMapper.valueToTree(numberOfOpenComplaints));

        List<StatsTutorLeaderboardDTO> tutorLeaderboard = textAssessmentService.calculateTutorLeaderboardForExercise(id);
        data.set("tutorLeaderboard", objectMapper.valueToTree(tutorLeaderboard));

        return ResponseEntity.ok(data);
    }

    /**
     * DELETE /exercises/:id : delete the "id" exercise.
     *
     * @param id the id of the exercise to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("/exercises/{id}")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        log.debug("REST request to delete Exercise : {}", id);
        Exercise exercise = exerciseService.findOne(id);
        if (Optional.ofNullable(exercise).isPresent()) {
            if (!authCheckService.isAtLeastInstructorForExercise(exercise))
                return forbidden();
            exerciseService.delete(exercise, true, false);
        }
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, id.toString())).build();
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
    public ResponseEntity<Resource> cleanup(@PathVariable Long id, @RequestParam(defaultValue = "false") boolean deleteRepositories) throws IOException {
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
     */
    @GetMapping(value = "/exercises/{exerciseId}/participations/{studentIds}")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Resource> exportSubmissions(@PathVariable Long exerciseId, @PathVariable String studentIds) throws IOException {
        studentIds = studentIds.replaceAll(" ", "");
        Exercise exercise = exerciseService.findOne(exerciseId);

        if (!authCheckService.isAtLeastTeachingAssistantForExercise(exercise))
            return forbidden();

        List<String> studentList = Arrays.asList(studentIds.split("\\s*,\\s*"));
        if (studentList.isEmpty() || studentList == null) {
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
    public ResponseEntity<Exercise> getResultsForCurrentStudent(@PathVariable Long exerciseId) {
        long start = System.currentTimeMillis();
        User student = userService.getUserWithGroupsAndAuthorities();
        log.debug(student.getLogin() + " requested access for exercise with id " + exerciseId, exerciseId);

        Exercise exercise = exerciseService.findOne(exerciseId);
        // if exercise is not yet released to the students they should not have any access to it
        if (!authCheckService.isAllowedToSeeExercise(exercise, student)) {
            return forbidden();
        }

        if (exercise != null) {
            List<Participation> participations = participationService.findByExerciseIdAndStudentIdWithEagerResults(exercise.getId(), student.getId());

            exercise.setParticipations(new HashSet<>());

            for (Participation participation : participations) {

                participation.setResults(exercise.findResultsFilteredForStudents(participation));
                exercise.addParticipation(participation);
            }

            // remove sensitive information for students
            boolean isStudent = !authCheckService.isAtLeastTeachingAssistantForExercise(exercise, student);
            if (isStudent) {
                exercise.filterSensitiveInformation();
            }
        }

        log.debug("getResultsForCurrentStudent took " + (System.currentTimeMillis() - start) + "ms");

        return ResponseUtil.wrapOrNotFound(Optional.ofNullable(exercise));
    }
}
