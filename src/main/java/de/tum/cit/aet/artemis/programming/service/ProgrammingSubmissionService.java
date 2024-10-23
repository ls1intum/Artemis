package de.tum.cit.aet.artemis.programming.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.core.config.Constants.PROGRAMMING_GRACE_PERIOD_SECONDS;
import static de.tum.cit.aet.artemis.core.config.Constants.SETUP_COMMIT_MESSAGE;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import jakarta.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.repository.ComplaintRepository;
import de.tum.cit.aet.artemis.assessment.repository.FeedbackRepository;
import de.tum.cit.aet.artemis.assessment.repository.ResultRepository;
import de.tum.cit.aet.artemis.assessment.service.FeedbackService;
import de.tum.cit.aet.artemis.athena.service.AthenaSubmissionSelectionService;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.ContinuousIntegrationException;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.exception.VersionControlException;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.exam.service.ExamDateService;
import de.tum.cit.aet.artemis.exam.service.ExamSubmissionService;
import de.tum.cit.aet.artemis.exercise.api.ExerciseDateApi;
import de.tum.cit.aet.artemis.exercise.domain.InitializationState;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.SubmissionType;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.repository.ParticipationRepository;
import de.tum.cit.aet.artemis.exercise.repository.StudentParticipationRepository;
import de.tum.cit.aet.artemis.exercise.repository.SubmissionRepository;
import de.tum.cit.aet.artemis.exercise.service.ParticipationAuthorizationCheckService;
import de.tum.cit.aet.artemis.exercise.service.ParticipationService;
import de.tum.cit.aet.artemis.exercise.service.SubmissionService;
import de.tum.cit.aet.artemis.programming.domain.AbstractBaseProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.Commit;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.domain.SolutionProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.TemplateProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.submissionpolicy.LockRepositoryPolicy;
import de.tum.cit.aet.artemis.programming.domain.submissionpolicy.SubmissionPolicy;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseStudentParticipationRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingSubmissionRepository;
import de.tum.cit.aet.artemis.programming.repository.SubmissionPolicyRepository;
import de.tum.cit.aet.artemis.programming.service.ci.ContinuousIntegrationTriggerService;
import de.tum.cit.aet.artemis.programming.service.hestia.ProgrammingExerciseGitDiffReportService;
import de.tum.cit.aet.artemis.programming.service.vcs.VersionControlService;

// TODO: this class has too many dependencies to other services. We should reduce this
@Profile(PROFILE_CORE)
@Service
public class ProgrammingSubmissionService extends SubmissionService {

    private static final Logger log = LoggerFactory.getLogger(ProgrammingSubmissionService.class);

    @Value("${artemis.git.name}")
    private String artemisGitName;

    @Value("${artemis.git.email}")
    private String artemisGitEmail;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final ProgrammingSubmissionRepository programmingSubmissionRepository;

    private final ProgrammingMessagingService programmingMessagingService;

    private final ProgrammingExerciseParticipationService programmingExerciseParticipationService;

    private final ExamSubmissionService examSubmissionService;

    private final Optional<VersionControlService> versionControlService;

    private final Optional<ContinuousIntegrationTriggerService> continuousIntegrationTriggerService;

    private final GitService gitService;

    private final ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository;

    private final ProgrammingExerciseGitDiffReportService programmingExerciseGitDiffReportService;

    private final ParticipationAuthorizationCheckService participationAuthCheckService;

    private final SubmissionPolicyRepository submissionPolicyRepository;

