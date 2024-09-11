package de.tum.cit.aet.artemis.web.rest;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastEditor;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastInstructor;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastTutor;
import de.tum.cit.aet.artemis.domain.Course;
import de.tum.cit.aet.artemis.domain.Exercise;
import de.tum.cit.aet.artemis.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.domain.Result;
import de.tum.cit.aet.artemis.domain.Submission;
import de.tum.cit.aet.artemis.domain.SubmissionVersion;
import de.tum.cit.aet.artemis.domain.User;
import de.tum.cit.aet.artemis.domain.participation.Participation;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.exercise.repository.StudentParticipationRepository;
import de.tum.cit.aet.artemis.exercise.repository.SubmissionRepository;
import de.tum.cit.aet.artemis.exercise.repository.SubmissionVersionRepository;
import de.tum.cit.aet.artemis.programming.repository.BuildLogStatisticsEntryRepository;
import de.tum.cit.aet.artemis.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.service.BuildLogEntryService;
import de.tum.cit.aet.artemis.service.ResultService;
import de.tum.cit.aet.artemis.service.SubmissionService;
import de.tum.cit.aet.artemis.web.rest.dto.SearchResultPageDTO;
import de.tum.cit.aet.artemis.web.rest.dto.SubmissionVersionDTO;
import de.tum.cit.aet.artemis.web.rest.dto.SubmissionWithComplaintDTO;
import de.tum.cit.aet.artemis.web.rest.dto.pageablesearch.SearchTermPageableSearchDTO;
import de.tum.cit.aet.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.cit.aet.artemis.web.rest.util.HeaderUtil;

/**
 * REST controller for managing Submission.
 */
@Profile(PROFILE_CORE)
@RestController
@RequestMapping("api/")
public class SubmissionResource {

    private static final Logger log = LoggerFactory.getLogger(SubmissionResource.class);

    private static final String ENTITY_NAME = "submission";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final SubmissionRepository submissionRepository;

    private final SubmissionService submissionService;

    private final BuildLogEntryService buildLogEntryService;

    private final ResultService resultService;

    private final StudentParticipationRepository studentParticipationRepository;

    private final AuthorizationCheckService authCheckService;

    private final UserRepository userRepository;

    private final ExerciseRepository exerciseRepository;

    private final BuildLogStatisticsEntryRepository buildLogStatisticsEntryRepository;

    private final SubmissionVersionRepository submissionVersionRepository;

    public SubmissionResource(SubmissionService submissionService, SubmissionRepository submissionRepository, BuildLogEntryService buildLogEntryService,
            ResultService resultService, StudentParticipationRepository studentParticipationRepository, AuthorizationCheckService authCheckService, UserRepository userRepository,
            ExerciseRepository exerciseRepository, BuildLogStatisticsEntryRepository buildLogStatisticsEntryRepository, SubmissionVersionRepository submissionVersionRepository) {
        this.submissionService = submissionService;
        this.submissionRepository = submissionRepository;
        this.buildLogEntryService = buildLogEntryService;
        this.resultService = resultService;
        this.exerciseRepository = exerciseRepository;
        this.studentParticipationRepository = studentParticipationRepository;
        this.authCheckService = authCheckService;
        this.userRepository = userRepository;
        this.buildLogStatisticsEntryRepository = buildLogStatisticsEntryRepository;
        this.submissionVersionRepository = submissionVersionRepository;
    }

