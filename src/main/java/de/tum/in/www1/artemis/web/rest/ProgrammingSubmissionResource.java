package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.config.Constants.EXTERNAL_SYSTEM_REQUEST_BATCH_SIZE;
import static de.tum.in.www1.artemis.config.Constants.EXTERNAL_SYSTEM_REQUEST_BATCH_WAIT_TIME_MS;
import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.*;

import java.time.ZonedDateTime;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.eclipse.jgit.lib.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.SubmissionType;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.*;
import de.tum.in.www1.artemis.service.connectors.ContinuousIntegrationService;
import de.tum.in.www1.artemis.service.connectors.VersionControlService;
import de.tum.in.www1.artemis.service.feature.Feature;
import de.tum.in.www1.artemis.service.feature.FeatureToggle;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * REST controller for managing ProgrammingSubmission.
 */
@RestController
@RequestMapping("/api")
public class ProgrammingSubmissionResource {

    private final Logger log = LoggerFactory.getLogger(ProgrammingSubmissionResource.class);

    private static final String ENTITY_NAME = "programmingSubmission";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final ProgrammingSubmissionService programmingSubmissionService;

    private final ExerciseService exerciseService;

    private final ProgrammingExerciseService programmingExerciseService;

    private final SimpMessageSendingOperations messagingTemplate;

    private final AuthorizationCheckService authCheckService;

    private final ProgrammingExerciseParticipationService programmingExerciseParticipationService;

    private final ResultService resultService;

    private final Optional<VersionControlService> versionControlService;

    private final Optional<ContinuousIntegrationService> continuousIntegrationService;

    private final UserService userService;

    public ProgrammingSubmissionResource(ProgrammingSubmissionService programmingSubmissionService, ExerciseService exerciseService,
            ProgrammingExerciseService programmingExerciseService, SimpMessageSendingOperations messagingTemplate, AuthorizationCheckService authCheckService,
            ProgrammingExerciseParticipationService programmingExerciseParticipationService, ResultService resultService, Optional<VersionControlService> versionControlService,
            UserService userService, Optional<ContinuousIntegrationService> continuousIntegrationService) {
        this.programmingSubmissionService = programmingSubmissionService;
        this.exerciseService = exerciseService;
        this.programmingExerciseService = programmingExerciseService;
        this.messagingTemplate = messagingTemplate;
        this.authCheckService = authCheckService;
        this.programmingExerciseParticipationService = programmingExerciseParticipationService;
        this.resultService = resultService;
        this.versionControlService = versionControlService;
        this.userService = userService;
        this.continuousIntegrationService = continuousIntegrationService;
    }

    /**
     * POST /programming-submissions/:participationId : Notify the application about a new push to the VCS for the participation with Id participationId This API is invoked by the
     * VCS Server at the push of a new commit
     *
     * @param participationId the participationId of the participation the repository is linked to
     * @param requestBody the body of the post request by the VCS.
     * @return the ResponseEntity with status 200 (OK), or with status 400 (Bad Request) if the latest commit was already notified about
     */
    @PostMapping(value = Constants.PROGRAMMING_SUBMISSION_RESOURCE_PATH + "{participationId}")
    public ResponseEntity<?> notifyPush(@PathVariable("participationId") Long participationId, @RequestBody Object requestBody) {
        log.debug("REST request to inform about new commit+push for participation: {}", participationId);

        try {
            // The 'user' is not properly logged into Artemis, this leads to an issue when accessing custom repository methods.
            // Therefore a mock auth object has to be created.
            SecurityUtils.setAuthorizationObject();
            ProgrammingSubmission submission = programmingSubmissionService.notifyPush(participationId, requestBody);
            // Remove unnecessary information from the new submission.
            submission.getParticipation().setExercise(null);
            submission.getParticipation().setSubmissions(null);

            programmingSubmissionService.notifyUserAboutSubmission(submission);
        }
        catch (IllegalArgumentException ex) {
            log.error(
                    "Exception encountered when trying to extract the commit hash from the request body: processing submission for participation {} failed with request object {}: {}",
                    participationId, requestBody, ex);
            return badRequest();
        }
        catch (IllegalStateException ex) {
            if (ex.getMessage().contains("empty setup commit")) {
                // ignore
            }
            else {
                log.warn("Processing submission for participation {} failed: {}", participationId, ex.getMessage());
            }
            // we return ok, because the problem is not on the side of the VCS Server and we don't want the VCS Server to kill the webhook if there are too many errors
            return ResponseEntity.status(HttpStatus.OK).build();
        }
        catch (EntityNotFoundException ex) {
            log.error("Participation with id {} is not a ProgrammingExerciseParticipation: processing submission for participation {} failed with request object {}: {}",
                    participationId, participationId, requestBody, ex);
            return notFound();
        }

        // TODO: we should not really return status code other than 200, because Bitbucket might kill the webhook, if there are too many errors
        return ResponseEntity.status(HttpStatus.OK).build();
    }

