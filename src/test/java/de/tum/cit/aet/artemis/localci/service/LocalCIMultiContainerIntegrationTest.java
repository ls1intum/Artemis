package de.tum.cit.aet.artemis.localci.service;

import static de.tum.cit.aet.artemis.core.config.Constants.LOCAL_CI_DOCKER_CONTAINER_WORKING_DIRECTORY;
import static de.tum.cit.aet.artemis.core.config.Constants.LOCAL_CI_RESULTS_DIRECTORY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.CopyArchiveFromContainerCmd;
import com.github.dockerjava.api.command.ExecCreateCmd;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.command.ExecStartCmd;
import com.github.dockerjava.api.command.InspectExecCmd;
import com.github.dockerjava.api.command.InspectExecResponse;
import com.github.dockerjava.api.exception.NotFoundException;

import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.localci.domain.BuildJob;
import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationLocalCILocalVCTestBase;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildConfig;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.domain.build.BuildPhaseCondition;
import de.tum.cit.aet.artemis.programming.domain.build.BuildStatus;
import de.tum.cit.aet.artemis.programming.dto.BuildContainerDTO;
import de.tum.cit.aet.artemis.programming.dto.BuildContainerRepositoryDTO;
import de.tum.cit.aet.artemis.programming.dto.BuildPhaseDTO;
import de.tum.cit.aet.artemis.programming.dto.BuildPlanPhasesDTO;
import de.tum.cit.aet.artemis.programming.util.LocalRepository;

