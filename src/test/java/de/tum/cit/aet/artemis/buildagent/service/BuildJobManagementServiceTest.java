package de.tum.cit.aet.artemis.buildagent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.buildagent.BuildAgentConfiguration;
import de.tum.cit.aet.artemis.buildagent.dto.BuildConfig;
import de.tum.cit.aet.artemis.buildagent.dto.BuildJobQueueItem;
import de.tum.cit.aet.artemis.buildagent.dto.BuildResult;
import de.tum.cit.aet.artemis.buildagent.dto.RepositoryInfo;
import de.tum.cit.aet.artemis.buildagent.service.runner.BuildJobRunner;
import de.tum.cit.aet.artemis.localci.service.DistributedDataAccessService;

/**
 * Covers two things the build job manager has to get right: the build timeout must not count the time spent pulling a
 * Docker image, and a cancelled attempt stays owned until the callable it started has actually exited.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BuildJobManagementServiceTest {

    private static final String BUILD_JOB_ID = "build-job-1";

    /**
     * Kept well below the pull duration so that a build budget exhausted by the pull would fail the test.
     */
    private static final int BUILD_TIMEOUT_SECONDS = 2;

    @Mock
    private BuildJobRunner buildJobRunner;

    private BuildJobManagementService buildJobManagementService;

    private ExecutorService executor;

    private final ThreadPoolExecutor buildExecutor = executor();

    private final ThreadPoolExecutor resultExecutor = executor();

    /**
     * Latches that keep a build callable parked, so a failing assertion cannot leave it blocked forever.
     * <p>
     * Interrupting through shutdownNow is not a reliable release signal here: the callable deliberately swallows the
     * first interrupt to prove that cancellation waits for a real exit, so an assertion failing before the test
     * releases the latch itself would leave the executor thread parked and hold up the whole suite.
     */
    private final List<CountDownLatch> latchesToRelease = new ArrayList<>();

    @AfterEach
    void tearDown() {
        latchesToRelease.forEach(CountDownLatch::countDown);
        latchesToRelease.clear();
        buildExecutor.shutdownNow();
        resultExecutor.shutdownNow();
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    private void setUpService() {
        executor = Executors.newSingleThreadExecutor();
        buildJobManagementService = new BuildJobManagementService(null, null, null, buildJobRunner, null, null);
    }

    @Test
    void awaitBuildResult_doesNotTimeOutWhileTheImageIsStillBeingPulled() throws Exception {
        setUpService();

        // The pull takes longer than the whole build budget, but only ~200 ms of the wait happens outside the pull.
        long pullEndNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        when(buildJobRunner.isFetchingImage(BUILD_JOB_ID)).thenAnswer(invocation -> System.nanoTime() < pullEndNanos);

        BuildResult expectedResult = someBuildResult();
        Future<BuildResult> future = executor.submit(() -> {
            Thread.sleep(3200);
            return expectedResult;
        });

        BuildResult result = buildJobManagementService.awaitBuildResult(future, BUILD_JOB_ID, BUILD_TIMEOUT_SECONDS);

        assertThat(result).isSameAs(expectedResult);
        assertThat(future).isNotCancelled();
    }

    @Test
    void awaitBuildResult_timesOutWhenNoImagePullIsInProgress() {
        setUpService();

        when(buildJobRunner.isFetchingImage(BUILD_JOB_ID)).thenReturn(false);

        Future<BuildResult> future = executor.submit(() -> {
            Thread.sleep(30_000);
            return someBuildResult();
        });

        // Without a pull to exclude, the build budget applies in full and the wait has to give up.
        assertThatThrownBy(() -> buildJobManagementService.awaitBuildResult(future, BUILD_JOB_ID, BUILD_TIMEOUT_SECONDS)).isInstanceOf(TimeoutException.class);
    }

    @Test
    void awaitBuildResult_returnsImmediatelyForAnAlreadyCompletedBuild() throws Exception {
        setUpService();

        BuildResult expectedResult = someBuildResult();
        Future<BuildResult> future = executor.submit(() -> expectedResult);

        assertThat(buildJobManagementService.awaitBuildResult(future, BUILD_JOB_ID, BUILD_TIMEOUT_SECONDS)).isSameAs(expectedResult);
    }

    private static BuildResult someBuildResult() {
        return new BuildResult("main", "abc123", "def456", List.of(), true);
    }

    @Test
    void cancellationCompletionWaitsUntilBuildCallableActuallyExits() throws Exception {
        BuildJobExecutionService executionService = mock(BuildJobExecutionService.class);
        BuildJobRunner runner = mock(BuildJobRunner.class);
        BuildJobQueueItem buildJob = buildJob();
        CountDownLatch callableStarted = new CountDownLatch(1);
        CountDownLatch interruptionObserved = new CountDownLatch(1);
        CountDownLatch allowCallableExit = new CountDownLatch(1);
        latchesToRelease.add(allowCallableExit);
        CountDownLatch callableExited = new CountDownLatch(1);
        when(executionService.runBuildJob(buildJob)).thenAnswer(invocation -> {
            callableStarted.countDown();
            try {
                try {
                    allowCallableExit.await();
                }
                catch (InterruptedException ignored) {
                    interruptionObserved.countDown();
                    allowCallableExit.await();
                }
                return mock(BuildResult.class);
            }
            finally {
                callableExited.countDown();
            }
        });
        BuildJobManagementService service = service(executionService, runner);

        CompletableFuture<BuildResult> result = service.executeBuildJob(buildJob);
        assertThat(callableStarted.await(5, TimeUnit.SECONDS)).isTrue();
        CountDownLatch terminalCallback = new CountDownLatch(1);
        result.whenComplete((ignoredResult, ignoredFailure) -> terminalCallback.countDown());

        service.cancelBuildJob(buildJob.id());

        assertThat(interruptionObserved.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(terminalCallback.await(200, TimeUnit.MILLISECONDS)).isFalse();
        assertThat(callableExited.getCount()).isOne();

        allowCallableExit.countDown();
        assertThat(callableExited.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(terminalCallback.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(result).isCompletedExceptionally();
        verify(runner, atLeastOnce()).cancel(buildJob.id());
        service.releaseBuildJob(result);
    }

    @Test
    void completedAttemptRemainsOwnedUntilTerminalHandlerReleasesIt() throws Exception {
        BuildJobExecutionService executionService = mock(BuildJobExecutionService.class);
        BuildJobRunner runner = mock(BuildJobRunner.class);
        BuildJobQueueItem buildJob = buildJob();
        BuildResult buildResult = mock(BuildResult.class);
        when(executionService.runBuildJob(buildJob)).thenReturn(buildResult);
        BuildJobManagementService service = service(executionService, runner);

        CompletableFuture<BuildResult> result = service.executeBuildJob(buildJob);
        assertThat(result.get(5, TimeUnit.SECONDS)).isSameAs(buildResult);
        assertThat(service.getRunningBuildJobIds()).containsExactly(buildJob.id());

        service.cancelBuildJob(buildJob.id());

        assertThat(result).isCompletedWithValue(buildResult);
        verify(runner, never()).cancel(buildJob.id());

        service.releaseBuildJob(result);
        assertThat(service.getRunningBuildJobIds()).isEmpty();
    }

    @Test
    void refusesToSubmitAJobWhenTheBuildResultExecutorIsGone() {
        BuildJobExecutionService executionService = mock(BuildJobExecutionService.class);
        BuildJobRunner runner = mock(BuildJobRunner.class);
        BuildJobQueueItem buildJob = buildJob();
        BuildJobManagementService service = service(executionService, runner);
        // What a pause leaves behind. Submitting anyway would start a build that nothing waits for, so its public
        // future would never complete and the queue processor would never release the attempt.
        resultExecutor.shutdownNow();

        assertThatThrownBy(() -> service.executeBuildJob(buildJob)).isInstanceOf(RejectedExecutionException.class);

        assertThat(service.getRunningBuildJobIds()).isEmpty();
        assertThat(buildExecutor.getTaskCount()).isZero();
    }

    private BuildJobManagementService service(BuildJobExecutionService executionService, BuildJobRunner runner) {
        BuildAgentConfiguration configuration = mock(BuildAgentConfiguration.class);
        when(configuration.getBuildExecutor()).thenReturn(buildExecutor);
        when(configuration.getBuildResultExecutor()).thenReturn(resultExecutor);
        var service = new BuildJobManagementService(mock(DistributedDataAccessService.class), executionService, configuration, runner, mock(BuildLogsMap.class),
                mock(TaskScheduler.class));
        ReflectionTestUtils.setField(service, "runBuildJobsAsynchronously", true);
        ReflectionTestUtils.setField(service, "timeoutSeconds", 60);
        return service;
    }

    private static BuildJobQueueItem buildJob() {
        BuildJobQueueItem buildJob = mock(BuildJobQueueItem.class);
        BuildConfig buildConfig = mock(BuildConfig.class);
        RepositoryInfo repositoryInfo = mock(RepositoryInfo.class);
        when(buildJob.id()).thenReturn("job-id");
        when(buildJob.buildConfig()).thenReturn(buildConfig);
        when(buildConfig.timeoutSeconds()).thenReturn(30);
        when(buildJob.repositoryInfo()).thenReturn(repositoryInfo);
        when(repositoryInfo.assignmentRepositoryUri()).thenReturn("http://localhost/repository.git");
        return buildJob;
    }

    private static ThreadPoolExecutor executor() {
        return new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(1));
    }
}