    /**
     * Trigger the CI build of the given participation.
     * The build result will be treated as if the user would have done a commit.
     *
     * @param participationId of the participation.
     * @param submissionType  will be used for the newly created submission.
     * @return ok if the participation could be found and has permissions, otherwise forbidden (403) or notFound (404). Will also return notFound if the user's git repository is not available.
     * The REST path would be: "/programming-submissions/{participationId}/trigger-build"
     */
    @PostMapping(Constants.PROGRAMMING_SUBMISSION_RESOURCE_PATH + "{participationId}/trigger-build")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    @FeatureToggle(Feature.PROGRAMMING_EXERCISES)
    public ResponseEntity<Void> triggerBuild(@PathVariable Long participationId, @RequestParam(defaultValue = "MANUAL") SubmissionType submissionType) {
        Participation participation = programmingExerciseParticipationService.findParticipation(participationId);
        if (!(participation instanceof ProgrammingExerciseParticipation)) {
            return notFound();
        }
        ProgrammingExerciseParticipation programmingExerciseParticipation = (ProgrammingExerciseParticipation) participation;
        if (!programmingExerciseParticipationService.canAccessParticipation(programmingExerciseParticipation)
                || (submissionType.equals(SubmissionType.INSTRUCTOR) && !authCheckService.isAtLeastInstructorForExercise(participation.getExercise()))) {
            return forbidden();
        }
        ProgrammingSubmission submission;
        try {
            submission = programmingSubmissionService.createSubmissionWithLastCommitHashForParticipation(programmingExerciseParticipation, submissionType);
        }
        catch (IllegalStateException ex) {
            return notFound();
        }

        programmingSubmissionService.triggerBuildAndNotifyUser(submission);

        return ResponseEntity.ok().build();
    }

    /**
     * Trigger the CI build for the latest submission of a given participation, if it did not receive a result.
     *
     * @param participationId to which the submission belongs.
     * @param lastGraded if true, will not use the most recent submission, but the most recent GRADED submission. This submission could e.g. be created before the deadline or after the deadline by the INSTRUCTOR.
     * @return 404 if there is no participation for the given id, 403 if the user mustn't access the participation, 200 if the build was triggered, a result already exists or the build is running.
     */
    @PostMapping(Constants.PROGRAMMING_SUBMISSION_RESOURCE_PATH + "{participationId}/trigger-failed-build")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    @FeatureToggle(Feature.PROGRAMMING_EXERCISES)
    public ResponseEntity<Void> triggerFailedBuild(@PathVariable Long participationId, @RequestParam(defaultValue = "false") boolean lastGraded) {
        Participation participation = programmingExerciseParticipationService.findParticipation(participationId);
        if (!(participation instanceof ProgrammingExerciseParticipation)) {
            return notFound();
        }
        ProgrammingExerciseParticipation programmingExerciseParticipation = (ProgrammingExerciseParticipation) participation;
        if (!programmingExerciseParticipationService.canAccessParticipation(programmingExerciseParticipation)) {
            return forbidden();
        }
        Optional<ProgrammingSubmission> submission = programmingSubmissionService.getLatestPendingSubmission(participationId, lastGraded);
        if (submission.isEmpty()) {
            return badRequest();
        }

        // If there is a result on the CIS for the submission, there must have been a communication issue between the CIS and Artemis. In this case we can just save the result.
        // TODO: If the submission is not the latest but the latest graded submission, this does not work if there have been commits since. To make it work we would have to find
        // the correct build for the given commit hash.
        Optional<Result> result = continuousIntegrationService.get().retrieveLatestBuildResult(programmingExerciseParticipation, submission.get());
        if (result.isPresent()) {
            resultService.notifyUserAboutNewResult(result.get(), participationId);
            return ResponseEntity.ok().build();
        }
        // If a build is already queued/running for the given participation, we just return. Note: We don't check that the running build belongs to the failed submission.
        ContinuousIntegrationService.BuildStatus buildStatus = continuousIntegrationService.get().getBuildStatus(programmingExerciseParticipation);
        if (buildStatus == ContinuousIntegrationService.BuildStatus.BUILDING || buildStatus == ContinuousIntegrationService.BuildStatus.QUEUED) {
            // We inform the user through the websocket that the submission is still in progress (build is running/queued, result should arrive soon).
            // This resets the pending submission timer in the client.
            programmingSubmissionService.notifyUserAboutSubmission(submission.get());
            return ResponseEntity.ok().build();
        }
        if (lastGraded) {
            // If the submission is not the latest but the last graded, there is no point in triggering the build again as this would build the most recent VCS commit (=different
            // commit hash than submission).
            return notFound();
        }
        // If there is no result on the CIS, we trigger a new build and hope it will arrive in Artemis this time.
        programmingSubmissionService.triggerBuildAndNotifyUser(submission.get());
        return ResponseEntity.ok().build();
    }

