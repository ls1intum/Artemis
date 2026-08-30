package de.tum.cit.aet.artemis.programming.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission.createFallbackSubmission;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.hibernate.Hibernate;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.audit.AuditEvent;
import org.springframework.boot.actuate.audit.AuditEventRepository;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.assessment.domain.CategoryState;
import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.assessment.domain.FeedbackType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.domain.ScaFeedback;
import de.tum.cit.aet.artemis.assessment.domain.TestCaseFeedback;
import de.tum.cit.aet.artemis.assessment.repository.ResultRepository;
import de.tum.cit.aet.artemis.assessment.repository.ScaFeedbackRepository;
import de.tum.cit.aet.artemis.assessment.repository.TestCaseFeedbackRepository;
import de.tum.cit.aet.artemis.assessment.service.FeedbackMessageService;
import de.tum.cit.aet.artemis.assessment.service.FeedbackService;
import de.tum.cit.aet.artemis.assessment.service.ResultService;
import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.SubmissionType;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.repository.StudentParticipationRepository;
import de.tum.cit.aet.artemis.exercise.service.ExerciseDateService;
import de.tum.cit.aet.artemis.localci.service.ProgrammingExerciseFeedbackCreationService;
import de.tum.cit.aet.artemis.localci.service.ci.ContinuousIntegrationResultService;
import de.tum.cit.aet.artemis.notification.service.notifications.GroupNotificationService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseTestCase;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.domain.SolutionProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.StaticCodeAnalysisCategory;
import de.tum.cit.aet.artemis.programming.domain.TemplateProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.build.BuildLogEntry;
import de.tum.cit.aet.artemis.programming.domain.submissionpolicy.LockRepositoryPolicy;
import de.tum.cit.aet.artemis.programming.domain.submissionpolicy.SubmissionPenaltyPolicy;
import de.tum.cit.aet.artemis.programming.domain.submissionpolicy.SubmissionPolicy;
import de.tum.cit.aet.artemis.programming.dto.BuildResultNotification;
import de.tum.cit.aet.artemis.programming.dto.ProgrammingExerciseGradingStatisticsDTO;
import de.tum.cit.aet.artemis.programming.dto.ProgrammingSubmissionCommitHashDTO;
import de.tum.cit.aet.artemis.programming.dto.SubmissionPolicyValuesDTO;
import de.tum.cit.aet.artemis.programming.exception.ContinuousIntegrationException;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseTestCaseRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingSubmissionRepository;
import de.tum.cit.aet.artemis.programming.repository.SolutionProgrammingExerciseParticipationRepository;
import de.tum.cit.aet.artemis.programming.repository.StaticCodeAnalysisCategoryRepository;
import de.tum.cit.aet.artemis.programming.repository.TemplateProgrammingExerciseParticipationRepository;

@Profile(PROFILE_CORE)
@Lazy
@Service
public class ProgrammingExerciseGradingService {

    private static final String NOT_EXECUTED_MESSAGE = "Test was not executed.";

    private static final Logger log = LoggerFactory.getLogger(ProgrammingExerciseGradingService.class);

    /**
     * Suffix of the {@code text} of the warning feedback created for a test case the build reported more than once.
     */
    private static final String DUPLICATE_TEST_CASE_FEEDBACK_SUFFIX = " - Duplicate Test Case!";

    private final Optional<ContinuousIntegrationResultService> continuousIntegrationResultService;

    private final ProgrammingExerciseTestCaseRepository testCaseRepository;

    private final ResultRepository resultRepository;

    private final StudentParticipationRepository studentParticipationRepository;

    private final ProgrammingSubmissionRepository programmingSubmissionRepository;

    private final TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository;

    private final SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository;

    private final AuditEventRepository auditEventRepository;

    private final GroupNotificationService groupNotificationService;

    private final ResultService resultService;

    private final ExerciseDateService exerciseDateService;

    private final SubmissionPolicyService submissionPolicyService;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final BuildLogEntryService buildLogService;

    private final StaticCodeAnalysisCategoryRepository staticCodeAnalysisCategoryRepository;

    private final ProgrammingExerciseFeedbackCreationService feedbackCreationService;

    private final FeedbackService feedbackService;

    private final MavenCentralRateLimitNotificationService mavenCentralRateLimitNotificationService;

    private final FeedbackMessageService feedbackMessageService;

    private final TestCaseFeedbackRepository testCaseFeedbackRepository;

    private final ScaFeedbackRepository scaFeedbackRepository;

    private final TestCasePointsService testCasePointsService;

    private final ProgrammingFeedbackSynthesizerService programmingFeedbackSynthesizerService;

    public ProgrammingExerciseGradingService(StudentParticipationRepository studentParticipationRepository, ResultRepository resultRepository,
            Optional<ContinuousIntegrationResultService> continuousIntegrationResultService, ProgrammingExerciseTestCaseRepository testCaseRepository,
            TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository, FeedbackService feedbackService,
            SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository, ProgrammingSubmissionRepository programmingSubmissionRepository,
            AuditEventRepository auditEventRepository, GroupNotificationService groupNotificationService, ResultService resultService, ExerciseDateService exerciseDateService,
            SubmissionPolicyService submissionPolicyService, ProgrammingExerciseRepository programmingExerciseRepository, BuildLogEntryService buildLogService,
            StaticCodeAnalysisCategoryRepository staticCodeAnalysisCategoryRepository, ProgrammingExerciseFeedbackCreationService feedbackCreationService,
            MavenCentralRateLimitNotificationService mavenCentralRateLimitNotificationService, FeedbackMessageService feedbackMessageService,
            TestCaseFeedbackRepository testCaseFeedbackRepository, ScaFeedbackRepository scaFeedbackRepository, TestCasePointsService testCasePointsService,
            ProgrammingFeedbackSynthesizerService programmingFeedbackSynthesizerService) {
        this.studentParticipationRepository = studentParticipationRepository;
        this.continuousIntegrationResultService = continuousIntegrationResultService;
        this.resultRepository = resultRepository;
        this.testCaseRepository = testCaseRepository;
        this.templateProgrammingExerciseParticipationRepository = templateProgrammingExerciseParticipationRepository;
        this.solutionProgrammingExerciseParticipationRepository = solutionProgrammingExerciseParticipationRepository;
        this.programmingSubmissionRepository = programmingSubmissionRepository;
        this.auditEventRepository = auditEventRepository;
        this.groupNotificationService = groupNotificationService;
        this.resultService = resultService;
        this.submissionPolicyService = submissionPolicyService;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.exerciseDateService = exerciseDateService;
        this.buildLogService = buildLogService;
        this.staticCodeAnalysisCategoryRepository = staticCodeAnalysisCategoryRepository;
        this.feedbackCreationService = feedbackCreationService;
        this.feedbackService = feedbackService;
        this.mavenCentralRateLimitNotificationService = mavenCentralRateLimitNotificationService;
        this.feedbackMessageService = feedbackMessageService;
        this.testCaseFeedbackRepository = testCaseFeedbackRepository;
        this.scaFeedbackRepository = scaFeedbackRepository;
        this.testCasePointsService = testCasePointsService;
        this.programmingFeedbackSynthesizerService = programmingFeedbackSynthesizerService;
    }

    /**
     * Uses the given requestBody to extract the relevant information from it.
     * Fetches and attaches the result's feedback items to it. For programming exercises the test cases are
     * extracted from the feedbacks & the result is updated with the information from the test cases.
     *
     * @param participation the participation for which the build was finished
     * @param requestBody   RequestBody containing the build result and its feedback items
     * @return result after compilation (can only be null in case an error occurs)
     */
    @Nullable
    public Result processNewProgrammingExerciseResult(@NonNull ProgrammingExerciseParticipation participation, @NonNull Object requestBody) {
        return processNewProgrammingExerciseResult(participation, requestBody, true);
    }

