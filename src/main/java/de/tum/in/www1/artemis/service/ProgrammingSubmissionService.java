package de.tum.in.www1.artemis.service;

import static de.tum.in.www1.artemis.config.Constants.*;

import java.net.URL;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.apache.http.HttpException;
import org.eclipse.jgit.lib.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.PathVariable;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.InitializationState;
import de.tum.in.www1.artemis.domain.enumeration.SubmissionType;
import de.tum.in.www1.artemis.domain.participation.*;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseStudentParticipationRepository;
import de.tum.in.www1.artemis.repository.ProgrammingSubmissionRepository;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.repository.StudentParticipationRepository;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.connectors.ContinuousIntegrationService;
import de.tum.in.www1.artemis.service.connectors.GitService;
import de.tum.in.www1.artemis.service.connectors.VersionControlService;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;
import de.tum.in.www1.artemis.web.websocket.programmingSubmission.BuildTriggerWebsocketError;

@Service
public class ProgrammingSubmissionService {

    private final Logger log = LoggerFactory.getLogger(ProgrammingSubmissionService.class);

    @Value("${artemis.git.name}")
    private String ARTEMIS_GIT_NAME;

    @Value("${artemis.git.email}")
    private String ARTEMIS_GIT_EMAIL;

    private final ProgrammingExerciseService programmingExerciseService;

    private final ProgrammingSubmissionRepository programmingSubmissionRepository;

    private final ParticipationService participationService;

    private final ProgrammingExerciseParticipationService programmingExerciseParticipationService;

    private final GroupNotificationService groupNotificationService;

    private final WebsocketMessagingService websocketMessagingService;

    private final Optional<VersionControlService> versionControlService;

    private final Optional<ContinuousIntegrationService> continuousIntegrationService;

    private final GitService gitService;

    private final SimpMessageSendingOperations messagingTemplate;

    private final StudentParticipationRepository studentParticipationRepository;

    private final SubmissionService submissionService;

    public ProgrammingSubmissionService(ProgrammingSubmissionRepository programmingSubmissionRepository, ProgrammingExerciseService programmingExerciseService,
            ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository, GroupNotificationService groupNotificationService,
            WebsocketMessagingService websocketMessagingService, Optional<VersionControlService> versionControlService,
            Optional<ContinuousIntegrationService> continuousIntegrationService, ParticipationService participationService, SimpMessageSendingOperations messagingTemplate,
            ProgrammingExerciseParticipationService programmingExerciseParticipationService, GitService gitService, ResultRepository resultRepository,
            StudentParticipationRepository studentParticipationRepository, SubmissionService submissionService) {
        this.programmingSubmissionRepository = programmingSubmissionRepository;
        this.programmingExerciseService = programmingExerciseService;
        this.groupNotificationService = groupNotificationService;
        this.websocketMessagingService = websocketMessagingService;
        this.versionControlService = versionControlService;
        this.continuousIntegrationService = continuousIntegrationService;
        this.participationService = participationService;
        this.messagingTemplate = messagingTemplate;
        this.programmingExerciseParticipationService = programmingExerciseParticipationService;
        this.gitService = gitService;
        this.studentParticipationRepository = studentParticipationRepository;
        this.submissionService = submissionService;
    }

