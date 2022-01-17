package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException.NOT_ALLOWED;
import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.*;

import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.SubmissionType;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.domain.participation.SolutionProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.domain.participation.TemplateProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.ExerciseDateService;
import de.tum.in.www1.artemis.service.connectors.ContinuousIntegrationService;
import de.tum.in.www1.artemis.service.connectors.VersionControlService;
import de.tum.in.www1.artemis.service.feature.Feature;
import de.tum.in.www1.artemis.service.feature.FeatureToggle;
import de.tum.in.www1.artemis.service.programming.ProgrammingExerciseParticipationService;
import de.tum.in.www1.artemis.service.programming.ProgrammingSubmissionService;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * REST controller for managing ProgrammingSubmission.
 */
@RestController
@RequestMapping("/api")
public class ProgrammingSubmissionResource {

    private final Logger log = LoggerFactory.getLogger(ProgrammingSubmissionResource.class);

    private final ProgrammingSubmissionService programmingSubmissionService;

    private final ExerciseRepository exerciseRepository;

    private final ParticipationRepository participationRepository;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final AuthorizationCheckService authCheckService;

    private final ProgrammingExerciseParticipationService programmingExerciseParticipationService;

    private final ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository;

    private final GradingCriterionRepository gradingCriterionRepository;

    private final SubmissionRepository submissionRepository;

    private final Optional<VersionControlService> versionControlService;

    private final Optional<ContinuousIntegrationService> continuousIntegrationService;

    private final UserRepository userRepository;

    private final ExerciseDateService exerciseDateService;

