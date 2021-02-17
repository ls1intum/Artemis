package de.tum.in.www1.artemis.web.rest;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.repository.SubmissionRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.service.*;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;

/**
 * REST controller for managing Submission.
 */
@RestController
@RequestMapping("/api")
public class SubmissionResource {

    private final Logger log = LoggerFactory.getLogger(SubmissionResource.class);

    private static final String ENTITY_NAME = "submission";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final SubmissionRepository submissionRepository;

    private final SubmissionService submissionService;

    private final ResultService resultService;

    private final ParticipationService participationService;

    private final AuthorizationCheckService authCheckService;

    private final UserRepository userRepository;

    private final ExerciseRepository exerciseRepository;

    public SubmissionResource(SubmissionService submissionService, SubmissionRepository submissionRepository, ResultService resultService,
            ParticipationService participationService, AuthorizationCheckService authCheckService, UserRepository userRepository, ExerciseRepository exerciseRepository) {
        this.submissionService = submissionService;
        this.submissionRepository = submissionRepository;
        this.resultService = resultService;
        this.exerciseRepository = exerciseRepository;
        this.participationService = participationService;
        this.authCheckService = authCheckService;
        this.userRepository = userRepository;
    }

    /**
     * DELETE /submissions/:id : delete the "id" submission.
     *
     * @param id the id of the submission to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("/submissions/{id}")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Void> deleteSubmission(@PathVariable Long id) {
        log.debug("REST request to delete Submission : {}", id);

        Optional<Submission> submission = submissionRepository.findWithEagerResultsAndAssessorById(id);

        if (submission.isEmpty()) {
            log.error("Submission with id: " + id + " cannot be deleted");
            return ResponseEntity.notFound().build();
        }

        checkAccessPermissionAtInstructor(submission.get());

        Result result = submission.get().getLatestResult();
        if (result != null) {
            resultService.deleteResultWithComplaint(result.getId());
        }
        submissionRepository.deleteById(id);

        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, id.toString())).build();
    }

    /**
     * GET /test-run-submissions : get test run submission for an exercise.
     *
     * Only returns the users test run submission for a specific exercise
     *
     * @param exerciseId exerciseID  for which all submissions should be returned
     * @return the ResponseEntity with status 200 (OK) and the list of the latest test run submission in body
     */
    @GetMapping("/exercises/{exerciseId}/test-run-submissions")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<List<Submission>> getTestRunSubmissionsForAssessment(@PathVariable Long exerciseId) {
        log.debug("REST request to get all test run submissions for exercise {}", exerciseId);
        Exercise exercise = exerciseRepository.findByIdElseThrow(exerciseId);
        if (!exercise.isExamExercise()) {
            throw new AccessForbiddenException("You are not allowed to access this resource");
        }
        if (!authCheckService.isAtLeastInstructorForExercise(exercise)) {
            throw new AccessForbiddenException("You are not allowed to access this resource");
        }
        User user = userRepository.getUserWithGroupsAndAuthorities();

        var testRunParticipations = participationService.findTestRunParticipationForExerciseWithEagerSubmissionsResult(user.getId(), List.of(exercise));
        if (!testRunParticipations.isEmpty() && testRunParticipations.get(0).findLatestSubmission().isPresent()) {
            var latestSubmission = testRunParticipations.get(0).findLatestSubmission().get();
            if (latestSubmission.getManualResults().isEmpty()) {
                latestSubmission.addResult(submissionService.prepareTestRunSubmissionForAssessment(latestSubmission));
            }
            latestSubmission.removeAutomaticResults();
            return ResponseEntity.ok().body(List.of(latestSubmission));
        }
        else {
            return ResponseEntity.ok(List.of());
        }
    }

    private void checkAccessPermissionAtInstructor(Submission submission) {
        Course course = findCourseFromSubmission(submission);
        User user = userRepository.getUserWithGroupsAndAuthorities();

        if (!authCheckService.isAtLeastInstructorInCourse(course, user)) {
            throw new AccessForbiddenException("You are not allowed to access this resource");
        }
    }

    private Course findCourseFromSubmission(Submission submission) {
        StudentParticipation studentParticipation = (StudentParticipation) submission.getParticipation();
        if (studentParticipation.getExercise() != null && studentParticipation.getExercise().getCourseViaExerciseGroupOrCourseMember() != null) {
            return studentParticipation.getExercise().getCourseViaExerciseGroupOrCourseMember();
        }

        return participationService.findOneStudentParticipation(studentParticipation.getId()).getExercise().getCourseViaExerciseGroupOrCourseMember();
    }
}