    /**
     * This method gets called if a new commit was pushed to the VCS
     *
     * @param participationId The ID to the Participation, where the push happend
     * @param requestBody the body of the post request by the VCS.
     * @return the ProgrammingSubmission for the last commitHash
     * @throws EntityNotFoundException if no ProgrammingExerciseParticipation could be found
     * @throws IllegalStateException if a ProgrammingSubmission already exists
     * @throws IllegalArgumentException it the Commit hash could not be parsed for submission from participation
     */
    public ProgrammingSubmission notifyPush(Long participationId, Object requestBody) throws EntityNotFoundException, IllegalStateException, IllegalArgumentException {
        Participation participation = participationService.findOneWithEagerSubmissions(participationId);
        if (!(participation instanceof ProgrammingExerciseParticipation)) {
            throw new EntityNotFoundException("ProgrammingExerciseParticipation with id " + participationId + " could not be found!");
        }

        ProgrammingExerciseParticipation programmingExerciseParticipation = (ProgrammingExerciseParticipation) participation;

        // if the commit is made by the Artemis user and contains the commit message "Setup" (use a constant to determine this), we should ignore this
        // and we should not create a new submission here
        Commit commit;
        try {
            // we can find this out by looking into the requestBody, e.g. changes=[{ref={id=refs/heads/BitbucketStationSupplies, displayId=BitbucketStationSupplies, type=BRANCH}
            // if the branch is different than master, throw an IllegalArgumentException, but make sure the REST call still returns 200 to Bitbucket
            commit = versionControlService.get().getLastCommitDetails(requestBody);
            log.info("NotifyPush invoked due to the commit " + commit.getCommitHash() + " by " + commit.getAuthorName() + " with " + commit.getAuthorEmail() + " in branch "
                    + commit.getBranch());
        }
        catch (Exception ex) {
            log.error("Commit could not be parsed for submission from participation " + programmingExerciseParticipation, ex);
            throw new IllegalArgumentException(ex);
        }

        if (commit.getBranch() != null && !commit.getBranch().equalsIgnoreCase("master")) {
            // if the commit was made in a branch different than master, ignore this
            throw new IllegalStateException(
                    "Submission for participation id " + participationId + " in branch " + commit.getBranch() + " will be ignored! Only the master branch is considered");
        }
        if (commit.getAuthorName() != null && commit.getAuthorName().equalsIgnoreCase(ARTEMIS_GIT_NAME) && commit.getAuthorEmail() != null
                && commit.getAuthorEmail().equalsIgnoreCase(ARTEMIS_GIT_EMAIL) && commit.getMessage() != null && commit.getMessage().equals(SETUP_COMMIT_MESSAGE)) {
            // if the commit was made by Artemis (this means it is a setup commit), we ignore this as well
            throw new IllegalStateException("Submission for participation id " + participationId + " based on an empty setup commit by Artemis will be ignored!");
        }

        if (programmingExerciseParticipation instanceof ProgrammingExerciseStudentParticipation && (programmingExerciseParticipation.getBuildPlanId() == null
                || !programmingExerciseParticipation.getInitializationState().hasCompletedState(InitializationState.INITIALIZED))) {
            // the build plan was deleted before, e.g. due to cleanup, therefore we need to reactivate the build plan by resuming the participation
            // This is needed as a request using a custom query is made using the ProgrammingExerciseRepository, but the user is not authenticated
            // as the VCS-server performs the request
            SecurityUtils.setAuthorizationObject();
            participationService.resumeExercise((ProgrammingExerciseStudentParticipation) programmingExerciseParticipation);
            // Note: in this case we do not need an empty commit: when we trigger the build manually (below), subsequent commits will work correctly
            try {
                continuousIntegrationService.get().triggerBuild(programmingExerciseParticipation);
            }
            catch (HttpException ex) {
                // TODO: This case is currently not handled. The correct handling would be creating the submission and informing the user that the build trigger failed.
            }
        }

        // There can't be two submissions for the same participation and commitHash!
        ProgrammingSubmission programmingSubmission = programmingSubmissionRepository.findFirstByParticipationIdAndCommitHash(participationId, commit.getCommitHash());
        if (programmingSubmission != null) {
            throw new IllegalStateException("Submission for participation id " + participationId + " and commitHash " + commit.getCommitHash() + " already exists!");
        }

        programmingSubmission = new ProgrammingSubmission();
        programmingSubmission.setCommitHash(commit.getCommitHash());
        log.info("create new programmingSubmission with commitHash: " + commit.getCommitHash() + " for participation " + participationId);

        programmingSubmission.setSubmitted(true);
        programmingSubmission.setSubmissionDate(ZonedDateTime.now());
        programmingSubmission.setType(SubmissionType.MANUAL);

        programmingExerciseParticipation.addSubmissions(programmingSubmission);

        programmingSubmission = programmingSubmissionRepository.save(programmingSubmission);
        // NOTE: we don't need to save the participation here, this might lead to concurrency problems when doing the empty commit during resume exercise!
        return programmingSubmission;
    }