    /**
     * Uses the given requestBody to extract the relevant information from it.
     * Fetches and attaches the result's feedback items to it. For programming exercises the test cases are
     * extracted from the feedbacks & the result is updated with the information from the test cases.
     *
     * @param participation the participation for which the build was finished
     * @param requestBody   RequestBody containing the build result and its feedback items
     * @param testsExpected whether test results are expected from this build (false for compile-only phases)
     * @return result after compilation (can only be null in case an error occurs)
     */
    @Nullable
    public Result processNewProgrammingExerciseResult(@NonNull ProgrammingExerciseParticipation participation, @NonNull Object requestBody, boolean testsExpected) {
        log.debug("Received new build result (NEW) for participation {}", participation.getId());

        try {
            ContinuousIntegrationResultService ciResultService = continuousIntegrationResultService.orElseThrow();
            var buildResult = ciResultService.convertBuildResult(requestBody);

            checkCorrectBranchElseThrow(participation, buildResult);
            checkHasCommitHashElseThrow(buildResult);

            ProgrammingExercise exercise = participation.getProgrammingExercise();

            // Find out which test cases were executed and calculate the score according to their status and weight.
            // This needs to be done as some test cases might not have been executed.
            // When the result is from a solution participation, extract the feedback items (= test cases) and store them in our database.
            if (participation instanceof SolutionProgrammingExerciseParticipation) {
                feedbackCreationService.extractTestCasesFromResultAndBroadcastUpdates(buildResult, exercise);
            }

            Result newResult = ciResultService.createResultFromBuildResult(buildResult, participation);

            // Fetch submission or create a fallback
            var latestSubmission = getSubmissionForBuildResult(participation, buildResult).orElseGet(() -> createAndSaveFallbackSubmission(participation, buildResult));

            // Determine if the build failed based on whether tests were expected.
            // When tests are expected: build failed if the build reported no test results at all. What decides is
            // the reported count, not the stored test-case feedback: feedback is only stored for tests the exercise
            // knows, and it knows them from the solution build. If that build failed or has not run yet, this build
            // still ran its tests - the student must not be told their build failed because of it.
            // When tests are NOT expected (compile-only phase): build failed if the script exited with non-zero.
            final boolean noTestResults = newResult.getTestCaseCount() == 0;
            final Integer exitCode = buildResult.buildScriptExitCode();
            final boolean scriptFailed = exitCode != null && exitCode != 0;
            final var buildFailed = testsExpected ? noTestResults : scriptFailed;
            if (latestSubmission.isBuildFailed() != buildFailed) {
                // Written directly. This is one boolean on a row that already exists, and it used to reach the database
                // only through saving the whole submission at the end of this method.
                programmingSubmissionRepository.updateBuildFailed(latestSubmission.getId(), buildFailed);
            }
            latestSubmission.setBuildFailed(buildFailed);

            if (buildResult.hasLogs()) {
                var programmingLanguage = exercise.getProgrammingLanguage();
                var buildLogs = buildResult.extractBuildLogs();

                if (latestSubmission.isBuildFailed()) {
                    // Check the unfiltered logs (asynchronously, so result processing is not delayed), as the filtering below can remove dependency download errors.
                    // Pass an immutable snapshot of the log messages, because the entries are truncated and filtered in place below while the async check runs.
                    var buildLogMessages = buildLogs.stream().map(BuildLogEntry::getLog).toList();
                    mavenCentralRateLimitNotificationService.notifyInstructorsIfBuildWasRateLimited(exercise.getId(), programmingLanguage, buildLogMessages);
                    buildLogs = buildLogService.removeUnnecessaryLogsForProgrammingLanguage(buildLogs, programmingLanguage);
                    var savedBuildLogs = buildLogService.saveBuildLogs(buildLogs, latestSubmission);

                    // Set the received logs in order to avoid duplicate entries (this removes existing logs)
                    latestSubmission.setBuildLogEntries(new LinkedHashSet<>(savedBuildLogs));
                }
            }

            // Note: we only set one side of the relationship because we don't know yet whether the result will actually be saved
            newResult.setSubmission(latestSubmission);
            newResult.setExerciseId(participation.getExercise().getId());
            newResult.setRatedIfNotAfterDueDate();
            // NOTE: the result is not saved yet, but is connected to the submission, the submission is not completely saved yet
            return processNewProgrammingExerciseResult(participation, newResult);
        }
        catch (ContinuousIntegrationException ex) {
            log.error("Result for participation {} could not be created", participation.getId(), ex);
            return null;
        }
    }

    /**
     * Checks that the build result belongs to the default branch of the student participation (in case it has a branch).
     * For all other cases (template/solution or student participation without a branch) it falls back to check the default branch of the programming exercise.
     *
     * @param participation The programming exercise participation in which the submission was made (including a reference to the programming exercise)
     * @param buildResult   The build result received from the CI system.
     * @throws IllegalArgumentException Thrown if the result does not belong to the default branch of the exercise.
     */
    private void checkCorrectBranchElseThrow(final ProgrammingExerciseParticipation participation, final BuildResultNotification buildResult) throws IllegalArgumentException {
        var branchName = buildResult.assignmentRepoBranchName();
        // If the branch is not present, it might be because the assignment repo did not change because only the test repo was changed
        if (!ObjectUtils.isEmpty(branchName)) {
            String participationDefaultBranch = null;
            if (participation instanceof ProgrammingExerciseStudentParticipation studentParticipation) {
                participationDefaultBranch = studentParticipation.getBranch();
            }
            if (StringUtils.isEmpty(participationDefaultBranch)) {
                participationDefaultBranch = programmingExerciseRepository.findBranchByExerciseId(participation.getExercise().getId());
            }

            if (!Objects.equals(branchName, participationDefaultBranch)) {
                throw new IllegalArgumentException("Result was produced for a different branch than the default branch");
            }
        }
    }

    /**
     * Build notifications need to provide an assignment commit hash to find the related submission.
     * If the information is missing, the result will not be processed further.
     *
     * @param buildResult The build result received from the CI system.
     */
    private void checkHasCommitHashElseThrow(final BuildResultNotification buildResult) {
        if (StringUtils.isEmpty(buildResult.commitHash(SubmissionType.MANUAL))) {
            throw new IllegalArgumentException("The provided result does not specify the assignment commit hash. The result will not get processed.");
        }
    }

    /**
     * Retrieves the submission that is assigned to the specified participation and its commit hash matches the one from the build result.
     *
     * @param participationId id of the participation
     * @param buildResult     The build result
     * @return The submission or empty if no submissions exist
     */
    protected Optional<ProgrammingSubmission> getSubmissionForBuildResult(ProgrammingExerciseParticipation participation, BuildResultNotification buildResult) {
        // Matching a commit hash needs the hash, the submission type and a way to order candidates, so only those are
        // read. Loading the submissions themselves means loading each one's participation, exercise and course, because
        // those are eager associations, so a student who pushed ten times used to make the database ship the exercise's
        // problem statement ten times over to find one commit hash.
        var candidates = programmingSubmissionRepository.findCommitHashesByParticipationId(participation.getId());
        return candidates.stream().filter(candidate -> {
            var commitHash = buildResult.commitHash(candidate.type());
            return !ObjectUtils.isEmpty(commitHash) && commitHash.equals(candidate.commitHash());
        }).max(ProgrammingSubmissionCommitHashDTO.NEWEST_FIRST)
                // The matching submission is read the same way, for the same reason. The participation it belongs to is
                // the one the caller already has, so it does not have to come back from the database with it.
                .flatMap(match -> programmingSubmissionRepository.findBuildResultSubmissionById(match.id()))
                .map(submission -> submission.toDetachedSubmission((Participation) participation));
    }

