package de.tum.in.www1.artemis.service.programming;

import static de.tum.in.www1.artemis.config.Constants.*;
import static java.util.stream.Collectors.toList;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.audit.AuditEvent;
import org.springframework.boot.actuate.audit.AuditEventRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.InitializationState;
import de.tum.in.www1.artemis.domain.enumeration.SubmissionType;
import de.tum.in.www1.artemis.domain.participation.*;
import de.tum.in.www1.artemis.exception.ContinuousIntegrationException;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.*;
import de.tum.in.www1.artemis.service.connectors.ContinuousIntegrationService;
import de.tum.in.www1.artemis.service.connectors.GitService;
import de.tum.in.www1.artemis.service.connectors.VersionControlService;
import de.tum.in.www1.artemis.service.exam.ExamDateService;
import de.tum.in.www1.artemis.service.exam.ExamSubmissionService;
import de.tum.in.www1.artemis.service.hestia.ProgrammingExerciseGitDiffReportService;
import de.tum.in.www1.artemis.service.notifications.GroupNotificationService;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;
import de.tum.in.www1.artemis.web.websocket.programmingSubmission.BuildTriggerWebsocketError;

@Service
public class ProgrammingSubmissionService extends SubmissionService {

    private final Logger log = LoggerFactory.getLogger(ProgrammingSubmissionService.class);

    @Value("${artemis.git.name}")
    private String artemisGitName;

    @Value("${artemis.git.email}")
    private String artemisGitEmail;

    @Value("${artemis.external-system-request.batch-size}")
    private int externalSystemRequestBatchSize;

    @Value("${artemis.external-system-request.batch-waiting-time}")
    private int externalSystemRequestBatchWaitingTime;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final ProgrammingSubmissionRepository programmingSubmissionRepository;

    private final ResultRepository resultRepository;

    private final ProgrammingExerciseParticipationService programmingExerciseParticipationService;

    private final ExamSubmissionService examSubmissionService;

    private final GroupNotificationService groupNotificationService;

    private final WebsocketMessagingService websocketMessagingService;

    private final Optional<VersionControlService> versionControlService;

    private final Optional<ContinuousIntegrationService> continuousIntegrationService;

    private final GitService gitService;

    private final SimpMessageSendingOperations messagingTemplate;

    private final AuditEventRepository auditEventRepository;

    private final ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository;

    private final ProgrammingExerciseGitDiffReportService programmingExerciseGitDiffReportService;

    public ProgrammingSubmissionService(ProgrammingSubmissionRepository programmingSubmissionRepository, ProgrammingExerciseRepository programmingExerciseRepository,
            GroupNotificationService groupNotificationService, SubmissionRepository submissionRepository, UserRepository userRepository, AuthorizationCheckService authCheckService,
            WebsocketMessagingService websocketMessagingService, Optional<VersionControlService> versionControlService, ResultRepository resultRepository,
            Optional<ContinuousIntegrationService> continuousIntegrationService, ParticipationService participationService, SimpMessageSendingOperations messagingTemplate,
            ProgrammingExerciseParticipationService programmingExerciseParticipationService, ExamSubmissionService examSubmissionService, GitService gitService,
            StudentParticipationRepository studentParticipationRepository, FeedbackRepository feedbackRepository, AuditEventRepository auditEventRepository,
            ExamDateService examDateService, ExerciseDateService exerciseDateService, CourseRepository courseRepository, ParticipationRepository participationRepository,
            ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository, ComplaintRepository complaintRepository,
            ProgrammingExerciseGitDiffReportService programmingExerciseGitDiffReportService) {
        super(submissionRepository, userRepository, authCheckService, resultRepository, studentParticipationRepository, participationService, feedbackRepository, examDateService,
                exerciseDateService, courseRepository, participationRepository, complaintRepository);
        this.programmingSubmissionRepository = programmingSubmissionRepository;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.groupNotificationService = groupNotificationService;
        this.websocketMessagingService = websocketMessagingService;
        this.versionControlService = versionControlService;
        this.continuousIntegrationService = continuousIntegrationService;
        this.messagingTemplate = messagingTemplate;
        this.programmingExerciseParticipationService = programmingExerciseParticipationService;
        this.examSubmissionService = examSubmissionService;
        this.gitService = gitService;
        this.resultRepository = resultRepository;
        this.auditEventRepository = auditEventRepository;
        this.programmingExerciseStudentParticipationRepository = programmingExerciseStudentParticipationRepository;
        this.programmingExerciseGitDiffReportService = programmingExerciseGitDiffReportService;
    }