/**
 * End-to-end tests for multi-container build plans that run the full path a submission takes: a push to the assignment
 * repository triggers one build job per configured container, the in-process build agent executes each job against the
 * mocked Docker client, and the result processing on the core merges the per-container results into a single finalized
 * result for the submission.
 * <p>
 * This covers the glue that the service-level merge tests in {@link LocalCIResultServiceIntegrationTest} bypass:
 * scheduling, agent execution, the result queue, and the locked, transactional aggregation in
 * {@code LocalCIResultProcessingService#processContainerResult}. The failure-mode tests verify the feedback guarantee
 * that motivates the multi-container design: the results a container has already delivered survive a sibling
 * container's crash, out-of-memory kill, or timeout.
 */
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

    private LocalRepository studentAssignmentRepository;

    private LocalRepository testsRepository;

    private String commitHash;

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
        studentAssignmentRepository = localVCLocalCITestService.createAndConfigureLocalRepository(projectKey1, assignmentRepositorySlug);
        commitHash = localVCLocalCITestService.commitFile(studentAssignmentRepository.workingCopyGitRepoFile.toPath(), studentAssignmentRepository.workingCopyGitRepo);
        studentAssignmentRepository.workingCopyGitRepo.push().call();

        testsRepository = localVCLocalCITestService.createAndConfigureLocalRepository(projectKey1, testsRepositorySlug);
        localVCLocalCITestService.commitFile(testsRepository.workingCopyGitRepoFile.toPath(), testsRepository.workingCopyGitRepo);
        testsRepository.workingCopyGitRepo.push().call();

        dockerClientTestService.mockInspectImage(dockerClient);
    }

    @AfterEach
    void removeRepositories() throws IOException {
        studentAssignmentRepository.resetLocalRepo();
        testsRepository.resetLocalRepo();
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
        processNewPush();

        ProgrammingSubmission submission = awaitFinalizedResult(participation.getId(), 120);

        // The containers of one commit share a single submission and a single result.
        assertThat(programmingSubmissionRepository.findAllByParticipationIdWithResults(participation.getId())).hasSize(1);
        assertThat(submission.getExpectedContainerCount()).isEqualTo(2);
        assertThat(submission.isBuildFailed()).isFalse();
        assertThat(submission.getResults()).hasSize(1);

        // The finalized result carries the feedback of both containers.
        Result result = resultRepository.findByIdWithEagerFeedbacksElseThrow(submission.getLatestResult().getId());
        Set<String> expectedNames = union(STRUCTURAL_TEST_NAMES, BEHAVIOR_TEST_NAMES);
        assertThat(feedbackTestNames(result)).containsExactlyInAnyOrderElementsOf(expectedNames);
        assertThat(result.isSuccessful()).isTrue();
        assertThat(result.getScore()).isNotNull();

        // One build job per container, both linked to the shared result.
        assertThat(buildJobRepository.countByResultId(result.getId())).isEqualTo(2);
        var jobs = buildJobRepository.findAll().stream().filter(job -> job.getParticipationId() == participation.getId()).toList();
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
        ProgrammingExerciseBuildConfig buildConfig = programmingExercise.getBuildConfig();
        int originalTimeout = buildConfig.getTimeoutSeconds();
        try (ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1)) {
            configureTwoContainerPlan(instructorImage, studentImage);
            mockContainerLifecycle(instructorImage, "mc-instructor-timeout");
            mockContainerLifecycle(studentImage, "mc-student-timeout");
            dockerClientTestService.mockInputStreamReturnedFromContainer(dockerClient, "mc-instructor-timeout", RESULTS_DIRECTORY_REGEX, structuralResults());

            // The instructor-configured timeout applies to each container job individually: only the student container's
            // commands hang past the timeout, so only the student job runs into it. The delay must sit inside the timed
            // part of the job — the time spent pulling and inspecting the image is deliberately excluded from the build
            // timeout — and the timeout must leave the instructor job enough room for its real git clones in this harness.
            buildConfig.setTimeoutSeconds(20);
            programmingExerciseBuildConfigRepository.save(buildConfig);
            mockHangingExec("mc-student-timeout", "mc-student-timeout-exec", scheduler);

            ProgrammingExerciseStudentParticipation participation = localVCLocalCITestService.createParticipation(programmingExercise, student1Login);
            processNewPush();

            ProgrammingSubmission submission = awaitFinalizedResult(participation.getId(), 180);

            // The instructor container's feedback reaches the student although the sibling job timed out.
            Result result = resultRepository.findByIdWithEagerFeedbacksElseThrow(submission.getLatestResult().getId());
            assertThat(feedbackTestNames(result)).containsExactlyInAnyOrderElementsOf(STRUCTURAL_TEST_NAMES);
            assertThat(result.getCompletionDate()).isNotNull();
            assertThat(result.isSuccessful()).isFalse();
            assertThat(submission.isBuildFailed()).isTrue();

            // The timed-out job is recorded as such; the sibling job is untouched by the timeout.
            assertThat(buildJobRepository.countByResultId(result.getId())).isEqualTo(2);
            var jobs = buildJobRepository.findAll().stream().filter(job -> job.getParticipationId() == participation.getId()).toList();
            assertThat(jobs).hasSize(2);
            assertThat(statusOfJobWithImage(jobs, studentImage)).isEqualTo(BuildStatus.TIMEOUT);
            assertThat(statusOfJobWithImage(jobs, instructorImage)).isEqualTo(BuildStatus.SUCCESSFUL);
        }
        finally {
            buildConfig.setTimeoutSeconds(originalTimeout);
            programmingExerciseBuildConfigRepository.save(buildConfig);
        }
    }

    /**
     * The shared assertions of the crash and out-of-memory scenarios: the aggregated result still finalizes, the
     * instructor container's feedback is preserved, the submission is marked as failed, and the failed container's
     * build logs are labeled with its name.
     */
    private void assertResultPreservedAfterStudentContainerFailure(ProgrammingExerciseStudentParticipation participation, ProgrammingSubmission submission, String instructorImage,
            String studentImage) {
        Result result = resultRepository.findByIdWithEagerFeedbacksElseThrow(submission.getLatestResult().getId());
        assertThat(feedbackTestNames(result)).containsExactlyInAnyOrderElementsOf(STRUCTURAL_TEST_NAMES);
        assertThat(result.getCompletionDate()).isNotNull();
        // A result of a submission with a failed container must not present itself as successful.
        assertThat(result.isSuccessful()).isFalse();
        assertThat(submission.isBuildFailed()).isTrue();

        assertThat(buildJobRepository.countByResultId(result.getId())).isEqualTo(2);
        var jobs = buildJobRepository.findAll().stream().filter(job -> job.getParticipationId() == participation.getId()).toList();
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
        localVCServletService.processNewPush(commitHash, studentAssignmentRepository.remoteBareGitRepo.getRepository(), userTestRepository.getUserWithAuthorities(),
                Optional.empty(), Optional.empty(), Optional.empty());
    }

    /**
     * Configures the exercise with a plan of two containers: the instructor container additionally checks out the test
     * repository, the student container is scoped to the assignment repository only.
     */
    private void configureTwoContainerPlan(String instructorImage, String studentImage) throws JsonProcessingException {
        BuildPhaseDTO instructorPhase = new BuildPhaseDTO("instructor_phase", "gradle test", BuildPhaseCondition.ALWAYS, false, List.of("build/test-results/test/*.xml"));
        BuildPhaseDTO studentPhase = new BuildPhaseDTO("student_phase", "gradle test", BuildPhaseCondition.ALWAYS, false, List.of("build/test-results/test/*.xml"));
        BuildContainerDTO instructorContainer = new BuildContainerDTO(INSTRUCTOR_CONTAINER, instructorImage, List.of(new BuildContainerRepositoryDTO(RepositoryType.TESTS)),
                List.of(instructorPhase));
        BuildContainerDTO studentContainer = new BuildContainerDTO(STUDENT_CONTAINER, studentImage, List.of(), List.of(studentPhase));
        ProgrammingExerciseBuildConfig buildConfig = programmingExercise.getBuildConfig();
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

    /**
     * The names of the test cases a container actually delivered feedback for. Finalizing the result adds a
     * "Test was not executed." placeholder for every registered test case without feedback, so those placeholders are
     * excluded here — they mark the absence of a container's results, not their delivery.
     */
    private Set<String> feedbackTestNames(Result result) {
        return result.getFeedbacks().stream().filter(feedback -> !feedback.isStaticCodeAnalysisFeedback())
                .filter(feedback -> !"Test was not executed.".equals(feedback.getDetailText()))
                .map(feedback -> feedback.getTestCase() != null ? feedback.getTestCase().getTestName() : feedback.getText()).collect(Collectors.toSet());
    }

    private BuildStatus statusOfJobWithImage(List<BuildJob> jobs, String dockerImage) {
        return jobs.stream().filter(job -> dockerImage.equals(job.getDockerImage())).findFirst().orElseThrow().getBuildStatus();
    }

    private static Set<String> union(Set<String> first, Set<String> second) {
        var union = new java.util.HashSet<>(first);
        union.addAll(second);
        return Set.copyOf(union);
    }
}