    @NonNull
    protected ProgrammingSubmission createAndSaveFallbackSubmission(ProgrammingExerciseParticipation participation, BuildResultNotification buildResult) {
        final var commitHash = buildResult.commitHash(SubmissionType.MANUAL);
        if (ObjectUtils.isEmpty(commitHash)) {
            log.error("Could not find commit hash for participation {}, build plan {}", participation.getId(), participation.getBuildPlanId());
        }
        log.warn("Could not find pending ProgrammingSubmission for Commit Hash {} (Participation {}, Build Plan {}). Will create a new one subsequently...", commitHash,
                participation.getId(), participation.getBuildPlanId());
        // We always take the build run date as the fallback solution, even though it might not be 100% accurate
        ZonedDateTime submissionDate = buildResult.buildRunDate();
        var submission = createFallbackSubmission(participation, submissionDate, commitHash);
        // Save to avoid TransientPropertyValueException.
        return programmingSubmissionRepository.save(submission);
    }

    /**
     * Fetches and attaches the result's feedback items to it. For programming exercises the test cases are
     * extracted from the feedbacks & the result is updated with the information from the test cases.
     *
     * @param participation the new result should belong to.
     * @param newResult     that contains the build result with its feedbacks.
     * @return the result after processing and persisting.
     */
    private Result processNewProgrammingExerciseResult(final ProgrammingExerciseParticipation participation, final Result newResult) {
        ProgrammingExercise programmingExercise = participation.getProgrammingExercise();
        boolean isSolutionParticipation = participation instanceof SolutionProgrammingExerciseParticipation;
        boolean isTemplateParticipation = participation instanceof TemplateProgrammingExerciseParticipation;
        boolean isStudentParticipation = !isSolutionParticipation && !isTemplateParticipation;

        Result processedResult = calculateScoreForResult(newResult, programmingExercise, isStudentParticipation);

        // Note: This programming submission might already have multiple results, however they do not contain the assessor or the feedback
        var programmingSubmission = (ProgrammingSubmission) processedResult.getSubmission();

        if (isStudentParticipation) {
            // When a student receives a new result, we want to check whether we need to lock the participation and the
            // repository when a lock repository policy is present. At this point, we know that the programming
            // exercise exists and that the participation must be a ProgrammingExerciseStudentParticipation.
            // Only lock the repository and the participation if the participation is not for a test run (i.e. for a course exercise practice repository or for an instructor exam
            // test run repository).
            // Student test exam participations will still be locked by this.
            // Already resolved: calculateScoreForResult above loads the submission policy for a student participation
            // and sets it on this very exercise instance. Loading it again meant a second fetch of the whole exercise
            // and the course it eagerly brings with it, problem statement and code of conduct included, for every
            // result.
            SubmissionPolicy submissionPolicy = programmingExercise.getSubmissionPolicy();
            if (submissionPolicy instanceof LockRepositoryPolicy policy && !((ProgrammingExerciseStudentParticipation) participation).isPracticeMode()) {
                submissionPolicyService.handleLockRepositoryPolicy(processedResult, (Participation) participation, policy);
            }

            if (programmingSubmission.getLatestResult() != null && programmingSubmission.getLatestResult().isManual() && !((Participation) participation).isPracticeMode()) {
                // Note: in this case, we do not want to save the processedResult, but we only want to update the latest semi-automatic one
                Result updatedLatestSemiAutomaticResult = updateLatestSemiAutomaticResultWithNewAutomaticFeedback(programmingSubmission.getLatestResult().getId(), processedResult);
                // Adding back dropped submission. The result owns the foreign key, so saving it is enough; the
                // submission itself did not change.
                updatedLatestSemiAutomaticResult.setSubmission(programmingSubmission);
                resultRepository.save(updatedLatestSemiAutomaticResult);

                return updatedLatestSemiAutomaticResult;
            }
        }

        // One insert. The result owns the foreign key to its submission, so setting it before saving writes it with the
        // insert; the participant score cron picks the result up from there. This used to insert the result without its
        // submission and then save the submission so that its cascade filled the column in, which meant selecting the
        // submission together with its participation, exercise and course for every result.
        programmingSubmission.addResult(processedResult);
        processedResult.setSubmission(programmingSubmission);
        processedResult = resultRepository.save(processedResult);

        return processedResult;
    }

    /**
     * Updates an existing semi-automatic result with automatic feedback from another result.
     * <p>
     * Note: for the second correction it is important that we do not create additional semi-automatic results
     *
     * @param lastSemiAutomaticResultId The latest manual result for the same submission (which must exist in the database)
     * @param newAutomaticResult        The new automatic result
     * @return The updated semi-automatic result
     */
    private Result updateLatestSemiAutomaticResultWithNewAutomaticFeedback(long lastSemiAutomaticResultId, Result newAutomaticResult) {
        // Note: fetch the semi-automatic result with feedback, test cases, and assessor again from the database
        var latestSemiAutomaticResult = resultRepository.findByIdWithEagerFeedbacksAndAssessor(lastSemiAutomaticResultId)
                .orElseThrow(() -> new EntityNotFoundException("Result", lastSemiAutomaticResultId));
        // this makes it the most recent result, but optionally keeps the draft state of an unfinished manual result
        latestSemiAutomaticResult.setCompletionDate(latestSemiAutomaticResult.getCompletionDate() != null ? newAutomaticResult.getCompletionDate() : null);

        // remove old automatic feedback (legacy rows, e.g. duplicate-test warnings and submission-policy feedback)
        latestSemiAutomaticResult.getFeedbacks().removeIf(feedback -> feedback != null && feedback.getType() == FeedbackType.AUTOMATIC);
        // remove the old typed automatic feedback; the copies added below are inserted separately and get fresh ids, so they cannot collide with these pending deletes
        latestSemiAutomaticResult.setTestCaseFeedbacks(List.of());
        latestSemiAutomaticResult.setScaFeedbacks(List.of());
        latestSemiAutomaticResult = resultRepository.save(latestSemiAutomaticResult);

        // copy all automatic feedback from the new automatic result (the copies share the deduplicated message rows)
        Result semiAutomaticResult = latestSemiAutomaticResult;
        newAutomaticResult.getTestCaseFeedbacks().stream().map(feedbackService::copyTestCaseFeedback).forEach(semiAutomaticResult::addTestCaseFeedback);
        newAutomaticResult.getScaFeedbacks().stream().map(feedbackService::copyScaFeedback).forEach(semiAutomaticResult::addScaFeedback);
        // Insert the copies right away: a synthesized legacy view is addressed by the id of its row, so everything that serializes this result afterwards needs the ids. They
        // are persisted (not merged) because they are new, which means the ids land on these very instances - and their test cases stay the initialized ones copied above,
        // which a merge copy would have replaced with uninitialized proxies.
        semiAutomaticResult.setTestCaseFeedbacks(testCaseFeedbackRepository.saveAll(semiAutomaticResult.getTestCaseFeedbacks()));
        semiAutomaticResult.setScaFeedbacks(scaFeedbackRepository.saveAll(semiAutomaticResult.getScaFeedbacks()));
        List<Feedback> copiedFeedbacks = newAutomaticResult.getFeedbacks().stream().map(feedbackService::copyFeedback).toList();
        latestSemiAutomaticResult = resultService.addFeedbackToResult(semiAutomaticResult, copiedFeedbacks, false);

        latestSemiAutomaticResult.setTestCaseCount(newAutomaticResult.getTestCaseCount());
        latestSemiAutomaticResult.setPassedTestCaseCount(newAutomaticResult.getPassedTestCaseCount());
        latestSemiAutomaticResult.setCodeIssueCount(newAutomaticResult.getCodeIssueCount());

        ProgrammingExercise exercise = (ProgrammingExercise) latestSemiAutomaticResult.getSubmission().getParticipation().getExercise();
        latestSemiAutomaticResult.setScore(latestSemiAutomaticResult.calculateTotalPointsForProgrammingExercises(calculateTestCasePoints(exercise, latestSemiAutomaticResult)),
                exercise.getMaxPoints(), exercise.getCourseViaExerciseGroupOrCourseMember());

        return latestSemiAutomaticResult;
    }