    public ProgrammingSubmissionService(ProgrammingSubmissionRepository programmingSubmissionRepository, ProgrammingExerciseRepository programmingExerciseRepository,
            SubmissionRepository submissionRepository, UserRepository userRepository, AuthorizationCheckService authCheckService,
            ProgrammingMessagingService programmingMessagingService, Optional<VersionControlService> versionControlService, ResultRepository resultRepository,
            Optional<ContinuousIntegrationTriggerService> continuousIntegrationTriggerService, ParticipationService participationService,
            ProgrammingExerciseParticipationService programmingExerciseParticipationService, ExamSubmissionService examSubmissionService, GitService gitService,
            StudentParticipationRepository studentParticipationRepository, FeedbackRepository feedbackRepository, ExamDateService examDateService, ExerciseDateApi exerciseDateApi,
            CourseRepository courseRepository, ParticipationRepository participationRepository,
            ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository, ComplaintRepository complaintRepository,
            ProgrammingExerciseGitDiffReportService programmingExerciseGitDiffReportService, ParticipationAuthorizationCheckService participationAuthCheckService,
            FeedbackService feedbackService, SubmissionPolicyRepository submissionPolicyRepository, Optional<AthenaSubmissionSelectionService> athenaSubmissionSelectionService) {
        super(submissionRepository, userRepository, authCheckService, resultRepository, studentParticipationRepository, participationService, feedbackRepository, examDateService,
                exerciseDateApi, courseRepository, participationRepository, complaintRepository, feedbackService, athenaSubmissionSelectionService);
        this.programmingSubmissionRepository = programmingSubmissionRepository;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.programmingMessagingService = programmingMessagingService;
        this.versionControlService = versionControlService;
        this.continuousIntegrationTriggerService = continuousIntegrationTriggerService;
        this.programmingExerciseParticipationService = programmingExerciseParticipationService;
        this.examSubmissionService = examSubmissionService;
        this.gitService = gitService;
        this.programmingExerciseStudentParticipationRepository = programmingExerciseStudentParticipationRepository;
        this.programmingExerciseGitDiffReportService = programmingExerciseGitDiffReportService;
        this.participationAuthCheckService = participationAuthCheckService;
        this.submissionPolicyRepository = submissionPolicyRepository;
    }