    /**
     * This method gets called if a new commit was pushed to the VCS
     *
     * @param participationId The ID to the Participation, where the push happened
     * @param requestBody     the body of the post request by the VCS.
     * @return the ProgrammingSubmission for the last commitHash
     * @throws EntityNotFoundException  if no ProgrammingExerciseParticipation could be found
     * @throws IllegalStateException    if a ProgrammingSubmission already exists
     * @throws IllegalArgumentException it the Commit hash could not be parsed for submission from participation
     */
    public ProgrammingSubmission notifyPush(Long participationId, Object requestBody) throws EntityNotFoundException, IllegalStateException, IllegalArgumentException {
        // Note: the following line is intentionally at the top of the method to get the most accurate submission date
        ZonedDateTime submissionDate = ZonedDateTime.now();
        Participation participation = participationRepository.findByIdWithLegalSubmissionsElseThrow(participationId);
        if (!(participation instanceof ProgrammingExerciseParticipation programmingExerciseParticipation)) {
            throw new EntityNotFoundException("Programming Exercise Participation", participationId);
        }

        // if the commit is made by the Artemis user and contains the commit message "Setup" (use a constant to determine this), we should ignore this
        // and we should not create a new submission here
        Commit commit;
        try {
            // we can find this out by looking into the requestBody, e.g. changes=[{ref={id=refs/heads/BitbucketStationSupplies, displayId=BitbucketStationSupplies, type=BRANCH}
            // if the branch is different from main, throw an IllegalArgumentException, but make sure the REST call still returns 200 to Bitbucket
            commit = versionControlService.get().getLastCommitDetails(requestBody);
            log.info("NotifyPush invoked due to the commit {} by {} with {} in branch {}", commit.getCommitHash(), commit.getAuthorName(), commit.getAuthorEmail(),
                    commit.getBranch());
        }
        catch (Exception ex) {
            log.error("Commit could not be parsed for submission from participation " + programmingExerciseParticipation, ex);
            throw new IllegalArgumentException(ex);
        }

        String branch = versionControlService.get().getOrRetrieveBranchOfParticipation(programmingExerciseParticipation);
        if (commit.getBranch() != null && !commit.getBranch().equalsIgnoreCase(branch)) {
            // if the commit was made in a branch different from the default, ignore this
            throw new IllegalStateException(
                    "Submission for participation id " + participationId + " in branch " + commit.getBranch() + " will be ignored! Only the default branch is considered");
        }
        if (artemisGitName.equalsIgnoreCase(commit.getAuthorName()) && artemisGitEmail.equalsIgnoreCase(commit.getAuthorEmail())
                && SETUP_COMMIT_MESSAGE.equals(commit.getMessage())) {
            // if the commit was made by Artemis and the message is "Setup" (this means it is an empty setup commit), we ignore this as well and do not create a submission!
            throw new IllegalStateException("Submission for participation id " + participationId + " based on an empty setup commit by Artemis will be ignored!");
        }

        if (programmingExerciseParticipation instanceof ProgrammingExerciseStudentParticipation && (programmingExerciseParticipation.getBuildPlanId() == null
                || !programmingExerciseParticipation.getInitializationState().hasCompletedState(InitializationState.INITIALIZED))) {
            // the build plan was deleted before, e.g. due to cleanup, therefore we need to reactivate the build plan by resuming the participation
            // This is needed as a request using a custom query is made using the ProgrammingExerciseRepository, but the user is not authenticated
            // as the VCS-server performs the request
            SecurityUtils.setAuthorizationObject();

            participationService.resumeProgrammingExercise((ProgrammingExerciseStudentParticipation) programmingExerciseParticipation);
            // Note: in this case we do not need an empty commit: when we trigger the build manually (below), subsequent commits will work correctly
            try {
                continuousIntegrationService.get().triggerBuild(programmingExerciseParticipation);
            }
            catch (ContinuousIntegrationException ex) {
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
        log.info("Create new programmingSubmission with commitHash: {} for participation {}", commit.getCommitHash(), participationId);

        programmingSubmission.setSubmitted(true);

        programmingSubmission.setSubmissionDate(submissionDate);
        programmingSubmission.setType(SubmissionType.MANUAL);

        // Students are not allowed to submit a programming exercise after the exam due date, if this happens we set the Submission to ILLEGAL
        checkForIllegalExamSubmission(programmingExerciseParticipation, programmingSubmission);

        programmingExerciseParticipation.addSubmission(programmingSubmission);

        programmingSubmission = programmingSubmissionRepository.save(programmingSubmission);

        updateGitDiffReport(programmingExerciseParticipation);

        // NOTE: we don't need to save the participation here, this might lead to concurrency problems when doing the empty commit during resume exercise!
        return programmingSubmission;
    }

    /**
     * Update the git-diff of the programming exercise when the push was to a solution or template repository
     *
     * @param programmingExerciseParticipation The participation
     */
    private void updateGitDiffReport(ProgrammingExerciseParticipation programmingExerciseParticipation) {
        if (programmingExerciseParticipation instanceof TemplateProgrammingExerciseParticipation
                || programmingExerciseParticipation instanceof SolutionProgrammingExerciseParticipation) {
            try {
                programmingExerciseGitDiffReportService.updateReport(programmingExerciseParticipation.getProgrammingExercise());
            }
            catch (Exception e) {
                log.error("Unable to update git-diff for programming exercise " + programmingExerciseParticipation.getProgrammingExercise().getId(), e);
            }
        }
    }

    /**
     * We check if a submission for an exam programming exercise is after the individual end date and a student is not allowed to submit anymore.
     * If this is the case, the submission is set to {@link SubmissionType#ILLEGAL}.
     *
     * @param programmingExerciseParticipation current participation of the exam exercise
     * @param programmingSubmission            new created submission of the repository commit
     */
    private void checkForIllegalExamSubmission(ProgrammingExerciseParticipation programmingExerciseParticipation, ProgrammingSubmission programmingSubmission) {
        ProgrammingExercise programmingExercise = programmingExerciseParticipation.getProgrammingExercise();
        boolean isExamExercise = programmingExercise.isExamExercise();
        // Students are not allowed to submit a programming exercise after the exam due date, if this happens we set the Submission to ILLEGAL
        if (isExamExercise && programmingExerciseParticipation instanceof ProgrammingExerciseStudentParticipation) {
            var optionalStudent = ((ProgrammingExerciseStudentParticipation) programmingExerciseParticipation).getStudent();
            Optional<User> optionalStudentWithGroups = optionalStudent.isPresent() ? userRepository.findOneWithGroupsAndAuthoritiesByLogin(optionalStudent.get().getLogin())
                    : Optional.empty();
            if (optionalStudentWithGroups.isPresent() && !examSubmissionService.isAllowedToSubmitDuringExam(programmingExercise, optionalStudentWithGroups.get(), true)) {
                final String message = "The student " + optionalStudentWithGroups.get().getLogin()
                        + " just illegally submitted code after the allowed individual due date (including the grace period) in the participation "
                        + programmingExerciseParticipation.getId() + " for the exam programming exercise " + programmingExercise.getId();
                programmingSubmission.setType(SubmissionType.ILLEGAL);
                groupNotificationService.notifyInstructorGroupAboutIllegalSubmissionsForExercise(programmingExercise, message);
                log.warn(message);
            }
        }
    }

    /**
     * A pending submission is one that does not have a result yet.
     *
     * @param participationId the id of the participation get the latest submission for
     * @param filterGraded    if true will not use the latest submission, but the latest graded submission.
     * @return the latest pending submission if exists or null.
     * @throws EntityNotFoundException  if the participation for the given id can't be found.
     * @throws IllegalArgumentException if the participation for the given id is not a programming exercise participation.
     */
    public Optional<ProgrammingSubmission> getLatestPendingSubmission(Long participationId, boolean filterGraded) throws EntityNotFoundException, IllegalArgumentException {
        Participation participation = participationRepository.findByIdElseThrow(participationId);
        if (!(participation instanceof ProgrammingExerciseParticipation programmingExerciseParticipation)) {
            throw new IllegalArgumentException("Participation with id " + participationId + " is not a programming exercise participation!");
        }
        if (!programmingExerciseParticipationService.canAccessParticipation(programmingExerciseParticipation)) {
            throw new AccessForbiddenException("Participation with id " + participationId + " can't be accessed by user " + SecurityUtils.getCurrentUserLogin());
        }

        return findLatestPendingSubmissionForParticipation(participationId, filterGraded);
    }

    /**
     * For every student participation of a programming exercise, try to find a pending submission.
     *
     * @param programmingExerciseId for which to search pending submissions
     * @return a Map of {[participationId]: ProgrammingSubmission | null}. Will contain an entry for every student participation of the exercise and a submission object if a
     * pending submission exists or null if not.
     */
    public Map<Long, Optional<ProgrammingSubmission>> getLatestPendingSubmissionsForProgrammingExercise(Long programmingExerciseId) {
        List<ProgrammingExerciseStudentParticipation> participations = programmingExerciseStudentParticipationRepository.findWithSubmissionsByExerciseId(programmingExerciseId);
        // TODO: find the latest pending submission directly using Java (the submissions are available now) and not with additional db queries
        return participations.stream().collect(Collectors.toMap(Participation::getId, p -> findLatestPendingSubmissionForParticipation(p.getId())));
    }

    private Optional<ProgrammingSubmission> findLatestPendingSubmissionForParticipation(final long participationId) {
        return findLatestPendingSubmissionForParticipation(participationId, false);
    }

    private Optional<ProgrammingSubmission> findLatestPendingSubmissionForParticipation(final long participationId, final boolean isGraded) {
        final var optionalSubmission = isGraded
                ? programmingSubmissionRepository.findGradedByParticipationIdOrderBySubmissionDateDesc(participationId, PageRequest.of(0, 1)).stream().findFirst()
                : programmingSubmissionRepository.findFirstByParticipationIdOrderBySubmissionDateDesc(participationId);

        if (optionalSubmission.isEmpty() || optionalSubmission.get().getLatestResult() != null) {
            // This is not an error case, it is very likely that there is no pending submission for a participation.
            return Optional.empty();
        }
        return optionalSubmission;
    }

    /**
     * Trigger the CI of all student participations and the template participation of the given exercise.
     * The build result will become rated regardless of the due date as the submission type is INSTRUCTOR.
     *
     * The method is async because it would time out a calling resource method.
     *
     * @param exerciseId to identify the programming exercise.
     * @throws EntityNotFoundException if there is no programming exercise for the given exercise id.
     */
    @Async
    public void triggerInstructorBuildForExercise(Long exerciseId) throws EntityNotFoundException {
        // Async can't access the authentication object. We need to do any security checks before this point.
        SecurityUtils.setAuthorizationObject();
        var programmingExercise = programmingExerciseRepository.findByIdElseThrow(exerciseId);

        // Let the instructor know that a build run was triggered.
        notifyInstructorAboutStartedExerciseBuildRun(programmingExercise);
        List<ProgrammingExerciseStudentParticipation> participations = new ArrayList<>(
                programmingExerciseStudentParticipationRepository.findWithSubmissionsByExerciseId(exerciseId));

        triggerBuildForParticipations(participations);

        // When the instructor build was triggered for the programming exercise, it is not considered 'dirty' anymore.
        setTestCasesChanged(programmingExercise, false);
        // Let the instructor know that the build run is finished.
        notifyInstructorAboutCompletedExerciseBuildRun(programmingExercise);
    }

    /**
     * trigger the build using the batch size approach for all participations
     *
     * @param participations the participations for which the method triggerBuild should be executed.
     */
    public void triggerBuildForParticipations(List<ProgrammingExerciseStudentParticipation> participations) {
        var index = 0;
        for (var participation : participations) {
            // Execute requests in batches instead all at once.
            if (index > 0 && index % externalSystemRequestBatchSize == 0) {
                try {
                    log.info("Sleep for {}s during triggerBuild", externalSystemRequestBatchWaitingTime / 1000);
                    Thread.sleep(externalSystemRequestBatchWaitingTime);
                }
                catch (InterruptedException ex) {
                    log.error("Exception encountered when pausing before executing successive build for participation " + participation.getId(), ex);
                }
            }
            triggerBuild(participation);
            index++;
        }
    }

    public void logTriggerInstructorBuild(User user, Exercise exercise, Course course) {
        var auditEvent = new AuditEvent(user.getLogin(), TRIGGER_INSTRUCTOR_BUILD, "exercise=" + exercise.getTitle(), "course=" + course.getTitle());
        auditEventRepository.add(auditEvent);
        log.info("User {} triggered an instructor build for all participations in exercise {} with id {}", user.getLogin(), exercise.getTitle(), exercise.getId());
    }

    private void notifyInstructorAboutStartedExerciseBuildRun(ProgrammingExercise programmingExercise) {
        websocketMessagingService.sendMessage(getProgrammingExerciseAllExerciseBuildsTriggeredTopic(programmingExercise.getId()), BuildRunState.RUNNING);
        // Send a notification to the client to inform the instructor about the test case update.
        groupNotificationService.notifyEditorAndInstructorGroupAboutExerciseUpdate(programmingExercise, BUILD_RUN_STARTED_FOR_PROGRAMMING_EXERCISE);
    }

    private void notifyInstructorAboutCompletedExerciseBuildRun(ProgrammingExercise programmingExercise) {
        websocketMessagingService.sendMessage(getProgrammingExerciseAllExerciseBuildsTriggeredTopic(programmingExercise.getId()), BuildRunState.COMPLETED);
        // Send a notification to the client to inform the instructor about the test case update.
        groupNotificationService.notifyEditorAndInstructorGroupAboutExerciseUpdate(programmingExercise, BUILD_RUN_COMPLETE_FOR_PROGRAMMING_EXERCISE);
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
     * @param participation  to create submission for.
     * @param submissionType of the submission to create.
     * @return created or reused submission.
     * @throws IllegalStateException if the last commit hash can't be retrieved.
     */
    public ProgrammingSubmission getOrCreateSubmissionWithLastCommitHashForParticipation(ProgrammingExerciseParticipation participation, SubmissionType submissionType)
            throws IllegalStateException {
        String lastCommitHash = getLastCommitHashForParticipation(participation);
        // we first try to get an existing programming submission with the last commit hash
        var programmingSubmission = programmingSubmissionRepository.findFirstByParticipationIdAndCommitHash(participation.getId(), lastCommitHash);
        // in case no programming submission is available, we create one
        return Objects.requireNonNullElseGet(programmingSubmission, () -> createSubmissionWithCommitHashAndSubmissionType(participation, lastCommitHash, submissionType));
    }

    private String getLastCommitHashForParticipation(ProgrammingExerciseParticipation participation) throws IllegalStateException {
        try {
            return gitService.getLastCommitHash(participation.getVcsRepositoryUrl()).getName();
        }
        catch (EntityNotFoundException ex) {
            var message = "Last commit hash for participation " + participation.getId() + " could not be retrieved due to exception: " + ex.getMessage();
            log.warn(message);
            throw new IllegalStateException(message);
        }
    }

    /**
     * Create a submission with SubmissionType.TEST and the provided commitHash.
     *
     * @param programmingExerciseId ProgrammingExercise id.
     * @param commitHash            last commitHash of the test repository, if null will use the last commitHash of the test repository.
     * @return The created solutionSubmission.
     * @throws EntityNotFoundException if the programming exercise for the given id does not exist.
     * @throws IllegalStateException   If no commitHash was no provided and no commitHash could be retrieved from the test repository.
     */
    public ProgrammingSubmission createSolutionParticipationSubmissionWithTypeTest(Long programmingExerciseId, @Nullable String commitHash)
            throws EntityNotFoundException, IllegalStateException {
        var solutionParticipation = programmingExerciseParticipationService.findSolutionParticipationByProgrammingExerciseId(programmingExerciseId);
        // If no commitHash is provided, use the last commitHash for the test repository.
        if (commitHash == null) {
            ProgrammingExercise programmingExercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(programmingExerciseId);
            try {
                commitHash = gitService.getLastCommitHash(programmingExercise.getVcsTestRepositoryUrl()).getName();
            }
            catch (EntityNotFoundException ex) {
                throw new IllegalStateException("Last commit hash for test repository of programming exercise with id " + programmingExercise.getId() + " could not be retrieved");
            }
        }
        return createSubmissionWithCommitHashAndSubmissionType(solutionParticipation, commitHash, SubmissionType.TEST);
    }

    private ProgrammingSubmission createSubmissionWithCommitHashAndSubmissionType(ProgrammingExerciseParticipation participation, String commitHash,
            SubmissionType submissionType) {
        // Make sure that the new submission has the submission date of now
        ProgrammingSubmission newSubmission = (ProgrammingSubmission) new ProgrammingSubmission().commitHash(commitHash).submitted(true).submissionDate(ZonedDateTime.now())
                .type(submissionType);
        newSubmission.setParticipation((Participation) participation);
        return programmingSubmissionRepository.saveAndFlush(newSubmission);
    }

    /**
     * Trigger a CI build for each submission & notify each user of the participation
     * Note: Instead of triggering all builds at the same time, we execute the builds in batches to not overload the CIS system (this has to be handled in the invoking method)
     *
     * Note: This call "resumes the exercise", i.e. re-creates the build plan if the build plan was already cleaned before
     *
     * @param participation the participation for which we create a new submission and new result
     */
    public void triggerBuild(ProgrammingExerciseStudentParticipation participation) {
        Optional<ProgrammingSubmission> submission = participation.findLatestSubmission();
        // we only need to trigger the build if the student actually already made a submission, otherwise this is not needed
        if (submission.isPresent()) {
            try {
                if (participation.getBuildPlanId() == null || !participation.getInitializationState().hasCompletedState(InitializationState.INITIALIZED)) {
                    // in this case, we first have to resume the exercise: this includes that we again set up the build plan properly before we trigger it
                    participationService.resumeProgrammingExercise(participation);
                    // Note: in this case we do not need an empty commit: when we trigger the build manually (below), subsequent commits will work correctly
                }
                continuousIntegrationService.get().triggerBuild(participation);
                // TODO: this is a workaround, in the future we should use the participation to notify the client and avoid using the submission
                this.notifyUserAboutSubmission(submission.get());
            }
            catch (Exception e) {
                log.error("Trigger build failed for {} with the exception {}", participation.getBuildPlanId(), e.getMessage());
                BuildTriggerWebsocketError error = new BuildTriggerWebsocketError(e.getMessage(), participation.getId());
                notifyUserAboutSubmissionError(participation, error);
            }
        }
    }

    /**
     * Triggers a build on the CI system and sends a websocket message to the user about the new submission and
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
                participationService.resumeProgrammingExercise((ProgrammingExerciseStudentParticipation) programmingExerciseParticipation);
                // Note: in this case we do not need an empty commit: when we trigger the build manually (below), subsequent commits will work correctly
            }
            continuousIntegrationService.get().triggerBuild(programmingExerciseParticipation);
            notifyUserAboutSubmission(submission);
        }
        catch (Exception e) {
            log.error("Trigger build failed for {} with the exception {}", programmingExerciseParticipation.getBuildPlanId(), e.getMessage());
            BuildTriggerWebsocketError error = new BuildTriggerWebsocketError(e.getMessage(), submission.getParticipation().getId());
            notifyUserAboutSubmissionError(submission, error);
        }
    }

    /**
     * Trigger the template repository build with the given commitHash.
     *
     * @param programmingExerciseId is used to retrieve the template participation.
     * @param commitHash            the unique hash code of the git repository identifying the submission, will be used for the created submission.
     * @param submissionType        will be used for the created submission.
     * @throws EntityNotFoundException if the programming exercise has no template participation (edge case).
     */
    public void triggerTemplateBuildAndNotifyUser(long programmingExerciseId, String commitHash, SubmissionType submissionType) throws EntityNotFoundException {
        TemplateProgrammingExerciseParticipation templateParticipation = programmingExerciseParticipationService
                .findTemplateParticipationByProgrammingExerciseId(programmingExerciseId);
        // If for some reason the programming exercise does not have a template participation, we can only log and abort.
        createSubmissionTriggerBuildAndNotifyUser(templateParticipation, commitHash, submissionType);
    }

    /**
     * Creates a submission with the given type and commitHash for the provided participation.
     * Will notify the user about occurring errors when trying to trigger the build.
     *
     * @param participation  for which to create the submission.
     * @param commitHash     the unique hash code of the git repository identifying the submission,to assign to the submission.
     * @param submissionType to assign to the submission.
     */
    private void createSubmissionTriggerBuildAndNotifyUser(ProgrammingExerciseParticipation participation, String commitHash, SubmissionType submissionType) {
        ProgrammingSubmission submission = createSubmissionWithCommitHashAndSubmissionType(participation, commitHash, submissionType);
        try {
            continuousIntegrationService.get().triggerBuild((ProgrammingExerciseParticipation) submission.getParticipation());
            notifyUserAboutSubmission(submission);
        }
        catch (Exception e) {
            BuildTriggerWebsocketError error = new BuildTriggerWebsocketError(e.getMessage(), submission.getParticipation().getId());
            notifyUserAboutSubmissionError(submission, error);
        }
    }

    /**
     * Executes setTestCasesChanged with testCasesChanged = true, also triggers template and solution build.
     * This method should be used if the solution participation would otherwise not be built.
     *
     * @param programmingExerciseId ProgrammingExercise id
     * @throws EntityNotFoundException if there is no programming exercise for the given id.
     */
    public void setTestCasesChangedAndTriggerTestCaseUpdate(long programmingExerciseId) throws EntityNotFoundException {
        setTestCasesChanged(programmingExerciseId, true);
        var programmingExercise = programmingExerciseRepository.findWithTemplateAndSolutionParticipationById(programmingExerciseId).get();

        try {
            continuousIntegrationService.get().triggerBuild(programmingExercise.getSolutionParticipation());
            continuousIntegrationService.get().triggerBuild(programmingExercise.getTemplateParticipation());
        }
        catch (ContinuousIntegrationException ex) {
            log.error("Could not trigger build for solution repository after test case update for programming exercise with id {}", programmingExerciseId);
        }
    }

    /**
     * see the description below
     *
     * @param programmingExerciseId id of a ProgrammingExercise.
     * @param testCasesChanged      set to true to mark the programming exercise as dirty.
     * @throws EntityNotFoundException if the programming exercise does not exist.
     */
    public void setTestCasesChanged(long programmingExerciseId, boolean testCasesChanged) throws EntityNotFoundException {
        var programmingExercise = programmingExerciseRepository.findByIdElseThrow(programmingExerciseId);
        setTestCasesChanged(programmingExercise, testCasesChanged);
    }

    /**
     * If testCasesChanged = true, this marks the programming exercise as dirty, meaning that its test cases were changed and the student submissions should be built & tested.
     * This method also sends out a notification to the client if testCasesChanged = true.
     * In case the testCaseChanged value is the same for the programming exercise or the programming exercise is not released or has no results, the method will return immediately.
     *
     * @param programmingExercise a ProgrammingExercise.
     * @param testCasesChanged    set to true to mark the programming exercise as dirty.
     * @throws EntityNotFoundException if the programming exercise does not exist.
     */
    public void setTestCasesChanged(ProgrammingExercise programmingExercise, boolean testCasesChanged) throws EntityNotFoundException {

        // If the flag testCasesChanged has not changed, we can stop the execution
        // Also, if the programming exercise has no results yet, there is no point in setting test cases changed to *true*.
        // It is only relevant when there are student submissions that should get an updated result.

        boolean resultsExist = resultRepository.existsByParticipation_ExerciseId(programmingExercise.getId());

        if (testCasesChanged == programmingExercise.getTestCasesChanged() || (!resultsExist && testCasesChanged)) {
            return;
        }
        programmingExercise.setTestCasesChanged(testCasesChanged);
        ProgrammingExercise updatedProgrammingExercise = programmingExerciseRepository.save(programmingExercise);
        // Send a websocket message about the new state to the client.
        websocketMessagingService.sendMessage(getProgrammingExerciseTestCaseChangedTopic(updatedProgrammingExercise.getId()), testCasesChanged);
        // Send a notification to the client to inform the instructor about the test case update.
        if (testCasesChanged) {
            groupNotificationService.notifyEditorAndInstructorGroupsAboutChangedTestCasesForProgrammingExercise(updatedProgrammingExercise);
        }
        else {
            groupNotificationService.notifyEditorAndInstructorGroupAboutExerciseUpdate(updatedProgrammingExercise, TEST_CASES_CHANGED_RUN_COMPLETED_NOTIFICATION);
        }
    }

    private String getProgrammingExerciseTestCaseChangedTopic(Long programmingExerciseId) {
        return "/topic/programming-exercises/" + programmingExerciseId + "/test-cases-changed";
    }

    private String getProgrammingExerciseAllExerciseBuildsTriggeredTopic(Long programmingExerciseId) {
        return "/topic/programming-exercises/" + programmingExerciseId + "/all-builds-triggered";
    }

    /**
     * Notify user on a new programming submission.
     *
     * @param submission ProgrammingSubmission
     */
    public void notifyUserAboutSubmission(ProgrammingSubmission submission) {
        if (submission.getParticipation() instanceof StudentParticipation studentParticipation) {
            // no need to send all exercise details here
            submission.getParticipation().setExercise(null);
            studentParticipation.getStudents().forEach(user -> messagingTemplate.convertAndSendToUser(user.getLogin(), NEW_SUBMISSION_TOPIC, submission));
        }

        if (submission.getParticipation() != null && submission.getParticipation().getExercise() != null) {
            var topicDestination = getExerciseTopicForTAAndAbove(submission.getParticipation().getExercise().getId());
            messagingTemplate.convertAndSend(topicDestination, submission);
        }
    }

    private void notifyUserAboutSubmissionError(ProgrammingSubmission submission, BuildTriggerWebsocketError error) {
        notifyUserAboutSubmissionError(submission.getParticipation(), error);
    }

    private void notifyUserAboutSubmissionError(Participation participation, BuildTriggerWebsocketError error) {
        if (participation instanceof StudentParticipation studentParticipation) {
            studentParticipation.getStudents().forEach(user -> messagingTemplate.convertAndSendToUser(user.getLogin(), NEW_SUBMISSION_TOPIC, error));
        }

        if (participation != null && participation.getExercise() != null) {
            messagingTemplate.convertAndSend(getExerciseTopicForTAAndAbove(participation.getExercise().getId()), error);
        }
    }

    private String getExerciseTopicForTAAndAbove(long exerciseId) {
        return EXERCISE_TOPIC_ROOT + exerciseId + PROGRAMMING_SUBMISSION_TOPIC;
    }

    /**
     * Given an exercise id and a tutor id, it returns all the programming submissions where the tutor has assessed a result
     *
     * Exam mode:
     * The query that returns the participations returns the contained submissions in a particular way:
     * Due to hibernate, the result list has null values at all places where a result was assessed by another tutor.
     * At the beginning of the list remain all the automatic results without assessor.
     * Then filtering out all submissions which have no result at the place of the correctionround leaves us with the submissions we are interested in.
     * Before returning the submissions we strip away all automatic results, to be able to correctly display them in the client.
     *
     * Not exam mode:
     * In this case the query that returns the participations returns the contained submissions in a different way:
     * Here hibernate sets all automatic results to null, therefore we must filter all those out. This way the client can access the submissions'
     * single result.
     *
     * @param exerciseId      - the id of the exercise we are looking for
     * @param correctionRound - the correctionRound for which the submissions should be fetched for
     * @param tutor           - the tutor we are interested in
     * @param examMode        - flag should be set to ignore the test run submissions
     * @return a list of programming submissions
     */
    public List<ProgrammingSubmission> getAllProgrammingSubmissionsAssessedByTutorForCorrectionRoundAndExercise(long exerciseId, User tutor, boolean examMode,
            int correctionRound) {
        List<Submission> submissions;
        if (examMode) {
            var participations = this.studentParticipationRepository.findAllByParticipationExerciseIdAndResultAssessorAndCorrectionRoundIgnoreTestRuns(exerciseId, tutor);
            // Latest submission might be illegal
            submissions = participations.stream().map(StudentParticipation::findLatestLegalOrIllegalSubmission).filter(Optional::isPresent).map(Optional::get)
                    // filter out the submissions that don't have a result (but a null value) for the correctionRound
                    .filter(submission -> submission.hasResultForCorrectionRound(correctionRound)).collect(toList());
        }
        else {
            submissions = this.submissionRepository.findAllByParticipationExerciseIdAndResultAssessor(exerciseId, tutor);
            // automatic results are null in the received results list. We need to filter them out for the client to display the dashboard correctly
            submissions.forEach(Submission::removeNullResults);
        }
        // strip away all automatic results from the submissions list
        submissions.forEach(Submission::removeAutomaticResults);
        submissions.forEach(submission -> {
            var latestResult = submission.getLatestResult();
            if (latestResult != null) {
                latestResult.setSubmission(null);
            }
        });
        List<ProgrammingSubmission> programmingSubmissions = submissions.stream().map(submission -> (ProgrammingSubmission) submission).collect(toList());
        // In Exam-Mode, the Submissions are retrieved from the studentParticipationRepository, for which the Set<Submission> is appended
        // In non-Exam Mode, the Submissions are retrieved from the submissionRepository, for which no Set<submission> is appended
        return removeExerciseAndSubmissionSet(programmingSubmissions, examMode);
    }

    /**
     * Given an exerciseId, returns all the programming submissions for that exercise, including their results. Submissions can be filtered to include only already submitted
     * submissions
     *
     * @param exerciseId    - the id of the exercise we are interested into
     * @param submittedOnly - if true, it returns only submission with submitted flag set to true
     * @param examMode      - set flag to ignore test run submissions for exam exercises
     * @return a list of programming submissions for the given exercise id
     */
    public List<ProgrammingSubmission> getProgrammingSubmissions(long exerciseId, boolean submittedOnly, boolean examMode) {
        List<StudentParticipation> participations;
        if (examMode) {
            participations = studentParticipationRepository.findAllWithEagerSubmissionsAndEagerResultsAndEagerAssessorByExerciseIdIgnoreTestRuns(exerciseId);
        }
        else {
            participations = studentParticipationRepository.findAllWithEagerSubmissionsAndEagerResultsAndEagerAssessorByExerciseId(exerciseId);
        }
        List<ProgrammingSubmission> programmingSubmissions = new ArrayList<>();
        participations.stream().peek(participation -> participation.getExercise().setStudentParticipations(null)).map(StudentParticipation::findLatestLegalOrIllegalSubmission)
                // filter out non submitted submissions if the flag is set to true
                .filter(optionalSubmission -> optionalSubmission.isPresent() && (!submittedOnly || optionalSubmission.get().isSubmitted()))
                .forEach(optionalSubmission -> programmingSubmissions.add((ProgrammingSubmission) optionalSubmission.get()));
        return removeExerciseAndSubmissionSet(programmingSubmissions, true);
    }

    /**
     * Given a List of ProgrammingSubmissions, this method will remove the attribute participation.exercise.
     * If removeSubmissionSet = true, also the Set participation.submissions is removed. The number of submissions will be
     * stored in the attribute participation.submissionCount instead of being determined by the size of the set of all submissions.
     * This method is intended to reduce the amount of data transferred to the client.
     * @param programmingSubmissionList - a List with all ProgrammingSubmissions to be modified
     * @param removeSubmissionSet - option to also remove the SubmissionSet from the ProgrammingSubmssion
     * @return a List with ProgrammingSubmissions and removed attributes
     */
    private List<ProgrammingSubmission> removeExerciseAndSubmissionSet(List<ProgrammingSubmission> programmingSubmissionList, boolean removeSubmissionSet) {
        programmingSubmissionList.forEach(programmingSubmission -> {
            if (programmingSubmission.getParticipation() != null) {
                Participation participation = programmingSubmission.getParticipation();
                participation.setExercise(null);
                if (removeSubmissionSet && participation.getSubmissions() != null) {
                    // Only remove the Submissions and store them in submissionsCount, if the Set<Submissions> is present.
                    participation.setSubmissionCount(participation.getSubmissions().size());
                    participation.setSubmissions(null);
                }
            }
        });
        return programmingSubmissionList;
    }

    /**
     * Given an exercise id, find a random programming submission for that exercise which still doesn't have any manual result. No manual result means that no user has started an
     * assessment for the corresponding submission yet.
     * For exam exercises we should also remove the test run participations as these should not be graded by the tutors.
     *
     * @param programmingExercise the exercise for which we want to retrieve a submission without manual result
     * @param correctionRound     - the correction round we want our submission to have results for
     * @param examMode            flag to determine if test runs should be removed. This should be set to true for exam exercises
     * @return a programmingSubmission without any manual result or an empty Optional if no submission without manual result could be found
     */
    public Optional<ProgrammingSubmission> getRandomProgrammingSubmissionEligibleForNewAssessment(ProgrammingExercise programmingExercise, boolean examMode, int correctionRound) {
        var submissionWithoutResult = super.getRandomSubmissionEligibleForNewAssessment(programmingExercise, examMode, correctionRound);
        if (submissionWithoutResult.isPresent()) {
            ProgrammingSubmission programmingSubmission = (ProgrammingSubmission) submissionWithoutResult.get();
            return Optional.of(programmingSubmission);
        }
        return Optional.empty();
    }

    /**
     * Get the programming submission with the given ID from the database and lock the submission to prevent other tutors from receiving and assessing it.
     *
     * @param submissionId    the id of the programming submission
     * @param correctionRound the correctionRound of the programming submission
     * @return the locked programming submission
     */
    public ProgrammingSubmission lockAndGetProgrammingSubmission(Long submissionId, int correctionRound) {
        ProgrammingSubmission programmingSubmission = findOneWithEagerResultsFeedbacksAssessor(submissionId);
        var manualResult = lockSubmission(programmingSubmission, correctionRound);
        return (ProgrammingSubmission) manualResult.getSubmission();
    }

    /**
     * Get a programming submission of the given exercise that still needs to be assessed and lock the submission to prevent other tutors from receiving and assessing it.
     *
     * @param exercise        the exercise the submission should belong to
     * @param correctionRound - the correction round we want our submission to have results for
     * @return a locked programming submission that needs an assessment
     */
    public ProgrammingSubmission lockAndGetProgrammingSubmissionWithoutResult(ProgrammingExercise exercise, int correctionRound) {
        ProgrammingSubmission programmingSubmission = getRandomProgrammingSubmissionEligibleForNewAssessment(exercise, exercise.isExamExercise(), correctionRound)
                .orElseThrow(() -> new EntityNotFoundException("Programming submission for exercise " + exercise.getId() + " could not be found"));
        Result newManualResult = lockSubmission(programmingSubmission, correctionRound);
        return (ProgrammingSubmission) newManualResult.getSubmission();
    }

    // TODO SE: explain in what context this method is even called

    /**
     * Locks the programmingSubmission submission. If the submission only has automatic results, and no manual,
     * create a new manual result. In the second correction round add a second manual result to the submission
     *
     * @param submission      the submission to lock
     * @param correctionRound the correction round for the assessment
     * @return the result that is locked with the current user
     */
    @Override
    // TODO: why do we override this method and why do we not try to reuse the method in the super class?
    protected Result lockSubmission(Submission submission, int correctionRound) {
        Optional<Result> optionalExistingResult;
        if (correctionRound == 0 && submission.getLatestResult() != null && AssessmentType.AUTOMATIC == submission.getLatestResult().getAssessmentType()) {
            optionalExistingResult = Optional.of(submission.getLatestResult());
        }
        else if (correctionRound == 0 && submission.getLatestResult() == null) {
            // Older programming Exercises have only one result in each submission. One submission for the automatic result, another one for the manual one.
            // When the assessment of such a submission is cancelled, this leaves behind a programming-submission without any results.
            // We still want to be able to assess the result-less submission again, so we need to avoid the below else branch, and the following out of bounds Exception.
            // New automatic results can be easily created by using the "trigger all" feature
            optionalExistingResult = Optional.empty();
        }
        else {
            optionalExistingResult = Optional.ofNullable(submission.getResultForCorrectionRound(correctionRound - 1));
        }
        List<Feedback> automaticFeedbacks = new ArrayList<>();
        if (optionalExistingResult.isPresent()) {
            automaticFeedbacks = optionalExistingResult.get().getFeedbacks().stream().map(Feedback::copyFeedback).collect(Collectors.toList());
        }
        // Create a new result (manual result) and try to reuse the existing submission with the latest commit hash
        ProgrammingSubmission existingSubmission = getOrCreateSubmissionWithLastCommitHashForParticipation((ProgrammingExerciseStudentParticipation) submission.getParticipation(),
                SubmissionType.MANUAL);
        Result newResult = saveNewEmptyResult(existingSubmission);
        newResult.setAssessor(userRepository.getUser());
        newResult.setAssessmentType(AssessmentType.SEMI_AUTOMATIC);
        // Copy automatic feedbacks into the manual result
        for (Feedback feedback : automaticFeedbacks) {
            feedback = feedbackRepository.save(feedback);
            feedback.setResult(newResult);
        }
        newResult.setFeedbacks(automaticFeedbacks);
        if (optionalExistingResult.isPresent()) {
            newResult.setResultString(optionalExistingResult.get().getResultString());
        }
        // Workaround to prevent the assessor turning into a proxy object after saving
        var assessor = newResult.getAssessor();
        newResult = resultRepository.save(newResult);
        newResult.setAssessor(assessor);
        log.debug("Assessment locked with result id: {} for assessor: {}", newResult.getId(), newResult.getAssessor().getName());
        // Make sure that submission is set back after saving
        newResult.setSubmission(existingSubmission);
        return newResult;
    }

    private ProgrammingSubmission findOneWithEagerResultsFeedbacksAssessor(Long submissionId) {
        return programmingSubmissionRepository.findWithEagerResultsFeedbacksAssessorById(submissionId)
                .orElseThrow(() -> new EntityNotFoundException("Programming submission with id \"" + submissionId + "\" does not exist"));
    }
}