    /**
     * Updates an incoming result with the information of the exercises test cases. This update includes:
     * - Checking which test cases were not executed (not all test cases are executed in an exercise with sequential test runs)
     * - Checking the due date and the visibility.
     * - Recalculating the score based on the successful test cases weight vs the total weight of all test cases.
     * <p>
     * If there are no test cases stored in the database for the given exercise (i.e. we have a legacy exercise) or the weight has not been changed, then the result will not change
     *
     * @param result                 to modify with new score and added feedbacks (not executed tests)
     * @param exercise               the result belongs to.
     * @param isStudentParticipation boolean flag indicating weather the participation of the result is not a solution/template participation.
     * @return Result with updated feedbacks and score
     */
    public Result calculateScoreForResult(Result result, ProgrammingExercise exercise, boolean isStudentParticipation) {
        Set<ProgrammingExerciseTestCase> testCases = testCaseRepository.findByExerciseIdAndActive(exercise.getId(), true);
        var relevantTestCases = testCases;

        // We don't filter the test cases for the solution/template participation's results as they are used as indicators for the instructor!
        if (isStudentParticipation) {
            relevantTestCases = filterRelevantTestCasesForStudent(testCases, result);
        }

        if (log.isDebugEnabled()) {
            log.debug("Calculating score for exercise {} (isStudent={}): {} active test cases, {} relevant test cases (names: {})", exercise.getId(), isStudentParticipation,
                    testCases.size(), relevantTestCases.size(), relevantTestCases.stream().map(ProgrammingExerciseTestCase::getTestName).sorted().toList());
        }

        // We only apply submission policies if it is a student participation
        return calculateScoreForResult(testCases, relevantTestCases, result, exercise, isStudentParticipation);
    }

    /**
     * Updates <b>all</b> latest results of the given exercise with the information of the exercises test cases.
     * <p>
     * This update includes:
     * <ul>
     * <li>Checking which test cases were not executed (not all test cases are executed in an exercise with sequential test runs).</li>
     * <li>Checking the due date and the visibility.</li>
     * <li>Recalculating the score based on the successful test cases weight vs the total weight of all test cases.</li>
     * </ul>
     * <p>
     * If there are no test cases stored in the database for the given exercise (i.e. we have a legacy exercise) or the weight has not been changed, then the result will not
     * change.
     *
     * @param exercise whose results should be updated.
     * @return the results of the exercise that have been updated.
     */
    public List<Result> updateAllResults(final ProgrammingExercise exercise) {
        final Set<ProgrammingExerciseTestCase> testCases = testCaseRepository.findByExerciseIdAndActive(exercise.getId(), true);

        final Stream<Result> updatedTemplateAndSolutionResult = updateTemplateAndSolutionResults(exercise, testCases);

        final List<StudentParticipation> studentParticipations = new ArrayList<>();
        // We only update the latest automatic results here, later manual assessments are not affected
        studentParticipations.addAll(studentParticipationRepository.findByExerciseIdWithLatestAutomaticResultAndFeedbacks(exercise.getId()));
        // Also update manual results
        studentParticipations.addAll(studentParticipationRepository.findByExerciseIdWithManualResultAndFeedbacks(exercise.getId()));

        final Stream<Result> updatedStudentResults = updateResults(exercise, testCases, studentParticipations);

        return Stream.concat(updatedTemplateAndSolutionResult, updatedStudentResults).toList();
    }

    /**
     * Updates the latest results of all participations that do not have an individual due date. This includes the template and solution participation.
     * <p>
     * For details what will be updated for individual results, see {@link ProgrammingExerciseGradingService#updateAllResults}.
     *
     * @param exercise whose results should be updated.
     * @return the results of the exercise that have been updated.
     */
    public List<Result> updateResultsOnlyRegularDueDateParticipations(final ProgrammingExercise exercise) {
        final Set<ProgrammingExerciseTestCase> testCases = testCaseRepository.findByExerciseIdAndActive(exercise.getId(), true);

        final Stream<Result> updatedTemplateAndSolutionResult = updateTemplateAndSolutionResults(exercise, testCases);

        final List<StudentParticipation> studentParticipations = new ArrayList<>();
        // We only update the latest automatic results here, later manual assessments are not affected
        studentParticipations.addAll(studentParticipationRepository.findByExerciseIdWithLatestAutomaticResultAndFeedbacksWithoutIndividualDueDate(exercise.getId()));
        // Also update manual results
        studentParticipations.addAll(studentParticipationRepository.findByExerciseIdWithManualResultAndFeedbacksWithoutIndividualDueDate(exercise.getId()));

        final Stream<Result> updatedStudentResults = updateResults(exercise, testCases, studentParticipations);

        return Stream.concat(updatedTemplateAndSolutionResult, updatedStudentResults).toList();
    }

    /**
     * Updates the latest result scores of the given participation.
     * <p>
     * For details what will be updated, see {@link ProgrammingExerciseGradingService#updateAllResults}.
     *
     * @param participation for which the results should be updated.
     * @return a list of updated results (maximum two: latest automatic, and latest manual result).
     */
    public List<Result> updateParticipationResults(final ProgrammingExerciseStudentParticipation participation) {
        final ProgrammingExercise exercise = participation.getProgrammingExercise();
        final Set<ProgrammingExerciseTestCase> testCases = testCaseRepository.findByExerciseIdAndActive(exercise.getId(), true);
        final Set<ProgrammingExerciseTestCase> testCasesBeforeDueDate = filterTestCasesForStudents(testCases, true);
        final Set<ProgrammingExerciseTestCase> testCasesAfterDueDate = filterTestCasesForStudents(testCases, false);

        final Optional<Result> updatedAutomaticResult = studentParticipationRepository.findByIdWithLatestAutomaticResultAndFeedbacks(participation.getId())
                .flatMap(studentParticipation -> updateLatestResult(exercise, studentParticipation, testCases, testCasesBeforeDueDate, testCasesAfterDueDate, true));
        final Optional<Result> updatedManualResult = studentParticipationRepository.findByIdWithManualResultAndFeedbacks(participation.getId())
                .flatMap(studentParticipation -> updateLatestResult(exercise, studentParticipation, testCases, testCasesBeforeDueDate, testCasesAfterDueDate, true));

        return Stream.of(updatedAutomaticResult, updatedManualResult).flatMap(Optional::stream).toList();
    }

    /**
     * Updates the latest results for the given participations.
     *
     * @param exercise       the participations belong to.
     * @param allTestCases   of the programming exercise.
     * @param participations for which the latest results should be updated.
     * @return all results that have been updated.
     */
    private Stream<Result> updateResults(final ProgrammingExercise exercise, final Set<ProgrammingExerciseTestCase> allTestCases, final List<StudentParticipation> participations) {
        final Set<ProgrammingExerciseTestCase> testCasesBeforeDueDate = filterTestCasesForStudents(allTestCases, true);
        final Set<ProgrammingExerciseTestCase> testCasesAfterDueDate = filterTestCasesForStudents(allTestCases, false);

        // Load the typed automatic feedback of every result up front with two queries. Without this each result would load its own (the per-result hydration below then finds
        // the collections initialized and does nothing), which is two queries per participation on an exercise that can have thousands.
        hydrateTypedFeedbackBulk(participations.stream().map(Participation::findLatestResult).filter(Objects::nonNull).filter(result -> result.getId() != null)
                .filter(result -> !Hibernate.isInitialized(result.getTestCaseFeedbacks()) || !Hibernate.isInitialized(result.getScaFeedbacks())).toList());

        return participations.stream().map(participation -> updateLatestResult(exercise, participation, allTestCases, testCasesBeforeDueDate, testCasesAfterDueDate, true))
                .flatMap(Optional::stream);
    }

    /**
     * Updates the latest results for the template and solution participation.
     *
     * @param exercise  the template and solution belong to.
     * @param testCases of the exercise.
     * @return a stream of results that have been updated.
     *         (maximum length two; if template and/or solution do not have a results, then fewer)
     */
    private Stream<Result> updateTemplateAndSolutionResults(final ProgrammingExercise exercise, final Set<ProgrammingExerciseTestCase> testCases) {
        final Optional<Result> templateResult = templateProgrammingExerciseParticipationRepository
                .findWithEagerResultsAndFeedbacksAndSubmissionsByProgrammingExerciseId(exercise.getId())
                .flatMap(templateParticipation -> updateLatestResult(exercise, templateParticipation, testCases, testCases, testCases, false));

        final Optional<Result> solutionResult = solutionProgrammingExerciseParticipationRepository
                .findWithEagerResultsAndFeedbacksAndSubmissionsByProgrammingExerciseId(exercise.getId())
                .flatMap(solutionParticipation -> updateLatestResult(exercise, solutionParticipation, testCases, testCases, testCases, false));

        return Stream.of(templateResult, solutionResult).flatMap(Optional::stream);
    }