    /**
     * A pending submission is one that does not have a result yet.
     *
     * @param participationId the id of the participation get the latest submission for
     * @param filterGraded if true will not use the latest submission, but the latest graded submission.
     * @return the latest pending submission if exists or null.
     * @throws EntityNotFoundException if the participation for the given id can't be found.
     * @throws IllegalArgumentException if the participation for the given id is not a programming exercise participation.
     */
    public Optional<ProgrammingSubmission> getLatestPendingSubmission(Long participationId, boolean filterGraded) throws EntityNotFoundException, IllegalArgumentException {
        Participation participation = participationService.findOne(participationId);
        if (participation == null) {
            throw new EntityNotFoundException("Participation with id " + participationId + " could not be retrieved!");
        }
        if (!(participation instanceof ProgrammingExerciseParticipation)) {
            throw new IllegalArgumentException("Participation with id " + participationId + " is not a programming exercise participation!");
        }
        if (!programmingExerciseParticipationService.canAccessParticipation((ProgrammingExerciseParticipation) participation)) {
            throw new AccessForbiddenException("Participation with id " + participationId + " can't be accessed by user " + SecurityUtils.getCurrentUserLogin());
        }

        return findLatestPendingSubmissionForParticipation(participationId, filterGraded);
    }

    /**
     * For every student participation of a programming exercise, try to find a pending submission.
     *
     * @param programmingExerciseId for which to search pending submissions
     * @return a Map of {[participationId]: ProgrammingSubmission | null}. Will contain an entry for every student participation of the exercise and a submission object if a pending submission exists or null if not.
     */
    public Map<Long, Optional<ProgrammingSubmission>> getLatestPendingSubmissionsForProgrammingExercise(Long programmingExerciseId) {
        List<ProgrammingExerciseStudentParticipation> participations = programmingExerciseParticipationService.findByExerciseId(programmingExerciseId);
        return participations.stream().collect(Collectors.toMap(Participation::getId, p -> findLatestPendingSubmissionForParticipation(p.getId())));
    }

    private Optional<ProgrammingSubmission> findLatestPendingSubmissionForParticipation(final long participationId) {
        return findLatestPendingSubmissionForParticipation(participationId, false);
    }

    private Optional<ProgrammingSubmission> findLatestPendingSubmissionForParticipation(final long participationId, final boolean isGraded) {
        final var optionalSubmission = isGraded
                ? programmingSubmissionRepository.findGradedByParticipationIdOrderBySubmissionDateDesc(participationId, PageRequest.of(0, 1)).stream().findFirst()
                : programmingSubmissionRepository.findFirstByParticipationIdOrderBySubmissionDateDesc(participationId);
        if (optionalSubmission.isEmpty() || optionalSubmission.get().getResult() != null) {
            // This is not an error case, it is very likely that there is no pending submission for a participation.
            return Optional.empty();
        }
        return optionalSubmission;
    }

    /**
     * Trigger the CI of all student participations and the template participation of the given exercise.
     * The build result will become rated regardless of the due date as the submission type is INSTRUCTOR.
     *
     * The method is async because it would timeout a calling resource method.
     *
     * @param exerciseId to identify the programming exercise.
     * @throws EntityNotFoundException if there is no programming exercise for the given exercise id.
     */
    @Async
    public void triggerInstructorBuildForExercise(@PathVariable Long exerciseId) throws EntityNotFoundException {
        // Async can't access the authentication object. We need to do any security checks before this point.
        SecurityUtils.setAuthorizationObject();
        ProgrammingExercise programmingExercise = programmingExerciseService.findById(exerciseId);
        if (programmingExercise == null) {
            throw new EntityNotFoundException("Programming exercise with id " + exerciseId + " not found.");
        }
        log.info("Trigger instructor build for all participations in exercise {} with id {}", programmingExercise.getTitle(), programmingExercise.getId());

        // Let the instructor know that a build run was triggered.
        notifyInstructorAboutStartedExerciseBuildRun(programmingExercise);
        List<ProgrammingExerciseParticipation> participations = new LinkedList<>(programmingExerciseParticipationService.findByExerciseId(exerciseId));

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
            triggerBuildAndNotifyUser(participation);
            index++;
        }

