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
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.ResultService;
import de.tum.in.www1.artemis.service.SubmissionService;
import de.tum.in.www1.artemis.web.rest.dto.PageableSearchDTO;
import de.tum.in.www1.artemis.web.rest.dto.SearchResultPageDTO;
import de.tum.in.www1.artemis.web.rest.dto.SubmissionWithComplaintDTO;
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

    private final StudentParticipationRepository studentParticipationRepository;

    private final AuthorizationCheckService authCheckService;

    private final UserRepository userRepository;

    private final ExerciseRepository exerciseRepository;

    private final BuildLogStatisticsEntryRepository buildLogStatisticsEntryRepository;

    public SubmissionResource(SubmissionService submissionService, SubmissionRepository submissionRepository, ResultService resultService,
            StudentParticipationRepository studentParticipationRepository, AuthorizationCheckService authCheckService, UserRepository userRepository,
            ExerciseRepository exerciseRepository, BuildLogStatisticsEntryRepository buildLogStatisticsEntryRepository) {
        this.submissionService = submissionService;
        this.submissionRepository = submissionRepository;
        this.resultService = resultService;
        this.exerciseRepository = exerciseRepository;
        this.studentParticipationRepository = studentParticipationRepository;
        this.authCheckService = authCheckService;
        this.userRepository = userRepository;
        this.buildLogStatisticsEntryRepository = buildLogStatisticsEntryRepository;
    }

    /**
     * DELETE /submissions/:id : delete the "id" submission.
     *
     * @param id the id of the submission to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("/submissions/{id}")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<Void> deleteSubmission(@PathVariable Long id) {
        log.debug("REST request to delete Submission : {}", id);

        Optional<Submission> submission = submissionRepository.findWithEagerResultsAndAssessorById(id);

        if (submission.isEmpty()) {
            log.error("Submission with id: {} cannot be deleted", id);
            return ResponseEntity.notFound().build();
        }

        checkAccessPermissionAtInstructor(submission.get());
        List<Result> results = submission.get().getResults();
        for (Result result : results) {
            resultService.deleteResult(result, true);
        }
        buildLogStatisticsEntryRepository.deleteByProgrammingSubmissionId(submission.get().getId());
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
    @PreAuthorize("hasRole('EDITOR')")
    public ResponseEntity<List<Submission>> getTestRunSubmissionsForAssessment(@PathVariable Long exerciseId) {
        log.debug("REST request to get all test run submissions for exercise {}", exerciseId);
        Exercise exercise = exerciseRepository.findByIdElseThrow(exerciseId);
        if (!exercise.isExamExercise()) {
            throw new AccessForbiddenException();
        }
        if (!authCheckService.isAtLeastEditorForExercise(exercise)) {
            throw new AccessForbiddenException();
        }
        User user = userRepository.getUserWithGroupsAndAuthorities();

        var testRunParticipations = studentParticipationRepository.findTestRunParticipationsByStudentIdAndIndividualExercisesWithEagerSubmissionsResult(user.getId(),
                List.of(exercise));
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

    /**
     * Get /exercises/:exerciseId/submissions-with-complaints
     *
     * Get all submissions associated to an exercise which have complaints in,
     * but filter out the ones that are about the tutor who is doing the request, since tutors cannot act on their own complaint
     * Additionally, filter out the ones where the student is the same as the assessor as this indicated that this is a test run.
     *
     * @param exerciseId of the exercise we are interested in
     * @return the ResponseEntity with status 200 (OK) and a list of SubmissionWithComplaintDTOs. The list can be empty.
     */
    @GetMapping("/exercises/{exerciseId}/submissions-with-complaints")
    @PreAuthorize("hasRole('TA')")
    public ResponseEntity<List<SubmissionWithComplaintDTO>> getSubmissionsWithComplaintsForAssessmentDashboard(@PathVariable Long exerciseId) {
        Exercise exercise = exerciseRepository.findByIdElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.TEACHING_ASSISTANT, exercise, null);
        boolean isAtLeastInstructor = authCheckService.isAtLeastInstructorForExercise(exercise);
        List<SubmissionWithComplaintDTO> submissionWithComplaintDTOs = submissionService.getSubmissionsWithComplaintsForExercise(exerciseId, isAtLeastInstructor);

        return ResponseEntity.ok(submissionWithComplaintDTOs);
    }

    /**
     * Get /exercises/:exerciseId//more-feedback-requests-with-complaints
     *
     * Get all more feedback requests associated to an exercise which have more feedback requests in,
     * but filter out the ones that are about the tutor who is doing the request, since tutors cannot act on their own complaint
     * Additionally, filter out the ones where the student is the same as the assessor as this indicated that this is a test run.
     *
     * @param exerciseId of the exercise we are interested in
     * @return the ResponseEntity with status 200 (OK) and a list of SubmissionWithComplaintDTOs. The list can be empty.
     */
    @GetMapping("/exercises/{exerciseId}/more-feedback-requests-with-complaints")
    @PreAuthorize("hasRole('TA')")
    public ResponseEntity<List<SubmissionWithComplaintDTO>> getSubmissionsWithMoreFeedbackRequestForAssessmentDashboard(@PathVariable Long exerciseId) {
        Exercise exercise = exerciseRepository.findByIdElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.TEACHING_ASSISTANT, exercise, null);

        List<SubmissionWithComplaintDTO> submissionWithComplaintDTOs = submissionService.getSubmissionsWithMoreFeedbackRequestsForExercise(exerciseId);

        return ResponseEntity.ok(submissionWithComplaintDTOs);
    }

    /**
     * Search for all submissions by participant name. The result is pageable since there
     * might be hundreds of submissions in the DB.
     *
     * @param exerciseId exerciseId which submissions belongs to
     * @param search     the pageable search containing the page size and query string
     * @return The desired page, sorted and matching the given query
     */
    @GetMapping("exercises/{exerciseId}/submissions-for-import")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<SearchResultPageDTO<Submission>> getSubmissionsOnPageWithSize(@PathVariable Long exerciseId, PageableSearchDTO<String> search) {
        log.debug("REST request to get all Submissions for import : {}", exerciseId);

        Exercise exercise = exerciseRepository.findByIdElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.INSTRUCTOR, exercise, null);

        return ResponseEntity.ok(submissionService.getSubmissionsOnPageWithSize(search, exercise.getId()));
    }

    private void checkAccessPermissionAtInstructor(Submission submission) {
        Course course = findCourseFromSubmission(submission);
        User user = userRepository.getUserWithGroupsAndAuthorities();

        if (!authCheckService.isAtLeastInstructorInCourse(course, user)) {
            throw new AccessForbiddenException();
        }
    }

    private Course findCourseFromSubmission(Submission submission) {
        Participation participation = submission.getParticipation();
        if (participation.getExercise() != null && participation.getExercise().getCourseViaExerciseGroupOrCourseMember() != null) {
            return participation.getExercise().getCourseViaExerciseGroupOrCourseMember();
        }

        return studentParticipationRepository.findByIdElseThrow(participation.getId()).getExercise().getCourseViaExerciseGroupOrCourseMember();
    }
}