    /**
     * Updates the score for the latest result of the given participation.
     *
     * @param exercise               the participation belongs to.
     * @param participation          of a student in the exercise.
     * @param allTestCases           of this exercise.
     * @param testCasesBeforeDueDate the test cases that are visible to the student before the due date.
     * @param testCasesAfterDueDate  the test cases that are visible to the student after the due date.
     * @param applySubmissionPolicy  true, if submission policies should be taken into account when updating the score.
     * @return the latest result with an updated score, or nothing if the participation had no results.
     */
    private Optional<Result> updateLatestResult(ProgrammingExercise exercise, Participation participation, Set<ProgrammingExerciseTestCase> allTestCases,
            Set<ProgrammingExerciseTestCase> testCasesBeforeDueDate, Set<ProgrammingExerciseTestCase> testCasesAfterDueDate, boolean applySubmissionPolicy) {
        final Result result = participation.findLatestResult();
        if (result == null) {
            return Optional.empty();
        }
        hydrateTypedFeedback(result);

        boolean isBeforeDueDate = exerciseDateService.isBeforeDueDate(participation);
        final Set<ProgrammingExerciseTestCase> testCasesForCurrentDate = isBeforeDueDate ? testCasesBeforeDueDate : testCasesAfterDueDate;

        calculateScoreForResult(allTestCases, testCasesForCurrentDate, result, exercise, applySubmissionPolicy);

        return Optional.of(result);
    }

    /**
     * Bulk variant of {@link #hydrateTypedFeedback(Result)}: loads the typed automatic feedback of many
     * results with two queries.
     *
     * @param results the results to hydrate
     */
    private void hydrateTypedFeedbackBulk(Collection<Result> results) {
        programmingFeedbackSynthesizerService.hydrateTypedFeedback(results);
    }

    /**
     * Loads the typed automatic feedback (test-case and SCA rows) of a stored result if the (lazy)
     * collections are not initialized yet. Score re-calculation iterates these collections, and the
     * results processed here are detached entities loaded without them.
     *
     * @param result the result to hydrate
     */
    private void hydrateTypedFeedback(Result result) {
        if (result.getId() == null) {
            return;
        }
        if (!Hibernate.isInitialized(result.getTestCaseFeedbacks())) {
            result.setTestCaseFeedbacks(testCaseFeedbackRepository.findWithTestCaseByResultIds(List.of(result.getId())));
        }
        if (!Hibernate.isInitialized(result.getScaFeedbacks())) {
            result.setScaFeedbacks(scaFeedbackRepository.findByResultIds(List.of(result.getId())));
        }
    }

    /**
     * Creates an audit event logging that a re-evaluation was triggered.
     *
     * @param user     who triggered the re-evaluation.
     * @param exercise for which the evaluation was triggered.
     * @param course   the exercise belongs to.
     * @param results  of the exercise.
     */
    public void logReEvaluate(User user, ProgrammingExercise exercise, Course course, List<Result> results) {
        var auditEvent = new AuditEvent(user.getLogin(), Constants.RE_EVALUATE_RESULTS, "exercise=" + exercise.getTitle(), "course=" + course.getTitle(),
                "results=" + results.size());
        auditEventRepository.add(auditEvent);
        log.info("User {} triggered a re-evaluation of {} results for exercise {} with id {}", user.getLogin(), results.size(), exercise.getTitle(), exercise.getId());
    }

    /**
     * Filter all test cases from the score calculation that are never visible or ones with visibility "after due date" if the due date has not yet passed.
     *
     * @param testCases which should be filtered.
     * @return testCases, but the ones based on the described visibility criterion removed.
     */
    private Set<ProgrammingExerciseTestCase> filterRelevantTestCasesForStudent(Set<ProgrammingExerciseTestCase> testCases, Result result) {
        boolean isBeforeDueDate = exerciseDateService.isBeforeDueDate(result.getSubmission().getParticipation());

        return filterTestCasesForStudents(testCases, isBeforeDueDate);
    }

    /**
     * Filters the test cases to only include the ones a student should be able to see.
     *
     * @param testCases       all test cases of an exercise.
     * @param isBeforeDueDate true, if the due date has not yet passed.
     * @return a set of test cases that are visible to the student.
     */
    private Set<ProgrammingExerciseTestCase> filterTestCasesForStudents(final Set<ProgrammingExerciseTestCase> testCases, boolean isBeforeDueDate) {
        return testCases.stream().filter(testCase -> !testCase.isInvisible()).filter(testCase -> !(isBeforeDueDate && testCase.isAfterDueDate())).collect(Collectors.toSet());
    }

    /**
     * @param exercise                   the result belongs to
     * @param result                     of the build run.
     * @param testCases                  all test cases of a given programming exercise.
     * @param successfulTestCases        test cases with positive feedback. i.e. there exists a feedback that is positive
     * @param staticCodeAnalysisFeedback of a given programming exercise.
     * @param weightSum                  the sum of all weights of test cases that are visible
     */
    public record ScoreCalculationData(ProgrammingExercise exercise, Result result, Set<ProgrammingExerciseTestCase> testCases,
            Set<ProgrammingExerciseTestCase> successfulTestCases, List<ScaFeedback> staticCodeAnalysisFeedback, double weightSum) {

        ScoreCalculationData(ProgrammingExercise exercise, Result result, Set<ProgrammingExerciseTestCase> testCases, Set<ProgrammingExerciseTestCase> successfulTestCases,
                List<ScaFeedback> staticCodeAnalysisFeedback) {
            this(exercise, result, testCases, successfulTestCases, staticCodeAnalysisFeedback, calculateWeightSum(testCases));
        }

        private static double calculateWeightSum(final Set<ProgrammingExerciseTestCase> testCases) {
            return testCases.stream().filter(testCase -> !testCase.isInvisible()).mapToDouble(ProgrammingExerciseTestCase::getWeight).sum();
        }

        public Participation participation() {
            return result.getSubmission().getParticipation();
        }
    }

