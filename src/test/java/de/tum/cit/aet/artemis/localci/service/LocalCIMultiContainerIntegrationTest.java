package de.tum.cit.aet.artemis.localci.service;

import static de.tum.cit.aet.artemis.assessment.web.AssessmentWebsocketTopics.NEW_RESULTS;
import static de.tum.cit.aet.artemis.core.config.Constants.LOCAL_CI_DOCKER_CONTAINER_WORKING_DIRECTORY;
import static de.tum.cit.aet.artemis.core.config.Constants.LOCAL_CI_RESULTS_DIRECTORY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;
import org.mockito.ArgumentMatcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;

import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.CopyArchiveFromContainerCmd;
import com.github.dockerjava.api.command.ExecCreateCmd;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.command.ExecStartCmd;
import com.github.dockerjava.api.command.InspectExecCmd;
import com.github.dockerjava.api.command.InspectExecResponse;
import com.github.dockerjava.api.exception.NotFoundException;

import tools.jackson.core.JacksonException;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.domain.TestCaseFeedback;
import de.tum.cit.aet.artemis.assessment.repository.TestCaseFeedbackRepository;
import de.tum.cit.aet.artemis.localci.domain.BuildJob;
import de.tum.cit.aet.artemis.localvc.util.LocalVCTestRepository;
import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationLocalCILocalVCTestBase;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildConfig;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.domain.build.BuildPhaseCondition;
import de.tum.cit.aet.artemis.programming.domain.build.BuildStatus;
import de.tum.cit.aet.artemis.programming.domain.submissionpolicy.LockRepositoryPolicy;
import de.tum.cit.aet.artemis.programming.dto.BuildContainerDTO;
import de.tum.cit.aet.artemis.programming.dto.BuildContainerRepositoryDTO;
import de.tum.cit.aet.artemis.programming.dto.BuildPhaseDTO;
import de.tum.cit.aet.artemis.programming.dto.BuildPlanPhasesDTO;