    /**
     * Trigger the CI of all participations of the given exercise.
     * The build result will become rated regardless of the due date as the submission type is INSTRUCTOR.
     *
     * @param exerciseId to identify the programming exercise.
     * @return ok if the operation was successful, notFound (404) if the programming exercise does not exist, forbidden (403) if the user is not allowed to access the exercise.
     */
    @PostMapping("/programming-exercises/{exerciseId}/trigger-instructor-build-all")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    @FeatureToggle(Feature.PROGRAMMING_EXERCISES)
    public ResponseEntity<Void> triggerInstructorBuildForExercise(@PathVariable Long exerciseId) {
        try {
            programmingSubmissionService.triggerInstructorBuildForExercise(exerciseId);
            return ResponseEntity.ok().build();
        }
        catch (EntityNotFoundException ex) {
            return notFound();
        }
    }

    /**
     * Trigger the CI of the provided participations of the given exercise.
     * The build result will become rated regardless of the due date as the submission type is INSTRUCTOR.
     *
     * Note: If a participationId does not belong to the exercise, it will be ignored!
     *
     * @param exerciseId to identify the programming exercise.
     * @param participationIds list of participation ids.
     * @return ok if the operation was successful, notFound (404) if the programming exercise does not exist, forbidden (403) if the user is not allowed to access the exercise.
     */
    @PostMapping("/programming-exercises/{exerciseId}/trigger-instructor-build")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    @FeatureToggle(Feature.PROGRAMMING_EXERCISES)
    public ResponseEntity<Void> triggerInstructorBuildForExercise(@PathVariable Long exerciseId, @RequestBody Set<Long> participationIds) {
        if (participationIds.isEmpty()) {
            return badRequest();
        }
        ProgrammingExercise programmingExercise = programmingExerciseService.findWithTemplateParticipationAndSolutionParticipationById(exerciseId);
        if (programmingExercise == null) {
            return notFound();
        }
        if (!authCheckService.isAtLeastInstructorForExercise(programmingExercise)) {
            return forbidden();
        }

        log.info("Trigger (failed) instructor build for participations {} in exercise {} with id {}", participationIds, programmingExercise.getTitle(),
                programmingExercise.getId());
        List<ProgrammingExerciseParticipation> participations = new LinkedList<>(
                programmingExerciseParticipationService.findByExerciseAndParticipationIds(exerciseId, participationIds));

        var index = 0;
        for (var participation : participations) {
            // Execute requests in batches instead all at once.
            if (index > 0 && index % EXTERNAL_SYSTEM_REQUEST_BATCH_SIZE == 0) {
                try {
                    log.info("Sleep for {}s during triggerBuild", EXTERNAL_SYSTEM_REQUEST_BATCH_WAIT_TIME_MS / 1000);
                    Thread.sleep(EXTERNAL_SYSTEM_REQUEST_BATCH_WAIT_TIME_MS);
                }
                catch (InterruptedException ex) {
                    log.error("Exception encountered when pausing before executing successive build for participation " + participation.getId(), ex);
                }
            }
            programmingSubmissionService.triggerBuildAndNotifyUser(participation);
            index++;
        }

        return ResponseEntity.ok().build();
    }