    /**
     * Calculates the grading for a result and updates the feedbacks
     *
     * @param testCases             All test cases for the exercise
     * @param relevantTestCases     Test cases relevant at the current due date depending on visibility and permission
     * @param result                The result to be updated
     * @param exercise              The current exercise
     * @param applySubmissionPolicy true, if submission policies should be taken into account when updating the score.
     * @return The updated result
     */
    private Result calculateScoreForResult(Set<ProgrammingExerciseTestCase> testCases, Set<ProgrammingExerciseTestCase> relevantTestCases, @NonNull Result result,
            ProgrammingExercise exercise, boolean applySubmissionPolicy) {
        List<ScaFeedback> staticCodeAnalysisFeedback = new ArrayList<>(result.getScaFeedbacks());
        boolean hasTestCaseFeedback = !result.getTestCaseFeedbacks().isEmpty();

        // Remove feedback that is in an invisible SCA category, resolve the Artemis category and penalty
        feedbackCreationService.categorizeScaFeedback(result, staticCodeAnalysisFeedback, exercise);

        if (applySubmissionPolicy) {
            // Only the policy's own values are read. Loading the exercise again to reach them, which is what this used
            // to do, fetched the problem statement and the course's code of conduct a second time for every result.
            SubmissionPolicy submissionPolicy = programmingExerciseRepository.findSubmissionPolicyValuesByExerciseId(exercise.getId())
                    .map(SubmissionPolicyValuesDTO::toDetachedPolicy).orElse(null);
            exercise.setSubmissionPolicy(submissionPolicy);
        }

        // Case 1: There are tests and test case feedback, find out which tests were not executed or should only count to the score after the due date.
        if (!relevantTestCases.isEmpty() && hasTestCaseFeedback) {
            filterTestCaseFeedbackWithoutActiveTestCase(result, testCases);

            createFeedbackForNotExecutedTests(result, relevantTestCases);
            boolean hasDuplicateTestCases = createFeedbacksForDuplicateTests(result, exercise);
            createSubmissionPolicyFeedback(result, exercise);

            final Set<ProgrammingExerciseTestCase> successfulTestCases = relevantTestCases.stream().filter(testCase -> testCase.isSuccessful(result)).collect(Collectors.toSet());

            var scoreCalculationData = new ScoreCalculationData(exercise, result, testCases, successfulTestCases, staticCodeAnalysisFeedback);
            // The score is always calculated from ALL (except visibility=never) test cases, regardless of the current date!

            updateResultScore(scoreCalculationData, hasDuplicateTestCases, applySubmissionPolicy);

            result.setTestCaseCount(relevantTestCases.size());
            result.setPassedTestCaseCount(successfulTestCases.size());
            result.setCodeIssueCount(staticCodeAnalysisFeedback.size());

            if (result.isManual()) {
                result.setScore(result.calculateTotalPointsForProgrammingExercises(calculateTestCasePoints(scoreCalculationData)), exercise.getMaxPoints(),
                        exercise.getCourseViaExerciseGroupOrCourseMember());
            }
        }
        // Case 2: There are no test cases that are executed before the due date has passed. We need to do this to differentiate this case from a build error.
        else if (!testCases.isEmpty() && hasTestCaseFeedback) {
            addFeedbackTestsNotExecuted(result, exercise, staticCodeAnalysisFeedback);
        }

        // Case 3: If there is no test case feedback, the build has failed, or it has previously fallen under case 2. In this case we just return the original result without
        // changing it.

        return result;
    }

    /**
     * Calculates the derived points per test-case id for the given calculation context. Test-case feedback
     * does not store credits — this map is how readers (score calculation, DTO assembly) obtain them.
     *
     * @param scoreCalculationData the calculation context (test cases, weight sum, exercise)
     * @return derived points per test-case id
     */
    public Map<Long, Double> calculateTestCasePoints(ScoreCalculationData scoreCalculationData) {
        return scoreCalculationData.testCases().stream().filter(testCase -> testCase.getId() != null)
                .collect(Collectors.toMap(ProgrammingExerciseTestCase::getId, testCase -> calculatePointsForTestCase(testCase, scoreCalculationData), (first, second) -> first));
    }

    /**
     * Calculates the derived points per test-case id for a result of the given exercise, loading the
     * exercise's active test cases. Convenience variant for callers outside the grading flow (e.g. manual
     * assessment). See {@link TestCasePointsService#calculateTestCasePoints(ProgrammingExercise, Result)}.
     *
     * @param exercise the programming exercise
     * @param result   the result whose participation determines special weight handling
     * @return derived points per test-case id
     */
    public Map<Long, Double> calculateTestCasePoints(ProgrammingExercise exercise, Result result) {
        return testCasePointsService.calculateTestCasePoints(exercise, result);
    }

    private void createSubmissionPolicyFeedback(Result result, ProgrammingExercise exercise) {
        if (exercise.getSubmissionPolicy() instanceof SubmissionPenaltyPolicy penaltyPolicy) {
            submissionPolicyService.createFeedbackForPenaltyPolicy(result, penaltyPolicy);
        }
    }

    /**
     * Adds the appropriate feedback to the result in case the automatic test cases were not executed.
     *
     * @param result                     to which the feedback should be added.
     * @param exercise                   to which the result belongs to.
     * @param staticCodeAnalysisFeedback that has been created for this result.
     */
    private void addFeedbackTestsNotExecuted(final Result result, final ProgrammingExercise exercise, final List<ScaFeedback> staticCodeAnalysisFeedback) {
        removeAllTestCaseFeedbackAndSetScoreToZero(result, staticCodeAnalysisFeedback);

        createFeedbacksForDuplicateTests(result, exercise);
    }

    /**
     * Only keeps test-case feedback that is associated with a relevant test case.
     * Used to remove feedback that is, e.g., related to test cases with visibility = never.
     *
     * @param result    of the build run.
     * @param testCases of the programming exercise.
     */
    private void filterTestCaseFeedbackWithoutActiveTestCase(Result result, final Set<ProgrammingExerciseTestCase> testCases) {
        result.getTestCaseFeedbacks().removeIf(feedback -> testCases.stream().noneMatch(test -> test.equals(feedback.getTestCase())));
    }

    /**
     * Checks which test cases were not executed and adds a test-case feedback row for them (positive =
     * {@code null}, matching the tri-state semantics).
     *
     * @param result    of the build run.
     * @param testCases of the given programming exercise.
     */
    private void createFeedbackForNotExecutedTests(Result result, Set<ProgrammingExerciseTestCase> testCases) {
        var notExecutedTestCases = testCases.stream().filter(testCase -> testCase.wasNotExecuted(result)).toList();
        if (notExecutedTestCases.isEmpty()) {
            // common case: all test cases were executed - avoid the message lookup entirely
            return;
        }
        var notExecutedMessage = feedbackMessageService.getOrCreate(NOT_EXECUTED_MESSAGE);
        notExecutedTestCases.forEach(testCase -> {
            TestCaseFeedback feedback = new TestCaseFeedback();
            feedback.setTestCase(testCase);
            feedback.setPositive(null);
            feedback.setMessage(notExecutedMessage);
            result.addTestCaseFeedback(feedback);
        });
    }

    /**
     * Checks which feedback entries have the same connected testcase and therefore indicate multiple testcases with the same name.
     * These duplicate testcases are added as a feedback element to the result.
     * The instructor and tutors are notified via a group notification.
     *
     * @param result              of the build run.
     * @param programmingExercise the current programming exercise.
     * @return true if result has duplicate test cases
     */
    private boolean createFeedbacksForDuplicateTests(Result result, ProgrammingExercise programmingExercise) {
        Set<ProgrammingExerciseTestCase> uniqueTestCases = new HashSet<>();
        Set<ProgrammingExerciseTestCase> duplicateTestCases = result.getTestCaseFeedbacks().stream().map(TestCaseFeedback::getTestCase)
                // Set.add() returns false if the test case is already present in the set
                .filter(testCase -> !uniqueTestCases.add(testCase)).collect(Collectors.toSet());

        // These warnings are the only automatic feedback this flow still writes to the legacy table, and re-evaluation runs the flow again on a result that already carries
        // them. Drop the previous ones first, otherwise every re-evaluation appends another copy (before the split, the removal of test feedback without an active test case
        // took care of this, because the warnings carry no test case).
        result.getFeedbacks().removeIf(feedback -> feedback.getText() != null && feedback.getText().endsWith(DUPLICATE_TEST_CASE_FEEDBACK_SUFFIX));

        if (!duplicateTestCases.isEmpty()) {
            String duplicateDetailText = "This is a duplicate test case. Please review all your test cases and verify that your test cases have unique names!";
            List<Feedback> feedbacksForDuplicateTestCases = duplicateTestCases.stream().map(testCase -> new Feedback().type(FeedbackType.AUTOMATIC)
                    .text(testCase.getTestName() + DUPLICATE_TEST_CASE_FEEDBACK_SUFFIX).detailText(duplicateDetailText).positive(false)).toList();
            result.addFeedbacks(feedbacksForDuplicateTestCases);

            groupNotificationService.notifyEditorAndInstructorGroupAboutDuplicateTestCasesForExercise(programmingExercise);

            return true;
        }

        return false;
    }