    public ProgrammingSubmissionResource(ProgrammingSubmissionService programmingSubmissionService, ExerciseRepository exerciseRepository,
            ParticipationRepository participationRepository, AuthorizationCheckService authCheckService, ProgrammingExerciseRepository programmingExerciseRepository,
            ProgrammingExerciseParticipationService programmingExerciseParticipationService,
            ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository, Optional<VersionControlService> versionControlService,
            UserRepository userRepository, Optional<ContinuousIntegrationService> continuousIntegrationService, GradingCriterionRepository gradingCriterionRepository,
            SubmissionRepository submissionRepository, ExerciseDateService exerciseDateService) {
        this.programmingSubmissionService = programmingSubmissionService;
        this.exerciseRepository = exerciseRepository;
        this.participationRepository = participationRepository;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.authCheckService = authCheckService;
        this.programmingExerciseParticipationService = programmingExerciseParticipationService;
        this.programmingExerciseStudentParticipationRepository = programmingExerciseStudentParticipationRepository;
        this.versionControlService = versionControlService;
        this.userRepository = userRepository;
        this.continuousIntegrationService = continuousIntegrationService;
        this.gradingCriterionRepository = gradingCriterionRepository;
        this.submissionRepository = submissionRepository;
        this.exerciseDateService = exerciseDateService;
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
            if (!ex.getMessage().contains("empty setup commit")) {
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
    @PreAuthorize("hasRole('USER')")
    @FeatureToggle(Feature.PROGRAMMING_EXERCISES)
    public ResponseEntity<Void> triggerBuild(@PathVariable Long participationId, @RequestParam(defaultValue = "MANUAL") SubmissionType submissionType) {
        Participation participation = participationRepository.findByIdElseThrow(participationId);
        // this call supports TemplateProgrammingExerciseParticipation, SolutionProgrammingExerciseParticipation and ProgrammingExerciseStudentParticipation
        if (!(participation instanceof ProgrammingExerciseParticipation programmingExerciseParticipation)) {
            return notFound();
        }

        if (!programmingExerciseParticipationService.canAccessParticipation(programmingExerciseParticipation)) {
            return forbidden();
        }

        // The editor is allowed to trigger an instructor build for template and solution participations,
        // but not for student participations. The instructor however, might trigger student participations.
        if (submissionType == SubmissionType.INSTRUCTOR && !authCheckService.isAtLeastInstructorForExercise(participation.getExercise())
                && !(authCheckService.isAtLeastEditorForExercise(participation.getExercise())
                        && (participation instanceof TemplateProgrammingExerciseParticipation || participation instanceof SolutionProgrammingExerciseParticipation))) {
            return forbidden();
        }

        try {
            var submission = programmingSubmissionService.getOrCreateSubmissionWithLastCommitHashForParticipation(programmingExerciseParticipation, submissionType);
            programmingSubmissionService.triggerBuildAndNotifyUser(submission);
        }
        catch (IllegalStateException ex) {
            return notFound();
        }

        return ResponseEntity.ok().build();
    }

    /**
     * Trigger the CI build for the latest submission of a given participation, if it did not receive a result.
     *
     * @param participationId to which the submission belongs.
     * @param lastGraded if true, will not use the most recent submission, but the most recent GRADED submission. This submission could e.g. be created before the deadline or after the deadline by the INSTRUCTOR.
     * @return 404 if there is no participation for the given id, 403 if the user mustn't access the participation, 200 if the build was triggered, a result already exists or the build is running.
     */
    // TODO: we should definitely change this URL, it does not make sense to use /programming-submissions/{participationId}
    @PostMapping(Constants.PROGRAMMING_SUBMISSION_RESOURCE_PATH + "{participationId}/trigger-failed-build")
    @PreAuthorize("hasRole('USER')")
    @FeatureToggle(Feature.PROGRAMMING_EXERCISES)
    public ResponseEntity<Void> triggerFailedBuild(@PathVariable Long participationId, @RequestParam(defaultValue = "false") boolean lastGraded) {
        Participation participation = participationRepository.findByIdElseThrow(participationId);
        if (!(participation instanceof ProgrammingExerciseParticipation programmingExerciseParticipation)) {
            return notFound();
        }
        if (!programmingExerciseParticipationService.canAccessParticipation(programmingExerciseParticipation)) {
            return forbidden();
        }
        Optional<ProgrammingSubmission> submission = programmingSubmissionService.getLatestPendingSubmission(participationId, lastGraded);
        if (submission.isEmpty()) {
            return badRequest();
        }

        // if the build plan was not cleaned yet, we can try to access the current build state, as the build might still be running (because it was slow or queued)
        if (programmingExerciseParticipation.getBuildPlanId() != null) {
            // If a build is already queued/running for the given participation, we just return. Note: We don't check that the running build belongs to the failed submission.
            ContinuousIntegrationService.BuildStatus buildStatus = continuousIntegrationService.get().getBuildStatus(programmingExerciseParticipation);
            if (buildStatus == ContinuousIntegrationService.BuildStatus.BUILDING || buildStatus == ContinuousIntegrationService.BuildStatus.QUEUED) {
                // We inform the user through the websocket that the submission is still in progress (build is running/queued, result should arrive soon).
                // This resets the pending submission timer in the client.
                programmingSubmissionService.notifyUserAboutSubmission(submission.get());
                return ResponseEntity.ok().build();
            }
        }
        if (lastGraded && submission.get().getType() != SubmissionType.INSTRUCTOR && submission.get().getType() != SubmissionType.TEST
                && exerciseDateService.isAfterDueDate(participation)) {
            // If the submission is not the latest but the last graded, there is no point in triggering the build again as this would build the most recent VCS commit.
            // This applies only to students submissions after the exercise due date.
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
    @PreAuthorize("hasRole('INSTRUCTOR')")
    @FeatureToggle(Feature.PROGRAMMING_EXERCISES)
    public ResponseEntity<Void> triggerInstructorBuildForExercise(@PathVariable Long exerciseId) {
        try {
            Exercise exercise = exerciseRepository.findByIdElseThrow(exerciseId);
            Course course = exercise.getCourseViaExerciseGroupOrCourseMember();
            User user = userRepository.getUserWithGroupsAndAuthorities();

            if (!authCheckService.isAtLeastInstructorForExercise(exercise, user)) {
                return forbidden();
            }

            programmingSubmissionService.logTriggerInstructorBuild(user, exercise, course);
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
    @PreAuthorize("hasRole('INSTRUCTOR')")
    @FeatureToggle(Feature.PROGRAMMING_EXERCISES)
    public ResponseEntity<Void> triggerInstructorBuildForExercise(@PathVariable Long exerciseId, @RequestBody Set<Long> participationIds) {
        if (participationIds.isEmpty()) {
            return badRequest();
        }
        ProgrammingExercise programmingExercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(exerciseId);
        if (programmingExercise == null) {
            return notFound();
        }
        if (!authCheckService.isAtLeastInstructorForExercise(programmingExercise)) {
            return forbidden();
        }

        log.info("Trigger (failed) instructor build for participations {} in exercise {} with id {}", participationIds, programmingExercise.getTitle(),
                programmingExercise.getId());
        var participations = programmingExerciseStudentParticipationRepository.findWithSubmissionsByExerciseIdAndParticipationIds(exerciseId, participationIds);
        programmingSubmissionService.triggerBuildForParticipations(participations);

        return ResponseEntity.ok().build();
    }

    /**
     * POST /programming-exercises/test-cases-changed/:exerciseId : informs Artemis about changed test cases for the "exerciseId" programmingExercise.
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

        String lastCommitHash = null;
        try {
            Commit commit = versionControlService.get().getLastCommitDetails(requestBody);
            lastCommitHash = commit.getCommitHash();
            log.info("create new programmingSubmission with commitHash: {} for exercise {}", lastCommitHash, exerciseId);
        }
        catch (Exception ex) {
            log.debug("Commit hash could not be parsed for from test repository from exercise " + exerciseId
                    + ", the submission will be created with the latest commitHash of the solution repository.", ex);
        }

        // When the tests were changed, the solution repository will be built. We therefore create a submission for the solution participation.
        ProgrammingSubmission submission = programmingSubmissionService.createSolutionParticipationSubmissionWithTypeTest(exerciseId, lastCommitHash);
        programmingSubmissionService.notifyUserAboutSubmission(submission);
        // It is possible that there is now a new test case or an old one has been removed. We use this flag to inform the instructor about outdated student results.
        programmingSubmissionService.setTestCasesChanged(exerciseId, true);

        return ResponseEntity.ok().build();
    }

    /**
     * GET /programming-submissions : get all the programming submissions for an exercise. It is possible to filter, to receive only the one that have been already submitted, or only the one
     * assessed by the tutor who is doing the call.
     * In case of exam exercise, it filters out all test run submissions.
     *
     * @param exerciseId the id of the exercise.
     * @param correctionRound the correctionRound for which all submissions should be fetched
     * @param submittedOnly if only submitted submissions should be returned.
     * @param assessedByTutor if the submission was assessed by calling tutor.
     * @return the ResponseEntity with status 200 (OK) and the list of Programming Submissions in body.
     */
    @GetMapping("/exercises/{exerciseId}/programming-submissions")
    @PreAuthorize("hasRole('TA')")
    public ResponseEntity<List<ProgrammingSubmission>> getAllProgrammingSubmissions(@PathVariable Long exerciseId, @RequestParam(defaultValue = "false") boolean submittedOnly,
            @RequestParam(defaultValue = "false") boolean assessedByTutor, @RequestParam(value = "correction-round", defaultValue = "0") int correctionRound) {
        log.debug("REST request to get all programming submissions");
        Exercise exercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(exerciseId);

        if (!authCheckService.isAtLeastTeachingAssistantForExercise(exercise)) {
            throw new AccessForbiddenException(NOT_ALLOWED);
        }

        final boolean examMode = exercise.isExamExercise();
        List<ProgrammingSubmission> programmingSubmissions;
        if (assessedByTutor) {
            User user = userRepository.getUserWithGroupsAndAuthorities();
            programmingSubmissions = programmingSubmissionService.getAllProgrammingSubmissionsAssessedByTutorForCorrectionRoundAndExercise(exerciseId, user, examMode,
                    correctionRound);
        }
        else {
            programmingSubmissions = programmingSubmissionService.getProgrammingSubmissions(exerciseId, submittedOnly, examMode);
        }

        if (!examMode) {
            programmingSubmissions.forEach(Submission::removeNullResults);
        }
        return ResponseEntity.ok().body(programmingSubmissions);
    }

    /**
     * GET /programming-submissions/:submissionId/lock : get the programmingSubmissions participation by it's id and locks the corresponding submission for assessment
     *
     * @param submissionId the id of the participation to retrieve
     * @param correctionRound correction round for which we prepare the submission
     * @return the ResponseEntity with status 200 (OK) and with body the programmingSubmissions participation
     */
    @GetMapping("/programming-submissions/{submissionId}/lock")
    @PreAuthorize("hasRole('TA')")
    public ResponseEntity<ProgrammingSubmission> lockAndGetProgrammingSubmission(@PathVariable Long submissionId,
            @RequestParam(value = "correction-round", defaultValue = "0") int correctionRound) {
        log.debug("REST request to get ProgrammingSubmission with id: {}", submissionId);
        var programmingSubmission = (ProgrammingSubmission) submissionRepository.findOneWithEagerResultAndFeedback(submissionId);
        final var participation = programmingSubmission.getParticipation();
        final var programmingExercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(participation.getExercise().getId());
        final var numberOfEnabledCorrectionRounds = programmingExercise.getNumberOfCorrectionRounds();
        var gradingCriteria = gradingCriterionRepository.findByExerciseIdWithEagerGradingCriteria(programmingExercise.getId());
        programmingExercise.setGradingCriteria(gradingCriteria);

        final User user = userRepository.getUserWithGroupsAndAuthorities();
        if (!authCheckService.isAtLeastTeachingAssistantForExercise(programmingExercise, user)) {
            return forbidden();
        }

        if (!programmingExercise.areManualResultsAllowed()) {
            return forbidden("assessment", "assessmentSaveNotAllowed", "Creating manual results is disabled for this exercise!");
        }

        long numberOfManualResults = programmingSubmission.getResults().stream().filter(Result::isManual).count();

        // this makes sure that new results are only created if it is really necessary.
        // At max 1 for course exercises and at max 2 results in exams with 2 correction rounds enabled.
        // The second (or third) result for complaint responses is not created here.
        if (numberOfManualResults < correctionRound + 1 && numberOfManualResults < numberOfEnabledCorrectionRounds) {
            // Check lock limit
            programmingSubmissionService.checkSubmissionLockLimit(programmingExercise.getCourseViaExerciseGroupOrCourseMember().getId());

            // As no manual result is present we need to lock the submission for assessment
            Result latestAutomaticResult = programmingSubmission.getLatestResult();
            if (latestAutomaticResult == null) {
                // if the participation does not have a result we want to create a new result for the submission of the participation.
                // If there isn't a submission either, we should not create any result.
                programmingSubmission = programmingSubmissionService.getLatestPendingSubmission(participation.getId(), false).orElseThrow();
            }
            programmingSubmission = programmingSubmissionService.lockAndGetProgrammingSubmission(programmingSubmission.getId(), correctionRound);
        }

        participation.setExercise(programmingExercise);
        // prepare programming submission for response
        programmingSubmissionService.hideDetails(programmingSubmission, user);
        // remove automatic results before sending to client
        programmingSubmission.setResults(programmingSubmission.getManualResults());
        programmingSubmission.getParticipation().setResults(new HashSet<>(programmingSubmission.getResults()));
        return ResponseEntity.ok(programmingSubmission);
    }

    /**
     * GET /programming-submission-without-assessment : get one Programming Submission without assessment.
     *
     * @param exerciseId the id of the exercise
     * @param lockSubmission optional value to define if the submission should be locked and has the value of false if not set manually
     * @param correctionRound the correction round for which we want to find the submission
     * @return the ResponseEntity with status 200 (OK) and the list of Programming Submissions in body
     */
    @GetMapping(value = "/exercises/{exerciseId}/programming-submission-without-assessment")
    @PreAuthorize("hasRole('TA')")
    public ResponseEntity<ProgrammingSubmission> getProgrammingSubmissionWithoutAssessment(@PathVariable Long exerciseId,
            @RequestParam(value = "lock", defaultValue = "false") boolean lockSubmission, @RequestParam(value = "correction-round", defaultValue = "0") int correctionRound) {
        log.debug("REST request to get a programming submission without assessment");
        final ProgrammingExercise programmingExercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(exerciseId);
        List<GradingCriterion> gradingCriteria = gradingCriterionRepository.findByExerciseIdWithEagerGradingCriteria(exerciseId);
        programmingExercise.setGradingCriteria(gradingCriteria);
        final User user = userRepository.getUserWithGroupsAndAuthorities();
        if (!authCheckService.isAtLeastTeachingAssistantForExercise(programmingExercise, user)) {
            return forbidden();
        }

        // Check if tutors can start assessing the students submission
        this.programmingSubmissionService.checkIfExerciseDueDateIsReached(programmingExercise);

        // Check if the limit of simultaneously locked submissions has been reached
        programmingSubmissionService.checkSubmissionLockLimit(programmingExercise.getCourseViaExerciseGroupOrCourseMember().getId());

        // TODO Check if submission has newly created manual result for this and endpoint and endpoint above
        final ProgrammingSubmission programmingSubmission;
        if (lockSubmission) {
            programmingSubmission = programmingSubmissionService.lockAndGetProgrammingSubmissionWithoutResult(programmingExercise, correctionRound);
        }
        else {
            Optional<ProgrammingSubmission> optionalProgrammingSubmission = programmingSubmissionService.getRandomProgrammingSubmissionEligibleForNewAssessment(programmingExercise,
                    programmingExercise.isExamExercise(), correctionRound);
            if (optionalProgrammingSubmission.isEmpty()) {
                return notFound();
            }
            programmingSubmission = optionalProgrammingSubmission.get();

        }

        programmingSubmission.getParticipation().setExercise(programmingExercise);
        programmingSubmissionService.hideDetails(programmingSubmission, user);
        // remove automatic results before sending to client
        programmingSubmission.setResults(programmingSubmission.getManualResults());
        return ResponseEntity.ok(programmingSubmission);
    }
}