    /**
     * POST /programming-exercises/test-cases-changed/:exerciseId : informs Artemis about changed test cases for the "id" programmingExercise.
     *
     * Problem with legacy programming exercises:
     * The repositories (solution, template, student) are built automatically when a commit is pushed into the test repository.
     * We have removed this trigger for newly created exercises, but can't remove it from legacy ones.
     * This means that legacy exercises will trigger the repositories to be built, but we won't create submissions here anymore.
     * Therefore incoming build results will have to create new submissions with SubmissionType.OTHER.
     * 
     * @param exerciseId the id of the programmingExercise where the test cases got changed
     * @param requestBody the body of the post request by the VCS.
     * @return the ResponseEntity with status 200 (OK)
     */
    @PostMapping(Constants.TEST_CASE_CHANGED_PATH + "{exerciseId}")
    public ResponseEntity<Void> testCaseChanged(@PathVariable Long exerciseId, @RequestBody Object requestBody) {
        log.info("REST request to inform about changed test cases of ProgrammingExercise : {}", exerciseId);
        // This is needed as a request using a custom query is made using the ExerciseRepository, but the user is not authenticated
        // as the VCS-server performs the request
        SecurityUtils.setAuthorizationObject();

        ObjectId lastCommitId = null;
        try {
            Commit commit = versionControlService.get().getLastCommitDetails(requestBody);
            String lastCommitHash = commit.getCommitHash();
            lastCommitId = ObjectId.fromString(lastCommitHash);
            log.info("create new programmingSubmission with commitHash: " + lastCommitHash + " for exercise " + exerciseId);
        }
        catch (Exception ex) {
            log.debug("Commit hash could not be parsed for from test repository from exercise " + exerciseId
                    + ", the submission will be created with the latest commitHash of the solution repository.", ex);
        }

        // When the tests were changed, the solution repository will be built. We therefore create a submission for the solution participation.
        ProgrammingSubmission submission = programmingSubmissionService.createSolutionParticipationSubmissionWithTypeTest(exerciseId, lastCommitId);
        programmingSubmissionService.notifyUserAboutSubmission(submission);
        // It is possible that there is now a new test case or an old one has been removed. We use this flag to inform the instructor about outdated student results.
        programmingSubmissionService.setTestCasesChanged(exerciseId, true);

        return ResponseEntity.ok().build();
    }

    /**
     * GET /programming-submissions : get all the programming submissions for an exercise. It is possible to filter, to receive only the one that have been already submitted, or only the one
     * assessed by the tutor who is doing the call.
     *
     * @param exerciseId the id of the exercise.
     * @param submittedOnly if only submitted submissions should be returned.
     * @param assessedByTutor if the submission was assessed by calling tutor.
     * @return the ResponseEntity with status 200 (OK) and the list of Programming Submissions in body.
     */
    @GetMapping("/exercises/{exerciseId}/programming-submissions")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<List<ProgrammingSubmission>> getAllProgrammingSubmissions(@PathVariable Long exerciseId, @RequestParam(defaultValue = "false") boolean submittedOnly,
            @RequestParam(defaultValue = "false") boolean assessedByTutor) {
        log.debug("REST request to get all programming submissions");
        Exercise exercise = exerciseService.findOneWithAdditionalElements(exerciseId);

        if (!authCheckService.isAtLeastTeachingAssistantForExercise(exercise)) {
            throw new AccessForbiddenException("You are not allowed to access this resource");
        }

        List<ProgrammingSubmission> programmingSubmissions;
        if (assessedByTutor) {
            User user = userService.getUserWithGroupsAndAuthorities();
            programmingSubmissions = programmingSubmissionService.getAllProgrammingSubmissionsByTutorForExercise(exerciseId, user.getId());
        }
        else {
            programmingSubmissions = programmingSubmissionService.getProgrammingSubmissions(exerciseId, submittedOnly);
        }

        return ResponseEntity.ok().body(programmingSubmissions);
    }

    /**
     * GET /programming-submission-without-assessment : get one Programming Submission without assessment.
     *
     * @param exerciseId the id of the exercise
     * @return the ResponseEntity with status 200 (OK) and the list of Programming Submissions in body
     */
    @GetMapping(value = "/exercises/{exerciseId}/programming-submission-without-assessment")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<ProgrammingSubmission> getProgrammingSubmissionWithoutAssessment(@PathVariable Long exerciseId) {
        log.debug("REST request to get a programming submission without assessment");
        final ProgrammingExercise programmingExercise = programmingExerciseService.findWithTemplateParticipationAndSolutionParticipationById(exerciseId);
        final User user = userService.getUserWithGroupsAndAuthorities();
        if (!authCheckService.isAtLeastTeachingAssistantForExercise(programmingExercise, user)) {
            return forbidden();
        }

        // Tutors cannot start assessing submissions if the exercise due date hasn't been reached yet
        if (programmingExercise.getBuildAndTestStudentSubmissionsAfterDueDate() != null
                && programmingExercise.getBuildAndTestStudentSubmissionsAfterDueDate().isAfter(ZonedDateTime.now())) {
            return notFound();
        }

        // TODO: Handle lock limit.

        final Optional<ProgrammingSubmission> programmingSubmissionOpt = programmingSubmissionService.getRandomProgrammingSubmissionWithoutManualResult(programmingExercise);
        if (programmingSubmissionOpt.isEmpty()) {
            return notFound();
        }
        final ProgrammingSubmission programmingSubmission = programmingSubmissionOpt.get();

        // Make sure the exercise is connected to the participation in the json response
        StudentParticipation studentParticipation = (StudentParticipation) programmingSubmission.getParticipation();
        studentParticipation.setExercise(programmingExercise);
        programmingSubmissionService.hideDetails(programmingSubmission, user);
        return ResponseEntity.ok(programmingSubmission);
    }
}