    /**
     * Update the score given the positive tests score divided by all tests' score.
     * Takes weight, bonus multiplier and absolute bonus points into account.
     * All tests in this case do not include ones with visibility=never.
     */
    private void updateResultScore(ScoreCalculationData scoreCalculationData, boolean hasDuplicateTestCases, boolean applySubmissionPolicy) {
        double score = 0D;

        if (!hasDuplicateTestCases) {
            score = calculateScore(scoreCalculationData, applySubmissionPolicy);
        }

        scoreCalculationData.result().setScore(score, scoreCalculationData.exercise().getCourseViaExerciseGroupOrCourseMember());
    }

    /**
     * Calculates the score of automatic test cases for the given result with possible penalties applied.
     *
     * @return the final total score in percent that should be given to the result.
     */
    private double calculateScore(ScoreCalculationData scoreCalculationData, boolean applySubmissionPolicy) {

        double points = calculateSuccessfulTestPoints(scoreCalculationData);
        points -= calculateTotalPenalty(scoreCalculationData, applySubmissionPolicy);

        points = Math.max(0, points);

        double maxPoints = scoreCalculationData.exercise().getMaxPoints();
        if (maxPoints <= 0.0) {
            return 0.0;
        }

        // The score is calculated as a percentage of the maximum points
        return points / maxPoints * 100.0;
    }

    /**
     * Calculates the total points that should be given for the successful test cases.
     * <p>
     * Additionally, updates the feedback in the result for each passed test case with the points
     * received for that specific test case.
     * <p>
     * Does not apply any penalties to the score yet.
     *
     * @return the total score for this result without penalty deductions.
     */
    private double calculateSuccessfulTestPoints(ScoreCalculationData scoreCalculationData) {
        Set<ProgrammingExerciseTestCase> successfulTestCases = scoreCalculationData.successfulTestCases();
        double successfulTestPoints = successfulTestCases.stream().mapToDouble(test -> calculatePointsForTestCase(test, scoreCalculationData)).sum();

        return capPointsAtMaximum(scoreCalculationData.exercise(), successfulTestPoints);
    }

    /**
     * Caps the points at the maximum achievable number.
     * <p>
     * The cap should be applied before the static code analysis penalty is subtracted as otherwise the penalty won't have any effect in some cases.
     * For example with maxPoints=20, points=30 and penalty=10, a student would still receive the full 20 points, if the points are not
     * capped before the penalty is subtracted. With the implemented order in place points will be capped to 20 points first, then the penalty is subtracted
     * resulting in 10 points.
     *
     * @param programmingExercise Used to determine the maximum allowed number of points.
     * @param points              A number of points that may potentially be higher than allowed.
     * @return The number of points, but no more than the exercise allows for.
     */
    private double capPointsAtMaximum(final ProgrammingExercise programmingExercise, double points) {
        if (Double.isNaN(points)) {
            return 0;
        }

        double maxPoints = programmingExercise.getMaxPoints() + Objects.requireNonNullElse(programmingExercise.getBonusPoints(), 0D);

        return Math.min(points, maxPoints);
    }

    /**
     * Calculates the points that should be awarded for a successful test case.
     *
     * @param testCase for which the points should be calculated.
     * @return the points which should be awarded for successfully completing the test case.
     */
    private double calculatePointsForTestCase(final ProgrammingExerciseTestCase testCase, ScoreCalculationData scoreCalculationData) {
        boolean isSolutionParticipation = scoreCalculationData.result() != null && scoreCalculationData.participation() instanceof SolutionProgrammingExerciseParticipation;
        return testCasePointsService.calculatePointsForTestCase(testCase, scoreCalculationData.testCases(), scoreCalculationData.exercise(), scoreCalculationData.weightSum(),
                isSolutionParticipation);
    }

    /**
     * Calculates a total penalty that should be applied to the score.
     * <p>
     * This includes the penalties from static code analysis and of submission policies.
     *
     * @return a total penalty that should be deducted from the score.
     */
    private double calculateTotalPenalty(ScoreCalculationData scoreCalculationData, boolean applySubmissionPolicy) {
        double penalty = 0;
        var exercise = scoreCalculationData.exercise();
        int maxStaticCodeAnalysisPenalty = Optional.ofNullable(exercise.getMaxStaticCodeAnalysisPenalty()).orElse(100);
        if (Boolean.TRUE.equals(exercise.isStaticCodeAnalysisEnabled()) && maxStaticCodeAnalysisPenalty > 0) {
            penalty += calculateStaticCodeAnalysisPenalty(scoreCalculationData.staticCodeAnalysisFeedback(), exercise);
        }

        if (applySubmissionPolicy && exercise.getSubmissionPolicy() instanceof SubmissionPenaltyPolicy penaltyPolicy) {
            penalty += submissionPolicyService.calculateSubmissionPenalty(scoreCalculationData.participation(), penaltyPolicy);
        }

        return penalty;
    }

    /**
     * Calculates the total penalty over all static code analysis issues.
     * Also updates the credits of each SCA feedback item as a side effect.
     * This allows other parts of Artemis a more simplified score calculation by just summing up all feedback points.
     *
     * @param staticCodeAnalysisFeedback The list of static code analysis feedback
     * @param programmingExercise        The current exercise
     * @return The sum of all penalties, capped at the maximum allowed penalty
     */
    private double calculateStaticCodeAnalysisPenalty(final List<ScaFeedback> staticCodeAnalysisFeedback, final ProgrammingExercise programmingExercise) {
        // reset stale penalties from earlier grading runs - the loop below only assigns penalties for rows
        // in currently GRADED categories, so a category switched away from GRADED must not keep its old value
        staticCodeAnalysisFeedback.forEach(feedback -> feedback.setPenalty(null));
        final var feedbackByCategory = staticCodeAnalysisFeedback.stream().collect(Collectors.groupingBy(feedback -> Objects.requireNonNullElse(feedback.getCategory(), "")));
        final double maxExercisePenaltyPoints = Objects.requireNonNullElse(programmingExercise.getMaxStaticCodeAnalysisPenalty(), 100) / 100.0 * programmingExercise.getMaxPoints();
        double overallPenaltyPoints = 0;

        for (var category : staticCodeAnalysisCategoryRepository.findByExerciseId(programmingExercise.getId())) {
            if (!category.getState().equals(CategoryState.GRADED)) {
                continue;
            }

            // get all feedback in this category
            List<ScaFeedback> categoryFeedback = feedbackByCategory.getOrDefault(category.getName(), List.of());

            // calculate the sum of all per-feedback penalties
            double categoryPenaltyPoints = categoryFeedback.size() * category.getPenalty();

            // cap at the maximum allowed penalty for this category
            if (category.getMaxPenalty() != null && categoryPenaltyPoints > category.getMaxPenalty()) {
                categoryPenaltyPoints = category.getMaxPenalty();
            }

            // Cap at the maximum allowed penalty for this exercise (maxStaticCodeAnalysisPenalty is in percent) The max penalty is applied to the maxScore. If no max penalty
            // was supplied, the value defaults to 100 percent. If for example maxScore is 6, maxBonus is 4 and the penalty is 50 percent, then a student can only lose
            // 3 (0.5 * maxScore) points due to static code analysis issues.
            if (overallPenaltyPoints + categoryPenaltyPoints > maxExercisePenaltyPoints) {
                categoryPenaltyPoints = maxExercisePenaltyPoints - overallPenaltyPoints;
            }
            overallPenaltyPoints += categoryPenaltyPoints;

            // update the graded penalty of the feedback rows in this category (the capped, per-row share;
            // negated it is the credits value used everywhere else)
            if (!categoryFeedback.isEmpty()) {
                double perFeedbackPenalty = categoryPenaltyPoints / categoryFeedback.size();
                categoryFeedback.forEach(feedback -> feedback.setPenalty(perFeedbackPenalty));
            }
        }

        return overallPenaltyPoints;
    }

    /**
     * Remove all test case feedback information from a result and treat it as if it has a score of 0.
     *
     * @param result                     Result containing all feedback
     * @param staticCodeAnalysisFeedback Static code analysis feedback to keep
     */
    private void removeAllTestCaseFeedbackAndSetScoreToZero(Result result, List<ScaFeedback> staticCodeAnalysisFeedback) {
        result.setTestCaseFeedbacks(List.of());
        result.setScaFeedbacks(staticCodeAnalysisFeedback);
        result.setScore(0D);
        result.setTestCaseCount(0);
        result.setPassedTestCaseCount(0);
        result.setCodeIssueCount(0);
    }