    /**
     * This method gets called if a new commit was pushed to the VCS
     *
     * @param participation The Participation, where the push happened
     * @param requestBody   the body of the post request by the VCS.
     * @return the ProgrammingSubmission for the last commitHash
     * @throws EntityNotFoundException  if no ProgrammingExerciseParticipation could be found
     * @throws IllegalStateException    if a ProgrammingSubmission already exists
     * @throws IllegalArgumentException if the Commit hash could not be parsed for submission from participation
     * @throws VersionControlException  if the commit belongs to the wrong branch (i.e. not the default branch for the participation).
     */
    public ProgrammingSubmission processNewProgrammingSubmission(ProgrammingExerciseParticipation participation, Object requestBody)
            throws EntityNotFoundException, IllegalStateException, IllegalArgumentException {
        // Note: the following line is intentionally at the top of the method to get the most accurate submission date
        var existingSubmissionCount = participation.getSubmissions().size();
        ZonedDateTime submissionDate = ZonedDateTime.now();
        VersionControlService versionControl = versionControlService.orElseThrow();

        // if the commit is made by the Artemis user and contains the commit message "Setup" (use a constant to determine this), we should ignore this
        // and we should not create a new submission here
        Commit commit;
        try {
            // we can find this out by looking into the requestBody
            // if the branch is different from main, throw an IllegalArgumentException, but make sure the REST call still returns 200
            commit = versionControl.getLastCommitDetails(requestBody);
            log.info("NotifyPush invoked due to the commit {} by {} with {} in branch {}", commit.commitHash(), commit.authorName(), commit.authorEmail(), commit.branch());
        }
        catch (Exception ex) {
            log.error("Commit could not be parsed for submission from participation {}", participation, ex);
            throw new IllegalArgumentException(ex);
        }

        String branch = versionControl.getOrRetrieveBranchOfParticipation(participation);
        if (commit.branch() != null && !commit.branch().equalsIgnoreCase(branch)) {
            // if the commit was made in a branch different from the default, ignore this
            throw new VersionControlException(
                    "Submission for participation id " + participation.getId() + " in branch " + commit.branch() + " will be ignored! Only the default branch is considered");
        }
        if (artemisGitName.equalsIgnoreCase(commit.authorName()) && artemisGitEmail.equalsIgnoreCase(commit.authorEmail()) && SETUP_COMMIT_MESSAGE.equals(commit.message())) {
            // if the commit was made by Artemis and the message is "Setup" (this means it is an empty setup commit), we ignore this as well and do not create a submission!
            throw new IllegalStateException("Submission for participation id " + participation.getId() + " based on an empty setup commit by Artemis will be ignored!");
        }

        if (participation instanceof ProgrammingExerciseStudentParticipation programmingExerciseStudentParticipation
                && (participation.getBuildPlanId() == null || !participation.getInitializationState().hasCompletedState(InitializationState.INITIALIZED))) {
            // the build plan was deleted before, e.g. due to cleanup, therefore we need to reactivate the build plan by resuming the participation
            // This is needed as a request using a custom query is made using the ProgrammingExerciseRepository, but the user is not authenticated
            // as the VCS-server performs the request
            SecurityUtils.setAuthorizationObject();

            participationService.resumeProgrammingExercise(programmingExerciseStudentParticipation);
            // Note: in this case we do not need an empty commit: when we trigger the build manually (below), subsequent commits will work correctly
        }

        // TODO: there might be cases in which Artemis should NOT trigger the build
        try {
            continuousIntegrationTriggerService.orElseThrow().triggerBuild(participation, commit.commitHash(), null);
        }
        catch (ContinuousIntegrationException ex) {
            // TODO: This case is currently not handled. The correct handling would be creating the submission and informing the user that the build trigger failed.
        }

        // There can't be two submissions for the same participation and commitHash!
        ProgrammingSubmission programmingSubmission = programmingSubmissionRepository
                .findFirstByParticipationIdAndCommitHashOrderByIdDescWithFeedbacksAndTeamStudents(participation.getId(), commit.commitHash());
        if (programmingSubmission != null) {
            throw new IllegalStateException("Submission for participation id " + participation.getId() + " and commitHash " + commit.commitHash() + " already exists!");
        }

        programmingSubmission = new ProgrammingSubmission();
        programmingSubmission.setCommitHash(commit.commitHash());
        log.info("Create new programmingSubmission with commitHash: {} for participation {}", commit.commitHash(), participation.getId());

        programmingSubmission.setSubmitted(true);
        programmingSubmission.setSubmissionDate(submissionDate);
        programmingSubmission.setType(SubmissionType.MANUAL);

        var programmingExercise = participation.getProgrammingExercise();
        var submissionPolicy = submissionPolicyRepository.findByProgrammingExerciseId(programmingExercise.getId());

        // Students are not allowed to submit a programming exercise after the due date, if this happens we set the Submission to ILLEGAL
        checkForIllegalSubmission(participation, programmingSubmission, submissionPolicy);

        participation.addSubmission(programmingSubmission);
        programmingSubmission = programmingSubmissionRepository.save(programmingSubmission);
        updateGitDiffReportForTemplateOrSolutionParticipation(participation);

        // NOTE: this might an important information if a lock submission policy of the corresponding programming exercise is active
        programmingSubmission.getParticipation().setSubmissionCount(existingSubmissionCount + 1);

        // NOTE: in case a submission policy is set for the corresponding programming exercise, set the locked value of the participation properly, in particular for exams
        if (participation instanceof ProgrammingExerciseStudentParticipation programmingExerciseStudentParticipation && submissionPolicy != null && submissionPolicy.isActive()
                && submissionPolicy instanceof LockRepositoryPolicy) {
            // we set the participation to locked when the new submission count is at least as high as the submission limit
            programmingExerciseStudentParticipation.setLocked(programmingExerciseStudentParticipation.getSubmissionCount() >= submissionPolicy.getSubmissionLimit());
        }

        // NOTE: we don't need to save the participation here, this might lead to concurrency problems when doing the empty commit during resume exercise!
        return programmingSubmission;
    }