        // When the instructor build was triggered for the programming exercise, it is not considered 'dirty' anymore.
        setTestCasesChanged(programmingExercise.getId(), false);
        // Let the instructor know that the build run is finished.
        notifyInstructorAboutCompletedExerciseBuildRun(programmingExercise);
    }

    private void notifyInstructorAboutStartedExerciseBuildRun(ProgrammingExercise programmingExercise) {
        websocketMessagingService.sendMessage(getProgrammingExerciseAllExerciseBuildsTriggeredTopic(programmingExercise.getId()), BuildRunState.RUNNING);
        // Send a notification to the client to inform the instructor about the test case update.
        groupNotificationService.notifyInstructorGroupAboutExerciseUpdate(programmingExercise, BUILD_RUN_STARTED_FOR_PROGRAMMING_EXERCISE);
    }

    private void notifyInstructorAboutCompletedExerciseBuildRun(ProgrammingExercise programmingExercise) {
        websocketMessagingService.sendMessage(getProgrammingExerciseAllExerciseBuildsTriggeredTopic(programmingExercise.getId()), BuildRunState.COMPLETED);
        // Send a notification to the client to inform the instructor about the test case update.
        groupNotificationService.notifyInstructorGroupAboutExerciseUpdate(programmingExercise, BUILD_RUN_COMPLETE_FOR_PROGRAMMING_EXERCISE);
    }

    /**
     * Create a submission with given submission type for the last commit hash of the given participation.
     * WARNING: The commitHash is used to map incoming results to submissions. Using this method could cause the result to have multiple fitting submissions.
     *
     * See discussion in: https://github.com/ls1intum/Artemis/pull/712#discussion_r314944129;
     *
     * Worst case scenario when using this method:
     * 1) Student executes a submission, the build is created on Bamboo
     * 2) The build takes longer than 2 minutes, this enables the student to trigger the submission again
     * 3) A new submission with the same commitHash is created on the server, there are now 2 submissions for the same commitHash and 2 running builds
     * 4) The first build returns a result to Artemis, this result is now attached to the second submission (that was just created)
     * 5) The second build finishes and returns a result to Artemis, this result is attached to the first submission
     *
     * @param participation to create submission for.
     * @param submissionType of the submission to create.
     * @return created submission.
     * @throws IllegalStateException if the last commit hash can't be retrieved.
     */
    public ProgrammingSubmission createSubmissionWithLastCommitHashForParticipation(ProgrammingExerciseParticipation participation, SubmissionType submissionType)
            throws IllegalStateException {
        ObjectId lastCommitHash = getLastCommitHashForParticipation(participation);
        return createSubmissionWithCommitHashAndSubmissionType(participation, lastCommitHash, submissionType);
    }

    private ObjectId getLastCommitHashForParticipation(ProgrammingExerciseParticipation participation) throws IllegalStateException {
        URL repoUrl = participation.getRepositoryUrlAsUrl();
        ObjectId lastCommitHash;
        try {
            lastCommitHash = gitService.getLastCommitHash(repoUrl);
        }
        catch (EntityNotFoundException ex) {
            throw new IllegalStateException("Last commit hash for participation " + participation.getId() + " could not be retrieved");
        }
        return lastCommitHash;
    }

    private ObjectId getLastCommitHashForTestRepository(ProgrammingExercise programmingExercise) throws IllegalStateException {
        URL repoUrl = programmingExercise.getTestRepositoryUrlAsUrl();
        ObjectId lastCommitHash;
        try {
            lastCommitHash = gitService.getLastCommitHash(repoUrl);
        }
        catch (EntityNotFoundException ex) {
            throw new IllegalStateException("Last commit hash for test repository of programming exercise with id " + programmingExercise.getId() + " could not be retrieved");
        }
        return lastCommitHash;
    }

    /**
     * Create a submission with SubmissionType.TEST and the provided commitHash.
     *
     * @param programmingExerciseId     ProgrammingExercise id.
     * @param commitHash                last commitHash of the test repository, if null will use the last commitHash of the test repository.
     * @return The created solutionSubmission.
     * @throws EntityNotFoundException  if the programming exercise for the given id does not exist.
     * @throws IllegalStateException    If no commitHash was no provided and no commitHash could be retrieved from the test repository.
     */
    public ProgrammingSubmission createSolutionParticipationSubmissionWithTypeTest(Long programmingExerciseId, @Nullable ObjectId commitHash)
            throws EntityNotFoundException, IllegalStateException {
        SolutionProgrammingExerciseParticipation solutionParticipation = programmingExerciseParticipationService
                .findSolutionParticipationByProgrammingExerciseId(programmingExerciseId);
        // If no commitHash is provided, use the last commitHash for the test repository.
        if (commitHash == null) {
            ProgrammingExercise programmingExercise = programmingExerciseService.findById(programmingExerciseId);
            commitHash = getLastCommitHashForTestRepository(programmingExercise);
        }
        return createSubmissionWithCommitHashAndSubmissionType(solutionParticipation, commitHash, SubmissionType.TEST);
    }

    private ProgrammingSubmission createSubmissionWithCommitHashAndSubmissionType(ProgrammingExerciseParticipation participation, ObjectId commitHash,
            SubmissionType submissionType) {
        ProgrammingSubmission newSubmission = (ProgrammingSubmission) new ProgrammingSubmission().commitHash(commitHash.getName()).submitted(true)
                .submissionDate(ZonedDateTime.now()).type(submissionType);
        newSubmission.setParticipation((Participation) participation);
        return programmingSubmissionRepository.saveAndFlush(newSubmission);
    }

    /**
     * Uses {@link #createSubmissionWithLastCommitHashForParticipation(ProgrammingExerciseParticipation, SubmissionType)} but for multiple participations.
     * Will ignore exceptions that are raised by this method and just not create a submission for the concerned participations.
     *
     * @param participations for which to create new submissions.
     * @param submissionType the type for the submissions to be created.
     * @return list of created submissions (might be smaller as the list of provided participations!).
     */
    public List<ProgrammingSubmission> createSubmissionWithLastCommitHashForParticipationsOfExercise(List<ProgrammingExerciseParticipation> participations,
            SubmissionType submissionType) {
        return participations.stream().map(participation -> {
            try {
                return createSubmissionWithLastCommitHashForParticipation(participation, submissionType);
            }
            catch (IllegalStateException ex) {
                // The exception is already logged, we just return null here.
                return null;
            }
        }).filter(Objects::nonNull).collect(Collectors.toList());
    }

    /**
     * Trigger a CI build for each submission & notify each user on a new programming submission.
     * Instead of triggering all builds at the same time, we execute the builds in batches to not overload the CIS system.
     *
     * Note: This call "resumes the exercise", i.e. re-creates the build plan if the build plan was already cleaned before
     *
     * @param participation the participation for which we create a new submission and new result
     */
    public void triggerBuildAndNotifyUser(ProgrammingExerciseParticipation participation) {
        var submission = createSubmissionWithLastCommitHashForParticipation(participation, SubmissionType.INSTRUCTOR);
        triggerBuildAndNotifyUser(submission);
    }

    /**
     * Sends a websocket message to the user about the new submission and triggers a build on the CI system.
     * Will send an error object in the case that the communication with the CI failed.
     *
     * Note: This call "resumes the exercise", i.e. re-creates the build plan if the build plan was already cleaned before
     *
     * @param submission ProgrammingSubmission that was just created.
     */
    public void triggerBuildAndNotifyUser(ProgrammingSubmission submission) {
        var programmingExerciseParticipation = (ProgrammingExerciseParticipation) submission.getParticipation();
        try {
            if (programmingExerciseParticipation instanceof ProgrammingExerciseStudentParticipation && (programmingExerciseParticipation.getBuildPlanId() == null
                    || !programmingExerciseParticipation.getInitializationState().hasCompletedState(InitializationState.INITIALIZED))) {
                // in this case, we first have to resume the exercise: this includes that we again setup the build plan properly before we trigger it
                participationService.resumeExercise((ProgrammingExerciseStudentParticipation) programmingExerciseParticipation);
                // Note: in this case we do not need an empty commit: when we trigger the build manually (below), subsequent commits will work correctly
            }
            continuousIntegrationService.get().triggerBuild(programmingExerciseParticipation);
            notifyUserAboutSubmission(submission);
        }
        catch (Exception e) {
            log.error("Trigger build failed for " + programmingExerciseParticipation.getBuildPlanId() + " with the exception " + e.getMessage());
            BuildTriggerWebsocketError error = new BuildTriggerWebsocketError(e.getMessage(), submission.getParticipation().getId());
            notifyUserAboutSubmissionError(submission, error);
        }
    }

    /**
     * Trigger the template repository build with the given commitHash.
     *
     * @param programmingExerciseId     is used to retrieve the template participation.
     * @param commitHash                will be used for the created submission.
     * @param submissionType            will be used for the created submission.
     * @throws EntityNotFoundException  if the programming exercise has no template participation (edge case).
     */
    public void triggerTemplateBuildAndNotifyUser(long programmingExerciseId, ObjectId commitHash, SubmissionType submissionType) throws EntityNotFoundException {
        TemplateProgrammingExerciseParticipation templateParticipation;
        templateParticipation = programmingExerciseParticipationService.findTemplateParticipationByProgrammingExerciseId(programmingExerciseId);
        // If for some reason the programming exercise does not have a template participation, we can only log and abort.
        createSubmissionTriggerBuildAndNotifyUser(templateParticipation, commitHash, submissionType);
    }

    /**
     * Creates a submission with the given type and commitHash for the provided participation.
     * Will notify the user about occurring errors when trying to trigger the build.
     *
     * @param participation  for which to create the submission.
     * @param commitHash     to assign to the submission.
     * @param submissionType to assign to the submission.
     */
    private void createSubmissionTriggerBuildAndNotifyUser(ProgrammingExerciseParticipation participation, ObjectId commitHash, SubmissionType submissionType) {
        ProgrammingSubmission submission = createSubmissionWithCommitHashAndSubmissionType(participation, commitHash, submissionType);
        try {
            continuousIntegrationService.get().triggerBuild((ProgrammingExerciseParticipation) submission.getParticipation());
            notifyUserAboutSubmission(submission);
        }
        catch (HttpException e) {
            BuildTriggerWebsocketError error = new BuildTriggerWebsocketError(e.getMessage(), submission.getParticipation().getId());
            notifyUserAboutSubmissionError(submission, error);
        }
    }

    /**
     * Executes setTestCasesChanged with testCasesChanged = true, also creates a submission for the solution participation and triggers its build.
     * This method should be used if the solution participation would otherwise not be built.
     *
     * @param programmingExerciseId ProgrammingExercise id
     * @throws EntityNotFoundException if there is no programming exercise for the given id.
     */
    public void setTestCasesChangedAndTriggerTestCaseUpdate(Long programmingExerciseId) throws EntityNotFoundException {
        setTestCasesChanged(programmingExerciseId, true);
        try {
            ProgrammingSubmission submission = createSolutionParticipationSubmissionWithTypeTest(programmingExerciseId, null);
            triggerBuildAndNotifyUser(submission);
            return;
        }
        catch (IllegalStateException ex) {
            log.debug("No submission could be created for the programming exercise with the id " + programmingExerciseId + ", trying to trigger the build without a submission.");
        }

        // Edge case: If no submission could be created, just trigger the solution build. On receiving the result, Artemis will try to create a new submission with the result
        // completionDate.
        SolutionProgrammingExerciseParticipation solutionParticipation = programmingExerciseParticipationService
                .findSolutionParticipationByProgrammingExerciseId(programmingExerciseId);
        try {
            continuousIntegrationService.get().triggerBuild(solutionParticipation);
        }
        catch (HttpException ex) {
            log.error("Could not trigger build for solution repository after test case update for programming exercise with id " + programmingExerciseId);
        }
    }

    /**
     * If testCasesChanged = true, this marks the programming exercise as dirty, meaning that its test cases were changed and the student submissions should be be built & tested.
     * This method also sends out a notification to the client if testCasesChanged = true.
     * In case the testCaseChanged value is the same for the programming exercise or the programming exercise is not released or has no results, the method will return immediately.
     *
     * @param programmingExerciseId id of a ProgrammingExercise.
     * @param testCasesChanged      set to true to mark the programming exercise as dirty.
     * @return the updated ProgrammingExercise.
     * @throws EntityNotFoundException if the programming exercise does not exist.
     */
    public ProgrammingExercise setTestCasesChanged(Long programmingExerciseId, boolean testCasesChanged) throws EntityNotFoundException {
        ProgrammingExercise programmingExercise = programmingExerciseService.findById(programmingExerciseId);

        // If the programming exercise is not released / has no results, there is no point in setting the dirty flag. It is only relevant when there are student submissions that
        // should get an updated result.
        if (testCasesChanged == programmingExercise.getTestCasesChanged() || !programmingExerciseService.hasAtLeastOneStudentResult(programmingExercise)) {
            return programmingExercise;
        }
        programmingExercise.setTestCasesChanged(testCasesChanged);
        ProgrammingExercise updatedProgrammingExercise = programmingExerciseService.save(programmingExercise);
        // Send a websocket message about the new state to the client.
        websocketMessagingService.sendMessage(getProgrammingExerciseTestCaseChangedTopic(programmingExerciseId), testCasesChanged);
        // Send a notification to the client to inform the instructor about the test case update.
        String notificationText = testCasesChanged ? TEST_CASES_CHANGED_NOTIFICATION : TEST_CASES_CHANGED_RUN_COMPLETED_NOTIFICATION;
        groupNotificationService.notifyInstructorGroupAboutExerciseUpdate(updatedProgrammingExercise, notificationText);

        return updatedProgrammingExercise;
    }

    private String getProgrammingExerciseTestCaseChangedTopic(Long programmingExerciseId) {
        return "/topic/programming-exercises/" + programmingExerciseId + "/test-cases-changed";
    }

    private String getProgrammingExerciseAllExerciseBuildsTriggeredTopic(Long programmingExerciseId) {
        return "/topic/programming-exercises/" + programmingExerciseId + "/all-builds-triggered";
    }

    /**
     * Notify user on a new programming submission.
     * @param submission ProgrammingSubmission
     */
    public void notifyUserAboutSubmission(ProgrammingSubmission submission) {
        String topic = Constants.PARTICIPATION_TOPIC_ROOT + submission.getParticipation().getId() + Constants.PROGRAMMING_SUBMISSION_TOPIC;
        messagingTemplate.convertAndSend(topic, submission);
    }

    private void notifyUserAboutSubmissionError(ProgrammingSubmission submission, BuildTriggerWebsocketError error) {
        String topic = Constants.PARTICIPATION_TOPIC_ROOT + submission.getParticipation().getId() + Constants.PROGRAMMING_SUBMISSION_TOPIC;
        messagingTemplate.convertAndSend(topic, error);
    }

    public ProgrammingSubmission findByResultId(long resultId) throws EntityNotFoundException {
        Optional<ProgrammingSubmission> programmingSubmission = programmingSubmissionRepository.findByResultId(resultId);
        return programmingSubmission.orElseThrow(() -> new EntityNotFoundException("Could not find programming submission for result id " + resultId));
    }

    /**
     * Given an exercise id and a tutor id, it returns all the programming submissions where the tutor has a result associated
     *
     * @param exerciseId - the id of the exercise we are looking for
     * @param tutorId    - the id of the tutor we are interested in
     * @return a list of programming submissions
     */
    public List<ProgrammingSubmission> getAllProgrammingSubmissionsByTutorForExercise(long exerciseId, long tutorId) {
        List<StudentParticipation> participations = this.studentParticipationRepository.findWithLatestSubmissionByExerciseAndAssessor(exerciseId, tutorId);
        return participations.stream().map(Participation::findLatestSubmission).filter(Optional::isPresent).map(submission -> (ProgrammingSubmission) submission.get())
                .collect(Collectors.toList());
    }

    /**
     * Given an exerciseId, returns all the programming submissions for that exercise, including their results. Submissions can be filtered to include only already submitted
     * submissions
     *
     * @param exerciseId    - the id of the exercise we are interested into
     * @param submittedOnly - if true, it returns only submission with submitted flag set to true
     * @return a list of programming submissions for the given exercise id
     */
    public List<ProgrammingSubmission> getProgrammingSubmissions(long exerciseId, boolean submittedOnly) {
        List<StudentParticipation> participations = studentParticipationRepository.findAllByExerciseIdWithEagerSubmissionsAndEagerResultsAndEagerAssessor(exerciseId);
        List<ProgrammingSubmission> submissions = new ArrayList<>();
        participations.stream().peek(participation -> participation.getExercise().setStudentParticipations(null)).map(StudentParticipation::findLatestSubmission)
                // filter out non submitted submissions if the flag is set to true
                .filter(submission -> submission.isPresent() && (!submittedOnly || submission.get().isSubmitted()))
                .forEach(submission -> submissions.add((ProgrammingSubmission) submission.get()));
        return submissions;
    }

    /**
     * Given an exercise id, find a random programming submission for that exercise which still doesn't have any manual result. No manual result means that no user has started an
     * assessment for the corresponding submission yet.
     *
     * @param programmingExercise the exercise for which we want to retrieve a submission without manual result
     * @return a fileUploadSubmission without any manual result or an empty Optional if no submission without manual result could be found
     */
    public Optional<ProgrammingSubmission> getRandomProgrammingSubmissionWithoutManualResult(ProgrammingExercise programmingExercise) {
        Random r = new Random();
        List<ProgrammingSubmission> submissionsWithoutResult = participationService.findByExerciseIdWithLatestSubmissionWithoutManualResults(programmingExercise.getId()).stream()
                .map(StudentParticipation::findLatestSubmission).filter(Optional::isPresent).map(Optional::get).map(submission -> (ProgrammingSubmission) submission)
                .collect(Collectors.toList());

        if (submissionsWithoutResult.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(submissionsWithoutResult.get(r.nextInt(submissionsWithoutResult.size())));
    }

    /**
     * Get the programming submission with the given id from the database. The submission is loaded together with exercise it belongs to, its result, the feedback of the result and the assessor of the
     * result. Throws an EntityNotFoundException if no submission could be found for the given id.
     *
     * @param submissionId the id of the submission that should be loaded from the database
     * @return the programming submission with the given id
     */
    public ProgrammingSubmission findByIdWithEagerResultAndFeedback(long submissionId) {
        return programmingSubmissionRepository.findByIdWithEagerResultAndFeedback(submissionId)
                .orElseThrow(() -> new EntityNotFoundException("Programming submission with id \"" + submissionId + "\" does not exist"));
    }

    public void hideDetails(ProgrammingSubmission submission, User user) {
        submissionService.hideDetails(submission, user);
    }
}