/** End-to-end multi-container builds: one job per container, run by the in-process agent, merged into one result. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Execution(ExecutionMode.SAME_THREAD)
@Isolated
class LocalCIMultiContainerIntegrationTest extends AbstractProgrammingIntegrationLocalCILocalVCTestBase {

    private static final String TEST_PREFIX = "localcimc";

    private static final String INSTRUCTOR_CONTAINER = "instructor_tests";

    private static final String STUDENT_CONTAINER = "student_tests";

    /** The 9 structural test cases of the partly-successful fixture; returned by the instructor container. */
    private static final Set<String> STRUCTURAL_TEST_NAMES = Set.of("testClass[SortStrategy]", "testAttributes[Context]", "testAttributes[Policy]", "testClass[MergeSort]",
            "testClass[BubbleSort]", "testConstructors[Policy]", "testMethods[Context]", "testMethods[Policy]", "testMethods[SortStrategy]");

    /** The 4 behavior test cases of the partly-successful fixture; returned by the student container. */
    private static final Set<String> BEHAVIOR_TEST_NAMES = Set.of("testMergeSort()", "testUseBubbleSortForSmallList()", "testUseMergeSortForBigList()", "testBubbleSort()");

    private static final String RESULTS_DIRECTORY_REGEX = LOCAL_CI_DOCKER_CONTAINER_WORKING_DIRECTORY + LOCAL_CI_RESULTS_DIRECTORY;

    // The result processing service is lazy and registers its result queue listener in @PostConstruct; without this
    // injection the agent's results would pile up in the queue unprocessed and no result would ever finalize.
    @SuppressWarnings("unused")
    @Autowired
    private LocalCIResultProcessingService localCIResultProcessingService;

    @Autowired
    private TestCaseFeedbackRepository testCaseFeedbackRepository;

    private LocalVCTestRepository studentAssignmentRepository;

    private LocalVCTestRepository testsRepository;

    /** Only created by the solution-build tests; reset after them when present. */
    private LocalVCTestRepository solutionRepository;

    /** Only created by the template-build test; reset after it when present. */
    private LocalVCTestRepository templateRepository;

    private String commitHash;

    private String testsCommitHash;

    @Override
    protected String getTestPrefix() {
        return TEST_PREFIX;
    }

    @BeforeAll
    void setupAll() {
        buildJobRepository.deleteAll();
        CredentialsProvider.setDefault(new UsernamePasswordCredentialsProvider(localVCUsername, localVCPassword));
    }

    @BeforeEach
    void initRepositories() throws Exception {
        // Classes sharing this context stop the agent's listener (init() is a no-op until resetInitializedState()) or close
        // its services: start from empty queues and a live, unpaused agent.
        distributedDataAccessService.getDistributedBuildJobQueue().clear();
        distributedDataAccessService.getDistributedProcessingJobs().clear();
        distributedDataAccessService.getDistributedBuildResultQueue().clear();
        if (buildAgentConfiguration.getBuildExecutor() == null) {
            buildAgentConfiguration.openBuildAgentServices();
        }
        sharedQueueProcessingService.resetInitializedState();
        sharedQueueProcessingService.setPauseState(false);
        sharedQueueProcessingService.init();
        studentAssignmentRepository = localVCLocalCITestService.createRepositoryWithWorkingCopy(projectKey1, assignmentRepositorySlug);
        commitHash = localVCLocalCITestService.commitFile(studentAssignmentRepository.workingCopyPath(), studentAssignmentRepository.workingCopy());
        studentAssignmentRepository.workingCopy().push().call();

        testsRepository = localVCLocalCITestService.createRepositoryWithWorkingCopy(projectKey1, testsRepositorySlug);
        testsCommitHash = localVCLocalCITestService.commitFile(testsRepository.workingCopyPath(), testsRepository.workingCopy());
        testsRepository.workingCopy().push().call();

        dockerClientTestService.mockInspectImage(dockerClient);
    }

    @AfterEach
    void removeRepositories() throws IOException {
        studentAssignmentRepository.deleteWorkingCopy();
        testsRepository.deleteWorkingCopy();
        if (solutionRepository != null) {
            solutionRepository.deleteWorkingCopy();
            solutionRepository = null;
        }
        if (templateRepository != null) {
            templateRepository.deleteWorkingCopy();
            templateRepository = null;
        }
    }

    /** A successful rebuild after a failed build clears the build-failed flag; its feedback is merged into the tutor's draft at finalize. */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testRebuildOfSameCommitAfterFailedAttemptIsSuccessfulAndSparesManualDraft() throws Exception {
        String instructorImage = "mc-instructor:rebuild";
        String studentImage = "mc-student:rebuild";
        configureTwoContainerPlan(instructorImage, studentImage);
        mockContainerLifecycle(instructorImage, "mc-instructor-rebuild");
        mockContainerLifecycle(studentImage, "mc-student-rebuild");
        dockerClientTestService.mockInputStreamReturnedFromContainer(dockerClient, "mc-instructor-rebuild", RESULTS_DIRECTORY_REGEX, structuralResults());
        // First attempt: the student container crashes.
        mockScriptExitCode("mc-student-rebuild", "mc-student-rebuild-exec", 1L);
        mockMissingResults("mc-student-rebuild");

        ProgrammingExerciseStudentParticipation participation = localVCLocalCITestService.createParticipation(programmingExercise, student1Login);
        processNewPush();
        ProgrammingSubmission failedSubmission = awaitFinalizedResult(participation.getId(), 120);
        Result failedResult = failedSubmission.getLatestResult();
        assertThat(failedSubmission.isBuildFailed()).isTrue();
        assertThat(failedResult.isSuccessful()).isFalse();

        // A tutor starts a manual assessment on the submission before the rebuild (no completion date yet).
        Result manualDraft = new Result();
        manualDraft.setAssessmentType(AssessmentType.MANUAL);
        manualDraft.setCompletionDate(null);
        manualDraft.setSubmission(failedSubmission);
        manualDraft.setExerciseId(programmingExercise.getId());
        manualDraft = resultRepository.save(manualDraft);

        // Second attempt: the student container now succeeds; both containers need fresh result streams.
        dockerClientTestService.mockInputStreamReturnedFromContainer(dockerClient, "mc-instructor-rebuild", RESULTS_DIRECTORY_REGEX, structuralResults());
        mockScriptExitCode("mc-student-rebuild", "mc-student-rebuild-exec", 0L);
        dockerClientTestService.mockInputStreamReturnedFromContainer(dockerClient, "mc-student-rebuild", RESULTS_DIRECTORY_REGEX, behaviorResults());
        localCITriggerService.triggerBuild(participation, false);

        ProgrammingSubmission rebuiltSubmission = awaitBuildMergedInto(participation.getId(), manualDraft.getId(), 2, 120);
        String jobStatuses = buildJobRepository.findAll().stream().filter(job -> Objects.equals(job.getParticipationId(), participation.getId()))
                .map(job -> job.getBuildJobId() + ":" + job.getBuildStatus()).toList().toString();
        assertThat(rebuiltSubmission.isBuildFailed()).as("jobs %s", jobStatuses).isFalse();

        // merged into the draft, which stays open and the latest result
        Result draftAfterRebuild = resultRepository.findByIdWithEagerFeedbacksElseThrow(manualDraft.getId());
        assertThat(draftAfterRebuild.getCompletionDate()).isNull();
        assertThat(draftAfterRebuild.getAssessmentType()).isEqualTo(AssessmentType.MANUAL);
        assertThat(draftAfterRebuild.getScore()).isNotNull();
        assertThat(feedbackTestNames(draftAfterRebuild)).containsExactlyInAnyOrderElementsOf(union(STRUCTURAL_TEST_NAMES, BEHAVIOR_TEST_NAMES));
        assertThat(rebuiltSubmission.getLatestResult().getId()).isEqualTo(manualDraft.getId());
    }

    /** A rebuild over the limit of a lock-repository policy activated after two builds stores its merged result unrated. */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testLockRepositoryPolicyMarksTheMergedResultOfAnOverLimitSubmissionUnrated() throws Exception {
        String instructorImage = "mc-instructor:lock";
        String studentImage = "mc-student:lock";
        configureTwoContainerPlan(instructorImage, studentImage);
        mockContainerLifecycle(instructorImage, "mc-instructor-lock");
        mockContainerLifecycle(studentImage, "mc-student-lock");
        stubLockTestResults();

        // Two student submissions, each built and merged, before any policy exists.
        ProgrammingExerciseStudentParticipation participation = localVCLocalCITestService.createParticipation(programmingExercise, student1Login);
        localVCServletService.processNewPush(commitHash, studentAssignmentRepository.bareRepository().getRepository(), student1, Optional.empty(), Optional.empty(),
                Optional.empty());
        ProgrammingSubmission firstSubmission = awaitFinalizedResult(participation.getId(), 120);

        String secondCommit = localVCLocalCITestService.commitFile(studentAssignmentRepository.workingCopyPath(), studentAssignmentRepository.workingCopy(), "second-push.txt");
        studentAssignmentRepository.workingCopy().push().call();
        stubLockTestResults();
        localVCServletService.processNewPush(secondCommit, studentAssignmentRepository.bareRepository().getRepository(), student1, Optional.empty(), Optional.empty(),
                Optional.empty());
        ProgrammingSubmission secondSubmission = awaitFinalizedResultAfter(participation.getId(), firstSubmission.getLatestResult().getId(), 120);

        LockRepositoryPolicy lockRepositoryPolicy = new LockRepositoryPolicy();
        lockRepositoryPolicy.setSubmissionLimit(1);
        lockRepositoryPolicy.setActive(true);
        programmingExerciseUtilService.addSubmissionPolicyToExercise(lockRepositoryPolicy, programmingExercise);

        stubLockTestResults();
        localCITriggerService.triggerBuild(participation, false);
        ProgrammingSubmission rebuiltSubmission = awaitFinalizedResultAfter(participation.getId(), secondSubmission.getLatestResult().getId(), 120);
        Result rebuiltResult = rebuiltSubmission.getLatestResult();
        assertThat(rebuiltResult.getCompletionDate()).isNotNull();
        assertThat(rebuiltResult.isRated()).isFalse();
    }

    /** Fresh result streams for both lock-test containers; a mocked archive stream can only be read once per build. */
    private void stubLockTestResults() throws IOException {
        dockerClientTestService.mockInputStreamReturnedFromContainer(dockerClient, "mc-instructor-lock", RESULTS_DIRECTORY_REGEX, structuralResults());
        dockerClientTestService.mockInputStreamReturnedFromContainer(dockerClient, "mc-student-lock", RESULTS_DIRECTORY_REGEX, behaviorResults());
    }

    /** A rebuild of a manually assessed submission merges its feedback into the assessment, which stays the latest result. */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testMergedResultFeedbackIsMergedIntoTheLatestManualResult() throws Exception {
        String instructorImage = "mc-instructor:manual";
        String studentImage = "mc-student:manual";
        configureTwoContainerPlan(instructorImage, studentImage);
        mockContainerLifecycle(instructorImage, "mc-instructor-manual");
        mockContainerLifecycle(studentImage, "mc-student-manual");
        dockerClientTestService.mockInputStreamReturnedFromContainer(dockerClient, "mc-instructor-manual", RESULTS_DIRECTORY_REGEX, structuralResults());
        dockerClientTestService.mockInputStreamReturnedFromContainer(dockerClient, "mc-student-manual", RESULTS_DIRECTORY_REGEX, behaviorResults());

        ProgrammingExerciseStudentParticipation participation = localVCLocalCITestService.createParticipation(programmingExercise, student1Login);
        processNewPush();
        ProgrammingSubmission submission = awaitFinalizedResult(participation.getId(), 120);

        // A tutor completes a manual assessment of the submission.
        Result manualResult = new Result();
        manualResult.setAssessmentType(AssessmentType.MANUAL);
        manualResult.setCompletionDate(ZonedDateTime.now());
        manualResult.setSubmission(submission);
        manualResult.setExerciseId(programmingExercise.getId());
        manualResult = resultRepository.save(manualResult);

        // Rebuild the same commit with fresh result streams.
        dockerClientTestService.mockInputStreamReturnedFromContainer(dockerClient, "mc-instructor-manual", RESULTS_DIRECTORY_REGEX, structuralResults());
        dockerClientTestService.mockInputStreamReturnedFromContainer(dockerClient, "mc-student-manual", RESULTS_DIRECTORY_REGEX, behaviorResults());
        localCITriggerService.triggerBuild(participation, false);
        ProgrammingSubmission rebuiltSubmission = awaitBuildMergedInto(participation.getId(), manualResult.getId(), 2, 120);
        assertThat(rebuiltSubmission.getLatestResult().getId()).isEqualTo(manualResult.getId());

        Result mergedManualResult = resultRepository.findByIdWithEagerFeedbacksElseThrow(manualResult.getId());
        assertThat(mergedManualResult.getAssessmentType()).isEqualTo(AssessmentType.MANUAL);
        assertThat(feedbackTestNames(mergedManualResult)).containsExactlyInAnyOrderElementsOf(union(STRUCTURAL_TEST_NAMES, BEHAVIOR_TEST_NAMES));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testTestsPushRebuildsTemplateOnceAfterMultiContainerSolutionBuild() throws Exception {
        String instructorImage = "mc-instructor:template";
        String studentImage = "mc-student:template";
        configureTwoContainerPlan(instructorImage, studentImage);
        mockContainerLifecycle(instructorImage, "mc-instructor-template");
        mockContainerLifecycle(studentImage, "mc-student-template");
        dockerClientTestService.mockInputStreamReturnedFromContainer(dockerClient, "mc-instructor-template", RESULTS_DIRECTORY_REGEX, structuralResults());
        dockerClientTestService.mockInputStreamReturnedFromContainer(dockerClient, "mc-student-template", RESULTS_DIRECTORY_REGEX, behaviorResults());

        solutionRepository = localVCLocalCITestService.createRepositoryWithWorkingCopy(projectKey1, solutionRepositorySlug);
        localVCLocalCITestService.commitFile(solutionRepository.workingCopyPath(), solutionRepository.workingCopy());
        solutionRepository.workingCopy().push().call();
        templateRepository = localVCLocalCITestService.createRepositoryWithWorkingCopy(projectKey1, templateRepositorySlug);
        localVCLocalCITestService.commitFile(templateRepository.workingCopyPath(), templateRepository.workingCopy());
        templateRepository.workingCopy().push().call();

        localVCServletService.processNewPush(testsCommitHash, testsRepository.bareRepository().getRepository(), userTestRepository.getUserWithAuthorities(), Optional.empty(),
                Optional.empty(), Optional.empty());

        awaitFinalizedResult(solutionParticipation.getId(), 120);
        awaitFinishedBuildJobs(templateParticipation.getId(), 2, 120);
        awaitFinalizedResult(templateParticipation.getId(), 120);

        // Exactly one template build: two container jobs, not two per solution container.
        var templateJobs = buildJobRepository.findAll().stream().filter(job -> Objects.equals(job.getParticipationId(), templateParticipation.getId())).toList();
        assertThat(templateJobs).hasSize(2);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testSolutionBuildGeneratesAllTestCasesAcrossContainersWithoutDuplicates() throws Exception {
        String instructorImage = "mc-instructor:solution";
        String studentImage = "mc-student:solution";
        configureTwoContainerPlan(instructorImage, studentImage);
        mockContainerLifecycle(instructorImage, "mc-instructor-solution");
        mockContainerLifecycle(studentImage, "mc-student-solution");
        // The structural results already contain the constructor test; the behavior container reports it a second time.
        var behaviorPlusSharedTest = new java.util.HashMap<>(behaviorResults());
        behaviorPlusSharedTest.putAll(constructorTestResult());
        dockerClientTestService.mockInputStreamReturnedFromContainer(dockerClient, "mc-instructor-solution", RESULTS_DIRECTORY_REGEX, structuralResults());
        dockerClientTestService.mockInputStreamReturnedFromContainer(dockerClient, "mc-student-solution", RESULTS_DIRECTORY_REGEX, behaviorPlusSharedTest);

        solutionRepository = localVCLocalCITestService.createRepositoryWithWorkingCopy(projectKey1, solutionRepositorySlug);
        localVCLocalCITestService.commitFile(solutionRepository.workingCopyPath(), solutionRepository.workingCopy());
        solutionRepository.workingCopy().push().call();

        localCITriggerService.triggerBuild(solutionParticipation, false);

        ProgrammingSubmission submission = awaitFinalizedResult(solutionParticipation.getId(), 120);

        Set<String> expectedNames = union(STRUCTURAL_TEST_NAMES, BEHAVIOR_TEST_NAMES);
        assertThat(testCaseRepository.findByExerciseIdAndActive(programmingExercise.getId(), true)).extracting(testCase -> testCase.getTestName())
                .containsExactlyInAnyOrderElementsOf(expectedNames);

        Result result = resultRepository.findByIdWithEagerFeedbacksElseThrow(submission.getLatestResult().getId());
        assertThat(feedbackTestNames(result)).containsExactlyInAnyOrderElementsOf(expectedNames);
        assertThat(testCaseFeedbacksOf(result)).filteredOn(feedback -> "testConstructors[Policy]".equals(feedback.getTestCase().getTestName())).hasSize(1);
        assertThat(result.getFeedbacks()).noneMatch(feedback -> feedback.getText() != null && feedback.getText().contains("Duplicate Test Case"));
        assertThat(result.getScore()).isGreaterThan(0.0);
        // partly successful fixture: scored, built, but not every test passed
        assertThat(result.isSuccessful()).isFalse();
        assertThat(submission.isBuildFailed()).isFalse();
    }

    /** A test removed from the solution, so reported by no container, is deactivated when the merged result is finalized. */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testSolutionBuildDeactivatesATestRemovedFromEveryContainer() throws Exception {
        String instructorImage = "mc-instructor:deactivate";
        String studentImage = "mc-student:deactivate";
        configureTwoContainerPlan(instructorImage, studentImage);
        mockContainerLifecycle(instructorImage, "mc-instructor-deactivate");
        mockContainerLifecycle(studentImage, "mc-student-deactivate");

        // First solution build: every test case is registered across the two containers.
        dockerClientTestService.mockInputStreamReturnedFromContainer(dockerClient, "mc-instructor-deactivate", RESULTS_DIRECTORY_REGEX, structuralResults());
        dockerClientTestService.mockInputStreamReturnedFromContainer(dockerClient, "mc-student-deactivate", RESULTS_DIRECTORY_REGEX, behaviorResults());
        solutionRepository = localVCLocalCITestService.createRepositoryWithWorkingCopy(projectKey1, solutionRepositorySlug);
        localVCLocalCITestService.commitFile(solutionRepository.workingCopyPath(), solutionRepository.workingCopy());
        solutionRepository.workingCopy().push().call();
        localCITriggerService.triggerBuild(solutionParticipation, false);
        ProgrammingSubmission firstSubmission = awaitFinalizedResult(solutionParticipation.getId(), 120);
        assertThat(testCaseRepository.findByExerciseIdAndActive(programmingExercise.getId(), true)).extracting(testCase -> testCase.getTestName())
                .containsExactlyInAnyOrderElementsOf(union(STRUCTURAL_TEST_NAMES, BEHAVIOR_TEST_NAMES));

        // Second build: the constructor test is removed from the solution, so it is reported by neither container.
        Map<String, String> structuralWithoutConstructor = structuralResults().entrySet().stream().filter(entry -> !entry.getKey().contains("ConstructorTest"))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        dockerClientTestService.mockInputStreamReturnedFromContainer(dockerClient, "mc-instructor-deactivate", RESULTS_DIRECTORY_REGEX, structuralWithoutConstructor);
        dockerClientTestService.mockInputStreamReturnedFromContainer(dockerClient, "mc-student-deactivate", RESULTS_DIRECTORY_REGEX, behaviorResults());
        localCITriggerService.triggerBuild(solutionParticipation, false);
        awaitFinalizedResultAfter(solutionParticipation.getId(), firstSubmission.getLatestResult().getId(), 120);

        // only the removed test is deactivated
        Set<String> remainingActive = new HashSet<>(union(STRUCTURAL_TEST_NAMES, BEHAVIOR_TEST_NAMES));
        remainingActive.remove("testConstructors[Policy]");
        assertThat(testCaseRepository.findByExerciseIdAndActive(programmingExercise.getId(), true)).extracting(testCase -> testCase.getTestName())
                .containsExactlyInAnyOrderElementsOf(remainingActive);
        assertThat(testCaseRepository.findByExerciseIdAndActive(programmingExercise.getId(), false)).extracting(testCase -> testCase.getTestName())
                .contains("testConstructors[Policy]");
    }

    /** A solution build with a crashed container skips the reconciliation, so no test case is deactivated. */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testSolutionBuildWithACrashedContainerKeepsItsTestCasesActive() throws Exception {
        String instructorImage = "mc-instructor:keep";
        String studentImage = "mc-student:keep";
        configureTwoContainerPlan(instructorImage, studentImage);
        mockContainerLifecycle(instructorImage, "mc-instructor-keep");
        mockContainerLifecycle(studentImage, "mc-student-keep");

        // First solution build: every test case is registered across the two containers.
        dockerClientTestService.mockInputStreamReturnedFromContainer(dockerClient, "mc-instructor-keep", RESULTS_DIRECTORY_REGEX, structuralResults());
        dockerClientTestService.mockInputStreamReturnedFromContainer(dockerClient, "mc-student-keep", RESULTS_DIRECTORY_REGEX, behaviorResults());
        solutionRepository = localVCLocalCITestService.createRepositoryWithWorkingCopy(projectKey1, solutionRepositorySlug);
        localVCLocalCITestService.commitFile(solutionRepository.workingCopyPath(), solutionRepository.workingCopy());
        solutionRepository.workingCopy().push().call();
        localCITriggerService.triggerBuild(solutionParticipation, false);
        ProgrammingSubmission firstSubmission = awaitFinalizedResult(solutionParticipation.getId(), 120);
        Set<String> allTestNames = union(STRUCTURAL_TEST_NAMES, BEHAVIOR_TEST_NAMES);
        assertThat(testCaseRepository.findByExerciseIdAndActive(programmingExercise.getId(), true)).extracting(testCase -> testCase.getTestName())
                .containsExactlyInAnyOrderElementsOf(allTestNames);

        // Second build: the student container crashes and reports no tests at all.
        mockScriptExitCode("mc-student-keep", "mc-student-keep-exec", 1L);
        mockMissingResults("mc-student-keep");
        localCITriggerService.triggerBuild(solutionParticipation, false);
        ProgrammingSubmission secondSubmission = awaitFinalizedResultAfter(solutionParticipation.getId(), firstSubmission.getLatestResult().getId(), 120);

        assertThat(secondSubmission.isBuildFailed()).isTrue();
        assertThat(testCaseRepository.findByExerciseIdAndActive(programmingExercise.getId(), true)).extracting(testCase -> testCase.getTestName())
                .containsExactlyInAnyOrderElementsOf(allTestNames);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testContainerResultsMergeIntoOneFinalizedResultEndToEnd() throws Exception {
        String instructorImage = "mc-instructor:happy";
        String studentImage = "mc-student:happy";
        configureTwoContainerPlan(instructorImage, studentImage);
        mockContainerLifecycle(instructorImage, "mc-instructor-happy");
        mockContainerLifecycle(studentImage, "mc-student-happy");
        dockerClientTestService.mockInputStreamReturnedFromContainer(dockerClient, "mc-instructor-happy", RESULTS_DIRECTORY_REGEX, structuralResults());
        dockerClientTestService.mockInputStreamReturnedFromContainer(dockerClient, "mc-student-happy", RESULTS_DIRECTORY_REGEX, behaviorResults());

        ProgrammingExerciseStudentParticipation participation = localVCLocalCITestService.createParticipation(programmingExercise, student1Login);
        // The spy is not among the beans reset between tests, so only the calls of this build are counted.
        clearInvocations(programmingMessagingService);
        processNewPush();

        ProgrammingSubmission submission = awaitFinalizedResult(participation.getId(), 120);

        // the student is notified once, with the completed result
        verify(programmingMessagingService, never()).notifyUserAboutNewResult(argThat(reported -> reported.getCompletionDate() == null), any());
        verify(programmingMessagingService, timeout(2000).times(1)).notifyUserAboutNewResult(argThat(reported -> reported.getCompletionDate() != null), any());
        // the report reads every feedback row's message, so reaching the student shows they were loaded whole
        verify(websocketMessagingService, timeout(2000).atLeastOnce()).sendMessageToUser(eq(student1Login), eq(NEW_RESULTS.at()), any());

        assertThat(programmingSubmissionRepository.findAllByParticipationIdWithResults(participation.getId())).hasSize(1);
        assertThat(submission.isBuildFailed()).isFalse();
        assertThat(submission.getResults()).hasSize(1);

        Result result = resultRepository.findByIdWithEagerFeedbacksElseThrow(submission.getLatestResult().getId());
        Set<String> expectedNames = union(STRUCTURAL_TEST_NAMES, BEHAVIOR_TEST_NAMES);
        assertThat(feedbackTestNames(result)).containsExactlyInAnyOrderElementsOf(expectedNames);
        // the fixture is partly successful, so the merged result is scored but, as on the single-container path, not successful
        assertThat(result.isSuccessful()).isFalse();
        assertThat(result.getScore()).isNotNull();

        assertThat(buildJobRepository.countByResultId(result.getId())).isEqualTo(2);
        var jobs = buildJobRepository.findAll().stream().filter(job -> Objects.equals(job.getParticipationId(), participation.getId())).toList();
        assertThat(jobs).hasSize(2);
        assertThat(jobs).allSatisfy(job -> assertThat(job.getBuildStatus()).isEqualTo(BuildStatus.SUCCESSFUL));
        assertThat(jobs).extracting(job -> job.getDockerImage()).containsExactlyInAnyOrder(instructorImage, studentImage);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testInstructorResultsPreservedWhenStudentContainerCrashesEndToEnd() throws Exception {
        String instructorImage = "mc-instructor:crash";
        String studentImage = "mc-student:crash";
        configureTwoContainerPlan(instructorImage, studentImage);
        mockContainerLifecycle(instructorImage, "mc-instructor-crash");
        mockContainerLifecycle(studentImage, "mc-student-crash");
        dockerClientTestService.mockInputStreamReturnedFromContainer(dockerClient, "mc-instructor-crash", RESULTS_DIRECTORY_REGEX, structuralResults());
        // The student container's build script dies with a non-zero exit code and leaves no test results behind.
        mockScriptExitCode("mc-student-crash", "mc-student-crash-exec", 1L);
        mockMissingResults("mc-student-crash");

        ProgrammingExerciseStudentParticipation participation = localVCLocalCITestService.createParticipation(programmingExercise, student1Login);
        processNewPush();

        ProgrammingSubmission submission = awaitFinalizedResult(participation.getId(), 120);

        assertResultPreservedAfterStudentContainerFailure(participation, submission, instructorImage, studentImage);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testInstructorResultsPreservedWhenStudentContainerIsOomKilledEndToEnd() throws Exception {
        String instructorImage = "mc-instructor:oom";
        String studentImage = "mc-student:oom";
        configureTwoContainerPlan(instructorImage, studentImage);
        mockContainerLifecycle(instructorImage, "mc-instructor-oom");
        mockContainerLifecycle(studentImage, "mc-student-oom");
        dockerClientTestService.mockInputStreamReturnedFromContainer(dockerClient, "mc-instructor-oom", RESULTS_DIRECTORY_REGEX, structuralResults());
        // An out-of-memory kill surfaces to the agent as exit code 137 (SIGKILL by the kernel) and no test results.
        mockScriptExitCode("mc-student-oom", "mc-student-oom-exec", 137L);
        mockMissingResults("mc-student-oom");

        ProgrammingExerciseStudentParticipation participation = localVCLocalCITestService.createParticipation(programmingExercise, student1Login);
        processNewPush();

        ProgrammingSubmission submission = awaitFinalizedResult(participation.getId(), 120);

        assertResultPreservedAfterStudentContainerFailure(participation, submission, instructorImage, studentImage);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testInstructorResultsPreservedWhenStudentContainerTimesOutEndToEnd() throws Exception {
        String instructorImage = "mc-instructor:timeout";
        String studentImage = "mc-student:timeout";
        // The build configuration is stored apart from the exercise, so it is read after the two-container plan was saved:
        // an instance read before would write the previous plan back together with the timeout below.
        configureTwoContainerPlan(instructorImage, studentImage);
        ProgrammingExerciseBuildConfig buildConfig = programmingExerciseBuildConfigRepository.getProgrammingExerciseBuildConfigElseThrow(programmingExercise.getId());
        int originalTimeout = buildConfig.getTimeoutSeconds();
        try (ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1)) {
            mockContainerLifecycle(instructorImage, "mc-instructor-timeout");
            mockContainerLifecycle(studentImage, "mc-student-timeout");
            dockerClientTestService.mockInputStreamReturnedFromContainer(dockerClient, "mc-instructor-timeout", RESULTS_DIRECTORY_REGEX, structuralResults());

            // only the student container's commands hang past 20 s; the instructor job needs that long for its real git clones
            buildConfig.setTimeoutSeconds(20);
            programmingExerciseBuildConfigRepository.save(buildConfig);
            mockHangingExec("mc-student-timeout", "mc-student-timeout-exec", scheduler);

            ProgrammingExerciseStudentParticipation participation = localVCLocalCITestService.createParticipation(programmingExercise, student1Login);
            processNewPush();

            ProgrammingSubmission submission = awaitFinalizedResult(participation.getId(), 180);

            Result result = resultRepository.findByIdWithEagerFeedbacksElseThrow(submission.getLatestResult().getId());
            assertThat(feedbackTestNames(result)).containsExactlyInAnyOrderElementsOf(STRUCTURAL_TEST_NAMES);
            assertThat(result.getCompletionDate()).isNotNull();
            assertThat(result.isSuccessful()).isFalse();
            assertThat(submission.isBuildFailed()).isTrue();

            assertThat(buildJobRepository.countByResultId(result.getId())).isEqualTo(2);
            var jobs = buildJobRepository.findAll().stream().filter(job -> Objects.equals(job.getParticipationId(), participation.getId())).toList();
            assertThat(jobs).hasSize(2);
            assertThat(statusOfJobWithImage(jobs, studentImage)).isEqualTo(BuildStatus.TIMEOUT);
            assertThat(statusOfJobWithImage(jobs, instructorImage)).isEqualTo(BuildStatus.SUCCESSFUL);
        }
        finally {
            buildConfig.setTimeoutSeconds(originalTimeout);
            programmingExerciseBuildConfigRepository.save(buildConfig);
        }
    }

    private void assertResultPreservedAfterStudentContainerFailure(ProgrammingExerciseStudentParticipation participation, ProgrammingSubmission submission, String instructorImage,
            String studentImage) {
        Result result = resultRepository.findByIdWithEagerFeedbacksElseThrow(submission.getLatestResult().getId());
        assertThat(feedbackTestNames(result)).containsExactlyInAnyOrderElementsOf(STRUCTURAL_TEST_NAMES);
        assertThat(result.getCompletionDate()).isNotNull();
        assertThat(result.isSuccessful()).isFalse();
        assertThat(submission.isBuildFailed()).isTrue();

        assertThat(buildJobRepository.countByResultId(result.getId())).isEqualTo(2);
        var jobs = buildJobRepository.findAll().stream().filter(job -> Objects.equals(job.getParticipationId(), participation.getId())).toList();
        assertThat(jobs).hasSize(2);
        assertThat(jobs).extracting(job -> job.getDockerImage()).containsExactlyInAnyOrder(instructorImage, studentImage);

        // The failed container's logs are preserved for the student, labeled with the container that produced them.
        var buildLogs = buildLogEntryService.getLatestBuildLogs(submission);
        assertThat(buildLogs).isNotEmpty();
        assertThat(buildLogs).allSatisfy(buildLogEntry -> assertThat(buildLogEntry.getContainerName()).isEqualTo(STUDENT_CONTAINER));
    }

    // -------------------------------------------------------------------------------------------------
    // Setup helpers
    // -------------------------------------------------------------------------------------------------

    private void processNewPush() {
        localVCServletService.processNewPush(commitHash, studentAssignmentRepository.bareRepository().getRepository(), userTestRepository.getUserWithAuthorities(),
                Optional.empty(), Optional.empty(), Optional.empty());
    }

    /**
     * Configures the exercise with a plan of two containers: the instructor container additionally checks out the test
     * repository, the student container is scoped to the assignment repository only.
     */
    private void configureTwoContainerPlan(String instructorImage, String studentImage) throws JacksonException {
        BuildPhaseDTO instructorPhase = new BuildPhaseDTO("instructor_phase", "gradle test", BuildPhaseCondition.ALWAYS, false, List.of("build/test-results/test/*.xml"));
        BuildPhaseDTO studentPhase = new BuildPhaseDTO("student_phase", "gradle test", BuildPhaseCondition.ALWAYS, false, List.of("build/test-results/test/*.xml"));
        BuildContainerDTO instructorContainer = new BuildContainerDTO(INSTRUCTOR_CONTAINER, instructorImage, List.of(new BuildContainerRepositoryDTO(RepositoryType.TESTS)),
                List.of(instructorPhase));
        BuildContainerDTO studentContainer = new BuildContainerDTO(STUDENT_CONTAINER, studentImage, List.of(), List.of(studentPhase));
        ProgrammingExerciseBuildConfig buildConfig = programmingExerciseBuildConfigRepository.getProgrammingExerciseBuildConfigElseThrow(programmingExercise.getId());
        buildConfig.setBuildPlanConfiguration(new BuildPlanPhasesDTO(null, null, List.of(instructorContainer, studentContainer)).toBuildPlanConfiguration());
        programmingExerciseBuildConfigRepository.save(buildConfig);
    }

    /**
     * Maps the given image to its own Docker container id and gives that container its own commit-hash file streams, so
     * the two container jobs of one submission do not consume each other's mocked streams.
     */
    private void mockContainerLifecycle(String image, String containerId) throws IOException {
        DockerClientTestService.mockCreateContainerCmd(dockerClient, containerId, image);
        dockerClientTestService.mockInputStreamReturnedFromContainer(dockerClient, containerId, LOCAL_CI_DOCKER_CONTAINER_WORKING_DIRECTORY + "/testing-dir/.git/refs/heads/[^/]+",
                Map.of("testCommitHash", DUMMY_COMMIT_HASH), Map.of("testCommitHash", DUMMY_COMMIT_HASH));
        dockerClientTestService.mockInputStreamReturnedFromContainer(dockerClient, containerId,
                LOCAL_CI_DOCKER_CONTAINER_WORKING_DIRECTORY + "/testing-dir/assignment/.git/refs/heads/[^/]+", Map.of("commitHash", commitHash), Map.of("commitHash", commitHash));
    }

    /**
     * Replaces the exec chain of the given container with one whose commands report the given exit code. Only the build
     * script's exit code is ever read back (setup commands discard it), so this effectively sets the script's exit code.
     */
    private void mockScriptExitCode(String containerId, String execId, long exitCode) {
        ExecCreateCmd execCreateCmd = mock(ExecCreateCmd.class);
        ExecCreateCmdResponse execCreateCmdResponse = mock(ExecCreateCmdResponse.class);
        when(dockerClient.execCreateCmd(eq(containerId))).thenReturn(execCreateCmd);
        when(execCreateCmd.withCmd(any(String[].class))).thenReturn(execCreateCmd);
        when(execCreateCmd.withCmd(anyString(), anyString())).thenReturn(execCreateCmd);
        when(execCreateCmd.withCmd(anyString(), anyString(), anyString())).thenReturn(execCreateCmd);
        when(execCreateCmd.withUser(anyString())).thenReturn(execCreateCmd);
        when(execCreateCmd.withAttachStdout(anyBoolean())).thenReturn(execCreateCmd);
        when(execCreateCmd.withAttachStderr(anyBoolean())).thenReturn(execCreateCmd);
        when(execCreateCmd.exec()).thenReturn(execCreateCmdResponse);
        when(execCreateCmdResponse.getId()).thenReturn(execId);

        ExecStartCmd execStartCmd = mock(ExecStartCmd.class);
        when(dockerClient.execStartCmd(eq(execId))).thenReturn(execStartCmd);
        when(execStartCmd.withDetach(anyBoolean())).thenReturn(execStartCmd);
        when(execStartCmd.exec(any())).thenAnswer(invocation -> {
            ResultCallback.Adapter<?> callback = invocation.getArgument(0);
            callback.onComplete();
            return null;
        });

        InspectExecCmd inspectExecCmd = mock(InspectExecCmd.class);
        InspectExecResponse inspectExecResponse = mock(InspectExecResponse.class);
        when(dockerClient.inspectExecCmd(eq(execId))).thenReturn(inspectExecCmd);
        when(inspectExecCmd.exec()).thenReturn(inspectExecResponse);
        when(inspectExecResponse.getExitCodeLong()).thenReturn(exitCode);
    }

    /**
     * Makes every command in the given container hang for 45 seconds before completing, so the job running the
     * container hits the instructor-configured build timeout. The delay sits inside the timed part of the job (command
     * execution), unlike an image-inspection delay, which the build timeout deliberately does not cover.
     */
    private void mockHangingExec(String containerId, String execId, ScheduledExecutorService scheduler) {
        ExecCreateCmd execCreateCmd = mock(ExecCreateCmd.class);
        ExecCreateCmdResponse execCreateCmdResponse = mock(ExecCreateCmdResponse.class);
        when(dockerClient.execCreateCmd(eq(containerId))).thenReturn(execCreateCmd);
        when(execCreateCmd.withCmd(any(String[].class))).thenReturn(execCreateCmd);
        when(execCreateCmd.withCmd(anyString(), anyString())).thenReturn(execCreateCmd);
        when(execCreateCmd.withCmd(anyString(), anyString(), anyString())).thenReturn(execCreateCmd);
        when(execCreateCmd.withUser(anyString())).thenReturn(execCreateCmd);
        when(execCreateCmd.withAttachStdout(anyBoolean())).thenReturn(execCreateCmd);
        when(execCreateCmd.withAttachStderr(anyBoolean())).thenReturn(execCreateCmd);
        when(execCreateCmd.exec()).thenReturn(execCreateCmdResponse);
        when(execCreateCmdResponse.getId()).thenReturn(execId);

        ExecStartCmd execStartCmd = mock(ExecStartCmd.class);
        when(dockerClient.execStartCmd(eq(execId))).thenReturn(execStartCmd);
        when(execStartCmd.withDetach(anyBoolean())).thenReturn(execStartCmd);
        when(execStartCmd.exec(any())).thenAnswer(invocation -> {
            ResultCallback.Adapter<?> callback = invocation.getArgument(0);
            scheduler.schedule(callback::onComplete, 45, TimeUnit.SECONDS);
            return null;
        });
    }

    /** The given container has no test results to collect, as after a crashed or killed build script. */
    private void mockMissingResults(String containerId) {
        CopyArchiveFromContainerCmd copyArchiveFromContainerCmd = mock(CopyArchiveFromContainerCmd.class);
        ArgumentMatcher<String> resultsDirectoryMatcher = path -> path != null && path.matches(RESULTS_DIRECTORY_REGEX);
        doReturn(copyArchiveFromContainerCmd).when(dockerClient).copyArchiveFromContainerCmd(eq(containerId), argThat(resultsDirectoryMatcher));
        doThrow(new NotFoundException("no test results in container " + containerId)).when(copyArchiveFromContainerCmd).exec();
    }

    private Map<String, String> structuralResults() throws IOException {
        return dockerClientTestService.createMapFromTestResultsFolder(PARTLY_SUCCESSFUL_TEST_RESULTS_PATH).entrySet().stream()
                .filter(entry -> !entry.getKey().contains("SortingExampleBehaviorTest")).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private Map<String, String> behaviorResults() throws IOException {
        return dockerClientTestService.createMapFromTestResultsFolder(PARTLY_SUCCESSFUL_TEST_RESULTS_PATH).entrySet().stream()
                .filter(entry -> entry.getKey().contains("SortingExampleBehaviorTest")).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /** The single constructor test file ({@code testConstructors[Policy]}), used to make two containers report one test case. */
    private Map<String, String> constructorTestResult() throws IOException {
        return dockerClientTestService.createMapFromTestResultsFolder(PARTLY_SUCCESSFUL_TEST_RESULTS_PATH).entrySet().stream()
                .filter(entry -> entry.getKey().contains("ConstructorTest")).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    // -------------------------------------------------------------------------------------------------
    // Assertion helpers
    // -------------------------------------------------------------------------------------------------

    /**
     * Waits until the submission's latest result is finalized (completion date set) and returns the submission.
     * Uncaught exceptions of unrelated background threads (e.g. the SSH server's session teardown on Windows) must not
     * abort the wait, hence {@code dontCatchUncaughtExceptions()}.
     */
    private ProgrammingSubmission awaitFinalizedResult(long participationId, int timeoutInSeconds) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        await().dontCatchUncaughtExceptions().atMost(Duration.ofSeconds(timeoutInSeconds)).until(() -> {
            SecurityContextHolder.getContext().setAuthentication(auth);
            return programmingSubmissionRepository.findFirstByParticipationIdWithResultsOrderBySubmissionDateDesc(participationId)
                    .map(submission -> submission.getLatestResult() != null && submission.getLatestResult().getCompletionDate() != null).orElse(false);
        });
        return programmingSubmissionRepository.findFirstByParticipationIdWithResultsOrderBySubmissionDateDesc(participationId).orElseThrow();
    }

    /** Waits until the participation's latest result is finalized and newer than the given result, e.g. after a rebuild. */
    private ProgrammingSubmission awaitFinalizedResultAfter(long participationId, long previousResultId, int timeoutInSeconds) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        await().dontCatchUncaughtExceptions().atMost(Duration.ofSeconds(timeoutInSeconds)).until(() -> {
            SecurityContextHolder.getContext().setAuthentication(auth);
            return programmingSubmissionRepository.findFirstByParticipationIdWithResultsOrderBySubmissionDateDesc(participationId).map(ProgrammingSubmission::getLatestResult)
                    .map(latest -> latest.getId() > previousResultId && latest.getCompletionDate() != null).orElse(false);
        });
        return programmingSubmissionRepository.findFirstByParticipationIdWithResultsOrderBySubmissionDateDesc(participationId).orElseThrow();
    }

    /** Waits until the build's jobs link to the given manual result and it is the latest result again. */
    private ProgrammingSubmission awaitBuildMergedInto(long participationId, long manualResultId, int containerCount, int timeoutInSeconds) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        await().dontCatchUncaughtExceptions().atMost(Duration.ofSeconds(timeoutInSeconds)).until(() -> {
            SecurityContextHolder.getContext().setAuthentication(auth);
            long linkedJobs = buildJobRepository.findAll().stream().filter(job -> job.getParticipationId() == participationId)
                    .filter(job -> job.getResult() != null && job.getResult().getId() == manualResultId).count();
            boolean manualResultIsLatest = programmingSubmissionRepository.findFirstByParticipationIdWithResultsOrderBySubmissionDateDesc(participationId)
                    .map(ProgrammingSubmission::getLatestResult).map(latest -> latest.getId() == manualResultId).orElse(false);
            return linkedJobs >= containerCount && manualResultIsLatest;
        });
        return programmingSubmissionRepository.findFirstByParticipationIdWithResultsOrderBySubmissionDateDesc(participationId).orElseThrow();
    }

    /** Waits until the participation has at least the given number of build jobs that are no longer queued or building. */
    private void awaitFinishedBuildJobs(long participationId, int count, int timeoutInSeconds) {
        await().dontCatchUncaughtExceptions().atMost(Duration.ofSeconds(timeoutInSeconds))
                .until(() -> buildJobRepository.findAll().stream().filter(job -> job.getParticipationId() == participationId)
                        .filter(job -> job.getBuildStatus() != BuildStatus.QUEUED && job.getBuildStatus() != BuildStatus.BUILDING).count() >= count);
    }

    /** The test cases a result reports as executed; a placeholder row marks a registered test the build did not execute. */
    private Set<String> feedbackTestNames(Result result) {
        return testCaseFeedbacksOf(result).stream().filter(feedback -> !"Test was not executed.".equals(feedback.getMessageText()))
                .map(feedback -> feedback.getTestCase().getTestName()).collect(Collectors.toSet());
    }

    /** The stored test-case feedback rows of a result, with their test cases and messages loaded. */
    private List<TestCaseFeedback> testCaseFeedbacksOf(Result result) {
        return testCaseFeedbackRepository.findWithTestCaseAndMessageByResultId(result.getId());
    }

    private BuildStatus statusOfJobWithImage(List<BuildJob> jobs, String dockerImage) {
        return jobs.stream().filter(job -> dockerImage.equals(job.getDockerImage())).findFirst().orElseThrow().getBuildStatus();
    }

    private static Set<String> union(Set<String> first, Set<String> second) {
        var union = new HashSet<>(first);
        union.addAll(second);
        return Set.copyOf(union);
    }
}