    /**
     * Update the git-diff of the programming exercise when the push was to a solution or template repository
     *
     * @param programmingExerciseParticipation The participation
     */
    private void updateGitDiffReportForTemplateOrSolutionParticipation(ProgrammingExerciseParticipation programmingExerciseParticipation) {
        if (programmingExerciseParticipation instanceof TemplateProgrammingExerciseParticipation
                || programmingExerciseParticipation instanceof SolutionProgrammingExerciseParticipation) {
            try {
                programmingExerciseGitDiffReportService.updateReport(programmingExerciseParticipation.getProgrammingExercise());
            }
            catch (Exception e) {
                log.error("Unable to update git-diff for programming exercise {}", programmingExerciseParticipation.getProgrammingExercise().getId(), e);
            }
        }
    }

    /**
     * We check if a submission for a programming exercise is after the individual end date and a student is not allowed to submit anymore.
     * If this is the case, the submission is set to {@link SubmissionType#ILLEGAL}.
     *
     * @param programmingExerciseParticipation current participation of the exam exercise
     * @param programmingSubmission            new created submission of the repository commit
     */
    private void checkForIllegalSubmission(ProgrammingExerciseParticipation programmingExerciseParticipation, ProgrammingSubmission programmingSubmission,
            SubmissionPolicy submissionPolicy) {
        ProgrammingExercise programmingExercise = programmingExerciseParticipation.getProgrammingExercise();
        // Students are not allowed to submit a programming exercise after the due date, if this happens we set the Submission to ILLEGAL
        if (!(programmingExerciseParticipation instanceof ProgrammingExerciseStudentParticipation studentParticipation)) {
            return;
        }
        var optionalStudent = studentParticipation.getStudent();
        var optionalStudentWithGroups = optionalStudent.flatMap(student -> userRepository.findOneWithGroupsAndAuthoritiesByLogin(student.getLogin()));
        if (optionalStudentWithGroups.isEmpty()) {
            return;
        }
        User student = optionalStudentWithGroups.get();

        if (!isAllowedToSubmit(studentParticipation, student, programmingSubmission)) {
            final String message = ("The student %s illegally submitted code after the allowed individual due date (including the grace period) in the participation %d for the "
                    + "programming exercise \"%s\"").formatted(student.getLogin(), programmingExerciseParticipation.getId(), programmingExercise.getTitle());
            programmingSubmission.setType(SubmissionType.ILLEGAL);
            programmingMessagingService.notifyInstructorGroupAboutIllegalSubmissionsForExercise(programmingExercise, message);
            log.warn(message);
            return;
        }

        // we include submission policies here: if the student (for whatever reason) has more submission than allowed attempts, the submission would be illegal
        if (exceedsSubmissionPolicy(studentParticipation, submissionPolicy)) {
            final String message = "The student %s illegally submitted code after the submission policy lock limit %d in the participation %d for the programming exercise \"%s\""
                    .formatted(student.getLogin(), submissionPolicy.getSubmissionLimit(), programmingExerciseParticipation.getId(), programmingExercise.getTitle());
            programmingSubmission.setType(SubmissionType.ILLEGAL);
            programmingMessagingService.notifyInstructorGroupAboutIllegalSubmissionsForExercise(programmingExercise, message);
            log.warn(message);
        }
    }

    private boolean exceedsSubmissionPolicy(ProgrammingExerciseParticipation programmingExerciseParticipation, SubmissionPolicy submissionPolicy) {
        if (programmingExerciseParticipation instanceof ProgrammingExerciseStudentParticipation && submissionPolicy != null && submissionPolicy.isActive()
                && submissionPolicy instanceof LockRepositoryPolicy) {
            return programmingExerciseParticipation.getSubmissions().size() > submissionPolicy.getSubmissionLimit();
        }
        return false;
    }