    /**
     * DELETE /submissions/:submissionId : delete the "id" submission.
     *
     * @param submissionId the id of the submission to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("submissions/{submissionId}")
    @EnforceAtLeastInstructor
    public ResponseEntity<Void> deleteSubmission(@PathVariable Long submissionId) {
        log.debug("REST request to delete Submission : {}", submissionId);

        Optional<Submission> submission = submissionRepository.findWithEagerResultsAndAssessorById(submissionId);

        if (submission.isEmpty()) {
            log.error("Submission with id: {} cannot be deleted", submissionId);
            return ResponseEntity.notFound().build();
        }

        checkAccessPermissionAtInstructor(submission.get());
        List<Result> results = submission.get().getResults();
        for (Result result : results) {
            resultService.deleteResult(result, true);
        }
        // We have to set the results to an empty list because otherwise clearing the build log entries does not work correctly
        submission.get().setResults(Collections.emptyList());
        if (submission.get() instanceof ProgrammingSubmission programmingSubmission) {
            buildLogEntryService.deleteBuildLogEntriesForProgrammingSubmission(programmingSubmission);
        }
        buildLogStatisticsEntryRepository.deleteByProgrammingSubmissionId(submission.get().getId());
        submissionRepository.deleteById(submissionId);

        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, submissionId.toString())).build();
    }

    /**
     * GET /test-run-submissions : get test run submission for an exercise.
     * <p>
     * Only returns the users test run submission for a specific exercise
     *
     * @param exerciseId exerciseID for which all submissions should be returned
     * @return the ResponseEntity with status 200 (OK) and the list of the latest test run submission in body
     */
    @GetMapping("exercises/{exerciseId}/test-run-submissions")
    @EnforceAtLeastEditor
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
        if (!testRunParticipations.isEmpty() && testRunParticipations.getFirst().findLatestSubmission().isPresent()) {
            var latestSubmission = testRunParticipations.getFirst().findLatestSubmission().get();
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
     * <p>
     * Get all submissions associated to an exercise which have complaints in,
     * but filter out the ones that are about the tutor who is doing the request, since tutors cannot act on their own complaint
     * Additionally, filter out the ones where the student is the same as the assessor as this indicated that this is a test run.
     *
     * @param exerciseId of the exercise we are interested in
     * @return the ResponseEntity with status 200 (OK) and a list of SubmissionWithComplaintDTOs. The list can be empty.
     */
    @GetMapping("exercises/{exerciseId}/submissions-with-complaints")
    @EnforceAtLeastTutor
    public ResponseEntity<List<SubmissionWithComplaintDTO>> getSubmissionsWithComplaintsForAssessmentDashboard(@PathVariable Long exerciseId) {
        Exercise exercise = exerciseRepository.findByIdElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.TEACHING_ASSISTANT, exercise, null);
        boolean isAtLeastInstructor = authCheckService.isAtLeastInstructorForExercise(exercise);
        List<SubmissionWithComplaintDTO> submissionWithComplaintDTOs = submissionService.getSubmissionsWithComplaintsForExercise(exerciseId, isAtLeastInstructor);

        return ResponseEntity.ok(submissionWithComplaintDTOs);
    }

    /**
     * Get /exercises/:exerciseId//more-feedback-requests-with-complaints
     * <p>
     * Get all more feedback requests associated to an exercise which have more feedback requests in,
     * but filter out the ones that are about the tutor who is doing the request, since tutors cannot act on their own complaint
     * Additionally, filter out the ones where the student is the same as the assessor as this indicated that this is a test run.
     *
     * @param exerciseId of the exercise we are interested in
     * @return the ResponseEntity with status 200 (OK) and a list of SubmissionWithComplaintDTOs. The list can be empty.
     */
    @GetMapping("exercises/{exerciseId}/more-feedback-requests-with-complaints")
    @EnforceAtLeastTutor
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
    @EnforceAtLeastInstructor
    public ResponseEntity<SearchResultPageDTO<Submission>> getSubmissionsOnPageWithSize(@PathVariable Long exerciseId, SearchTermPageableSearchDTO<String> search) {
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

    /**
     * GET /submissions/{submissionId}/versions : get all submission versions for a given submission
     * {@link SubmissionVersion} are used in exams and store every submission a student has made.
     * <p>
     * A submission version is created every time a student clicks save or every 30s when the current state is saved
     *
     * @param submissionId the id of the submission for which all versions should be returned
     * @return the ResponseEntity with status 200 (OK) and with body a list of {@link SubmissionVersionDTO} for the given submission
     */

    @GetMapping("submissions/{submissionId}/versions")
    @EnforceAtLeastInstructor
    public ResponseEntity<List<SubmissionVersionDTO>> getSubmissionVersions(@PathVariable long submissionId) {
        var submission = submissionRepository.findByIdElseThrow(submissionId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.INSTRUCTOR, submission.getParticipation().getExercise(), userRepository.getUser());
        var submissionVersions = submissionVersionRepository.findSubmissionVersionBySubmissionIdOrderByCreatedDateAsc(submission.getId());
        final var dtos = submissionVersions.stream().map(SubmissionVersionDTO::of).toList();
        return ResponseEntity.ok(dtos);
    }
}