    /**
     * Generates grading statistics for a programming exercise.
     * <p>
     * This method compiles various statistics for a programming exercise identified by the provided exercise ID.
     * It gathers data on the number of passed and failed tests per test case, as well as the number of issues
     * detected in static code analysis per category. The results are encapsulated in a
     * {@link ProgrammingExerciseGradingStatisticsDTO} object which includes:
     * - The number of results processed
     * - A map of test case names to their respective pass/fail statistics
     * - A map of static code analysis category names to the number of students per issue count
     * <p>
     * The method performs the following steps:
     * 1. Initializes statistics for the number of passed and failed tests per test case.
     * 2. Initializes statistics for the number of students per amount of detected issues per category.
     * 3. Fetches the latest automatic results for the exercise along with their feedback.
     * 4. Processes each result to update test case statistics and detect issues per category.
     * 5. Merges individual result statistics into overall statistics.
     *
     * @param exerciseId the ID of the exercise
     * @return a {@link ProgrammingExerciseGradingStatisticsDTO} object containing the compiled grading statistics
     */
    public ProgrammingExerciseGradingStatisticsDTO generateGradingStatistics(Long exerciseId) {
        // Initialize statistics for the number of passed and failed tests per test case
        final var testCases = testCaseRepository.findByExerciseId(exerciseId);
        final var testCaseStatsMap = new HashMap<String, ProgrammingExerciseGradingStatisticsDTO.TestCaseStats>();
        for (ProgrammingExerciseTestCase testCase : testCases) {
            testCaseStatsMap.put(testCase.getTestName(), new ProgrammingExerciseGradingStatisticsDTO.TestCaseStats(0, 0));
        }

        // Initialize statistics for the number of students per amount of detected issues per category
        final Set<StaticCodeAnalysisCategory> categories = staticCodeAnalysisCategoryRepository.findByExerciseId(exerciseId);
        final var categoryIssuesStudentsMap = new HashMap<String, Map<Integer, Integer>>();
        for (StaticCodeAnalysisCategory category : categories) {
            categoryIssuesStudentsMap.put(category.getName(), new HashMap<>());
        }

        final var results = resultRepository.findLatestAutomaticResultsWithEagerFeedbacksForExercise(exerciseId);
        hydrateTypedFeedbackBulk(results);
        for (Result result : results) {
            // Count the number of detected issues per category for the current result
            final var categoryIssuesMap = categorizeStaticCodeAnalysisIssues(result);

            // Update the statistics for each test case based on the feedback
            updateTestCaseMapBasedOnResultFeedback(result, testCaseStatsMap);

            // Merge the current result's category issues map into the overall map
            mergeCategoryIssuesMap(categoryIssuesStudentsMap, categoryIssuesMap);
        }

        return new ProgrammingExerciseGradingStatisticsDTO(results.size(), testCaseStatsMap, categoryIssuesStudentsMap);
    }

    /**
     * Categorizes static code analysis issues from a given result.
     * <p>
     * This method processes the feedbacks associated with the provided result to identify
     * and count the occurrences of static code analysis issues for each category. The result
     * is a map where the key is the category name and the value is the count of issues detected
     * in that category.
     *
     * @param result The {@link Result} object containing feedbacks to be analyzed
     * @return A map where the key is the static code analysis category name and the value is the count of occurrences of issues in that category
     */
    private static Map<String, Integer> categorizeStaticCodeAnalysisIssues(Result result) {
        return result.getScaFeedbacks().stream()
                // Map each SCA feedback row to its (Artemis) static code analysis category name
                .map(ScaFeedback::getCategory)
                // Filter out any missing category names to avoid counting them
                .filter(categoryName -> categoryName != null && !categoryName.isEmpty())
                // Collect the results into a map where the key is the category name and the value is the count of occurrences
                .collect(Collectors.toMap(
                        // The key in the resulting map is the category name
                        categoryName -> categoryName,
                        // The initial value for each key is 1, representing the first occurrence
                        categoryName -> 1,
                        // If the key already exists, sum the existing value with the new value (i.e., increment the count)
                        Integer::sum));
    }

    /**
     * Updates the test case statistics map based on the feedback from a given result.
     * <p>
     * This method processes the feedbacks associated with the provided result to update
     * the test case statistics map. It counts the number of positive and non-positive feedbacks
     * for each test case and updates the corresponding entries in the provided test case statistics map.
     *
     * @param result           The {@link Result} object containing feedbacks to be analyzed
     * @param testCaseStatsMap The map of test case names to their respective statistics (passed and failed counts)
     */
    private static void updateTestCaseMapBasedOnResultFeedback(Result result, HashMap<String, ProgrammingExerciseGradingStatisticsDTO.TestCaseStats> testCaseStatsMap) {
        result.getTestCaseFeedbacks().stream()
                // Only rows with a resolvable test case name and an executed test (positive != null) count
                .filter(feedback -> feedback.getTestCase() != null && feedback.getTestCase().getTestName() != null && feedback.isPositive() != null)
                // Collect the rows into a map grouped by test case name, and partitioned by whether the test passed
                .collect(Collectors.groupingBy(
                        // Group by the name of the test case associated with the feedback
                        feedback -> feedback.getTestCase().getTestName(),
                        // Partition each group into positive and non-positive rows, and count the occurrences
                        Collectors.partitioningBy(TestCaseFeedback::isPositive, Collectors.counting())
                // Process each entry in the resulting map
                )).forEach((testName, partitionedFeedbacks) -> {
                    // Get the count of positive feedbacks for the test case, defaulting to 0 if none exist
                    long numPassed = partitionedFeedbacks.getOrDefault(true, 0L);
                    // Get the count of non-positive feedbacks for the test case, defaulting to 0 if none exist
                    long numFailed = partitionedFeedbacks.getOrDefault(false, 0L);
                    // Ensure there is an entry for the test case in the testCaseStatsMap, initializing with zero passed and failed if absent
                    testCaseStatsMap.putIfAbsent(testName, new ProgrammingExerciseGradingStatisticsDTO.TestCaseStats(0, 0));
                    // Update the entry for the test case in the testCaseStatsMap, incrementing the passed and failed counts
                    testCaseStatsMap.computeIfPresent(testName,
                            // Create a new TestCaseStats object with the updated counts and replace the existing entry
                            (key, stats) -> new ProgrammingExerciseGradingStatisticsDTO.TestCaseStats(stats.numPassed() + (int) numPassed, stats.numFailed() + (int) numFailed));
                });
    }

    /**
     * Merges the result map of a single student with the overall issues map.
     * <p>
     * This method updates the overall issues map for all students by incorporating the issues
     * detected for a single student. Each category of issues is updated with the count of issues
     * detected for that category and the number of students who had that count of issues.
     *
     * @param issuesAllStudents   The overall issues map for all students. The key is the category name,
     *                                and the value is a map where the key is the issue count and the value
     *                                is the number of students with that issue count.
     * @param issuesSingleStudent The issues map for one student. The key is the category name, and the value
     *                                is the number of issues detected in that category for this student.
     */
    private static void mergeCategoryIssuesMap(final Map<String, Map<Integer, Integer>> issuesAllStudents, final Map<String, Integer> issuesSingleStudent) {
        // Iterate over each entry in the issues map for a single student
        issuesSingleStudent.forEach((category, issueCount) -> {
            // Ensure the overall issues map has an entry for the current category
            issuesAllStudents.putIfAbsent(category, new HashMap<>());
            // Ensure the category map has an entry for the current issue count with an initial value of 0
            issuesAllStudents.get(category).putIfAbsent(issueCount, 0);
            // Increment the number of students who had the current issue count for the current category by 1
            issuesAllStudents.get(category).merge(issueCount, 1, Integer::sum);
        });
    }
}