    private boolean isAllowedToSubmit(ProgrammingExerciseStudentParticipation participation, User studentWithGroups, ProgrammingSubmission programmingSubmission) {
        ProgrammingExercise exercise = participation.getProgrammingExercise();
        if (exercise.isExamExercise()) {
            return examSubmissionService.isAllowedToSubmitDuringExam(exercise, studentWithGroups, true);
        }
        return isAllowedToSubmitForCourseExercise(participation, programmingSubmission);
    }

    private boolean isAllowedToSubmitForCourseExercise(ProgrammingExerciseStudentParticipation participation, ProgrammingSubmission programmingSubmission) {
        var dueDate = exerciseDateApi.getDueDate(participation);
        // Without a due date or in the practice mode, the student can always submit
        if (dueDate.isEmpty() || participation.isPracticeMode()) {
            return true;
        }
        return dueDate.get().plusSeconds(PROGRAMMING_GRACE_PERIOD_SECONDS).isAfter(programmingSubmission.getSubmissionDate());
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
        participationAuthCheckService.checkCanAccessParticipationElseThrow(programmingExerciseParticipation);

        return findLatestPendingSubmissionForParticipation(participationId, filterGraded);
    }

    /**
     * For every student participation of a programming exercise, try to find a pending submission.
     *
     * @param programmingExerciseId for which to search pending submissions
     * @return a Map of {[participationId]: ProgrammingSubmission | null}. Will contain an entry for every student participation of the exercise and a submission object if a
     *         pending submission exists or null if not.
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
                ? programmingSubmissionRepository.findGradedByParticipationIdWithResultsOrderBySubmissionDateDesc(participationId, PageRequest.of(0, 1)).stream().findFirst()
                : programmingSubmissionRepository.findFirstByParticipationIdWithResultsOrderBySubmissionDateDesc(participationId);

        if (optionalSubmission.isEmpty() || optionalSubmission.get().getLatestResult() != null) {
            // This is not an error case, it is very likely that there is no pending submission for a participation.
            return Optional.empty();
        }
        return optionalSubmission;
    }

    /**
     * Create a submission with given submission type for the last commit hash of the given participation.
     * WARNING: The commitHash is used to map incoming results to submissions. Using this method could cause the result to have multiple fitting submissions.
     * <p>
     * See <a href="https://github.com/ls1intum/Artemis/pull/712#discussion_r314944129">discussion</a>
     * <p>
     * Worst case scenario when using this method:
     * 1) Student executes a submission, the build is created on CI system
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
        var programmingSubmission = programmingSubmissionRepository.findFirstByParticipationIdAndCommitHashOrderByIdDescWithFeedbacksAndTeamStudents(participation.getId(),
                lastCommitHash);
        // in case no programming submission is available, we create one
        return Objects.requireNonNullElseGet(programmingSubmission, () -> createSubmissionWithCommitHashAndSubmissionType(participation, lastCommitHash, submissionType));
    }

    private String getLastCommitHashForParticipation(ProgrammingExerciseParticipation participation) throws IllegalStateException {
        try {
            return gitService.getLastCommitHash(participation.getVcsRepositoryUri()).getName();
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
                commitHash = gitService.getLastCommitHash(programmingExercise.getVcsTestRepositoryUri()).getName();
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
     * Given an exercise id and a tutor id, it returns all the programming submissions where the tutor has assessed a result
     * <p>
     * Exam mode:
     * The query that returns the participations returns the contained submissions in a particular way:
     * Due to hibernate, the result list has null values at all places where a result was assessed by another tutor.
     * At the beginning of the list remain all the automatic results without assessor.
     * Then filtering out all submissions which have no result at the place of the correction round leaves us with the submissions we are interested in.
     * Before returning the submissions we strip away all automatic results, to be able to correctly display them in the client.
     * <p>
     * Not exam mode (i.e. course exercise):
     * In this case the query that returns the participations returns the contained submissions in a different way:
     * Here hibernate sets all automatic results to null, therefore we must filter all those out. This way the client can access the submissions'
     * single result.
     *
     * @param exerciseId      - the id of the exercise we are looking for
     * @param correctionRound - the correctionRound for which the submissions should be fetched for
     * @param tutor           - the tutor we are interested in
     * @param examMode        - flag should be set to ignore the test run submissions
     * @return an unmodifiable list of programming submissions
     */
    public List<ProgrammingSubmission> getAllProgrammingSubmissionsAssessedByTutorForCorrectionRoundAndExercise(long exerciseId, User tutor, boolean examMode,
            int correctionRound) {
        List<Submission> submissions;
        if (examMode) {
            var participations = studentParticipationRepository.findAllByParticipationExerciseIdAndResultAssessorAndCorrectionRoundIgnoreTestRuns(exerciseId, tutor);
            // Latest submission might be illegal
            submissions = participations.stream().map(StudentParticipation::findLatestLegalOrIllegalSubmission).filter(Optional::isPresent).map(Optional::get)
                    // filter out the submissions that don't have a result (but a null value) for the correctionRound
                    .filter(submission -> submission.hasResultForCorrectionRound(correctionRound)).toList();
        }
        else {
            submissions = submissionRepository.findAllByParticipationExerciseIdAndResultAssessorIgnoreTestRuns(exerciseId, tutor);
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
        List<ProgrammingSubmission> programmingSubmissions = submissions.stream().map(submission -> (ProgrammingSubmission) submission).toList();
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
     * @return a list of programming submissions for the given exercise id
     */
    public List<ProgrammingSubmission> getProgrammingSubmissions(long exerciseId, boolean submittedOnly) {
        List<StudentParticipation> participations = studentParticipationRepository.findAllWithEagerSubmissionsAndEagerResultsAndEagerAssessorByExerciseIdIgnoreTestRuns(exerciseId);
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
     *
     * @param programmingSubmissionList - a List with all ProgrammingSubmissions to be modified
     * @param removeSubmissionSet       - option to also remove the SubmissionSet from the ProgrammingSubmission
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
     * Return the next submission ordered by individual due date of its participation
     * without a manual result.
     * No manual result means that no user has started an assessment for the corresponding submission yet.
     * For exam exercises we should also remove the test run participations as these should not be graded by the tutors.
     *
     * @param programmingExercise the exercise for which we want to retrieve a submission without manual result
     * @param correctionRound     - the correction round we want our submission to have results for
     * @param examMode            flag to determine if test runs should be removed. This should be set to true for exam exercises
     * @return a programmingSubmission without any manual result or an empty Optional if no submission without manual result could be found
     */
    public Optional<ProgrammingSubmission> getNextAssessableSubmission(ProgrammingExercise programmingExercise, boolean examMode, int correctionRound) {
        var submissionWithoutResult = super.getNextAssessableSubmission(programmingExercise, examMode, correctionRound);
        if (submissionWithoutResult.isPresent()) {
            ProgrammingSubmission programmingSubmission = (ProgrammingSubmission) submissionWithoutResult.get();
            return Optional.of(programmingSubmission);
        }
        return Optional.empty();
    }

    /**
     * Given an exercise id, find a random programming submission for that exercise which still doesn't have any manual result. No manual result means that no user has started an
     * assessment for the corresponding submission yet.
     * For exam exercises we should also remove the test run participations as these should not be graded by the tutors.
     *
     * @param programmingExercise the exercise for which we want to retrieve a submission without manual result
     * @param skipAssessmentQueue flag to determine if the submission should be retrieved from the assessment queue
     * @param correctionRound     - the correction round we want our submission to have results for
     * @param examMode            flag to determine if test runs should be removed. This should be set to true for exam exercises
     * @return a programmingSubmission without any manual result or an empty Optional if no submission without manual result could be found
     */
    public Optional<ProgrammingSubmission> getRandomAssessableSubmission(ProgrammingExercise programmingExercise, boolean skipAssessmentQueue, boolean examMode,
            int correctionRound) {
        return super.getRandomAssessableSubmission(programmingExercise, skipAssessmentQueue, examMode, correctionRound,
                programmingSubmissionRepository::findWithEagerResultsAndFeedbacksById);
    }

    /**
     * Get the programming submission with the given ID from the database and lock the submission to prevent other tutors from receiving and assessing it.
     *
     * @param submissionId    the id of the programming submission
     * @param correctionRound the correctionRound of the programming submission
     * @return the locked programming submission
     */
    public ProgrammingSubmission lockAndGetProgrammingSubmission(Long submissionId, int correctionRound) {
        ProgrammingSubmission programmingSubmission = programmingSubmissionRepository.findByIdWithResultsFeedbacksAssessorTestCases(submissionId);
        var manualResult = lockSubmission(programmingSubmission, correctionRound);
        return (ProgrammingSubmission) manualResult.getSubmission();
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
    public Result lockSubmission(Submission submission, int correctionRound) {
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

        // Create a new manual result and try to reuse the existing submission with the latest commit hash
        ProgrammingSubmission existingSubmission = getOrCreateSubmissionWithLastCommitHashForParticipation((ProgrammingExerciseStudentParticipation) submission.getParticipation(),
                SubmissionType.MANUAL);
        Result newResult = saveNewEmptyResult(existingSubmission);
        newResult.setAssessor(userRepository.getUser());
        newResult.setAssessmentType(AssessmentType.SEMI_AUTOMATIC);

        // Copy automatic feedbacks into the manual result
        List<Feedback> automaticFeedbacks = new ArrayList<>();
        if (optionalExistingResult.isPresent()) {
            Result existingResult = optionalExistingResult.get();
            automaticFeedbacks = existingResult.getFeedbacks().stream().map(feedbackService::copyFeedback).collect(Collectors.toCollection(ArrayList::new));
            for (Feedback feedback : automaticFeedbacks) {
                feedback = feedbackRepository.save(feedback);
                feedback.setResult(newResult);
            }

            newResult.copyProgrammingExerciseCounters(existingResult);
        }
        newResult.setFeedbacks(automaticFeedbacks);

        // Workaround to prevent the assessor turning into a proxy object after saving
        var assessor = newResult.getAssessor();
        newResult = resultRepository.save(newResult);
        newResult.setAssessor(assessor);
        log.debug("Assessment locked with result id: {} for assessor: {}", newResult.getId(), newResult.getAssessor().getName());

        // Make sure that submission is set back after saving
        newResult.setSubmission(existingSubmission);
        return newResult;
    }

    /**
     * We need to create the submissions for the solution and template repository for the first time,
     * so that the client displays the correct results
     *
     * @param programmingExercise exercise that needs submissions for its template and solution repository
     */
    public void createInitialSubmissions(ProgrammingExercise programmingExercise) {
        createInitialSubmission(programmingExercise, programmingExercise.getSolutionParticipation());
        createInitialSubmission(programmingExercise, programmingExercise.getTemplateParticipation());
    }

    /**
     * Creates an initial submission of an {@link AbstractBaseProgrammingExerciseParticipation} with the current commit hash
     * in the repository
     *
     * @param programmingExercise Exercise for which the participation is created
     * @param participation       Template or Solution Participation
     */
    private void createInitialSubmission(ProgrammingExercise programmingExercise, AbstractBaseProgrammingExerciseParticipation participation) {
        ProgrammingSubmission submission = (ProgrammingSubmission) submissionRepository.initializeSubmission(participation, programmingExercise, SubmissionType.INSTRUCTOR);
        var latestHash = gitService.getLastCommitHash(participation.getVcsRepositoryUri());
        submission.setCommitHash(latestHash.getName());
        submission.setSubmissionDate(ZonedDateTime.now());
        submissionRepository.save(submission);
    }
}
