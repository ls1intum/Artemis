package de.tum.cit.aet.artemis.buildagent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.buildagent.BuildAgentConfiguration;
import de.tum.cit.aet.artemis.buildagent.dto.BuildConfig;
import de.tum.cit.aet.artemis.buildagent.dto.BuildJobQueueItem;
import de.tum.cit.aet.artemis.buildagent.dto.BuildResult;
import de.tum.cit.aet.artemis.buildagent.dto.RepositoryInfo;
import de.tum.cit.aet.artemis.buildagent.service.runner.BuildJobRunner;
import de.tum.cit.aet.artemis.localci.service.DistributedDataAccessService;

class BuildJobManagementServiceTest {

    private final ThreadPoolExecutor buildExecutor = executor();

    private final ThreadPoolExecutor resultExecutor = executor();

    @AfterEach
    void tearDown() {
        buildExecutor.shutdownNow();
        resultExecutor.shutdownNow();
    }

    @Test
    void cancellationCompletionWaitsUntilBuildCallableActuallyExits() throws Exception {
        BuildJobExecutionService executionService = mock(BuildJobExecutionService.class);
        BuildJobRunner runner = mock(BuildJobRunner.class);
        BuildJobQueueItem buildJob = buildJob();
        CountDownLatch callableStarted = new CountDownLatch(1);
        CountDownLatch interruptionObserved = new CountDownLatch(1);
        CountDownLatch allowCallableExit = new CountDownLatch(1);
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
