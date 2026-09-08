package de.tum.cit.aet.artemis.buildagent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.buildagent.BuildAgentConfiguration;
import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentDTO;
import de.tum.cit.aet.artemis.buildagent.dto.BuildJobQueueItem;
import de.tum.cit.aet.artemis.buildagent.service.runner.BuildJobRunner;
import de.tum.cit.aet.artemis.localci.exception.DockerImagePullException;
import de.tum.cit.aet.artemis.localci.exception.LocalCIException;
import de.tum.cit.aet.artemis.localci.service.DistributedDataAccessService;
import de.tum.cit.aet.artemis.programming.domain.build.BuildStatus;

class SharedQueueProcessingServiceTest {

    @Test
    void failedResumeRemainsPausedAndCanBeRetried() {
        BuildAgentConfiguration configuration = mock(BuildAgentConfiguration.class);
        org.mockito.Mockito.doThrow(new LocalCIException("still stopping")).when(configuration).openBuildAgentServices();
        SharedQueueProcessingService service = new SharedQueueProcessingService(configuration, mock(BuildJobManagementService.class), mock(BuildLogsMap.class),
                mock(TaskScheduler.class), mock(BuildJobRunner.class), mock(BuildAgentInformationService.class), mock(DistributedDataAccessService.class));
        service.setPauseState(true);

        ReflectionTestUtils.invokeMethod(service, "resumeBuildAgent");
        ReflectionTestUtils.invokeMethod(service, "resumeBuildAgent");

        assertThat(service.isPaused()).isTrue();
        verify(configuration, times(2)).openBuildAgentServices();
    }

    @Test
    void pauseWaitsForAdmittedGenerationCreateAndThenRejectsNewCreates() throws InterruptedException {
        SharedQueueProcessingService service = new SharedQueueProcessingService(mock(BuildAgentConfiguration.class), mock(BuildJobManagementService.class),
                mock(BuildLogsMap.class), mock(TaskScheduler.class), mock(BuildJobRunner.class), mock(BuildAgentInformationService.class),
                mock(DistributedDataAccessService.class));

        assertThat(service.tryAcquireGenerationAdmission()).isTrue();
        CountDownLatch pauseStarted = new CountDownLatch(1);
        AtomicReference<Thread> pauseThread = new AtomicReference<>();
        CompletableFuture<Void> pause = CompletableFuture.runAsync(() -> {
            pauseThread.set(Thread.currentThread());
            pauseStarted.countDown();
            service.setPauseState(true);
        });
        try {
            assertThat(pauseStarted.await(2, TimeUnit.SECONDS)).isTrue();
            org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(2)).until(() -> pauseThread.get().getState() == Thread.State.WAITING);
            assertThat(pause).isNotDone();
        }
        finally {
            service.releaseGenerationAdmission();
        }

        assertThat(pause).succeedsWithin(Duration.ofSeconds(2));
        assertThat(service.tryAcquireGenerationAdmission()).isFalse();
    }

    @Test
    void shouldPublishCurrentAttempt() {
        BuildJobQueueItem current = buildJob(1, null, "agent-1");
        BuildJobQueueItem finished = buildJob(1, BuildStatus.SUCCESSFUL, "agent-1");

        assertThat(SharedQueueProcessingService.shouldPublishResult(current, finished)).isTrue();
    }

    @Test
    void shouldPublishCancellationRemovedByCoordinatingNode() {
        BuildJobQueueItem cancelled = buildJob(1, BuildStatus.CANCELLED, "agent-1");

        assertThat(SharedQueueProcessingService.shouldPublishResult(null, cancelled)).isTrue();
    }

    @Test
    void shouldDiscardResultOfSupersededAttempt() {
        BuildJobQueueItem replacement = buildJob(2, null, "agent-2");
        BuildJobQueueItem finishedOldAttempt = buildJob(1, BuildStatus.SUCCESSFUL, "agent-1");

        assertThat(SharedQueueProcessingService.shouldPublishResult(replacement, finishedOldAttempt)).isFalse();
    }

    @Test
    void shouldDiscardCancellationWhenReplacementAttemptExists() {
        BuildJobQueueItem replacement = buildJob(2, null, "agent-2");
        BuildJobQueueItem cancelledOldAttempt = buildJob(1, BuildStatus.CANCELLED, "agent-1");

        assertThat(SharedQueueProcessingService.shouldPublishResult(replacement, cancelledOldAttempt)).isFalse();
    }

    @Test
    void shouldPublishCompletionWhenExternalCancellationAlreadyRemovedProcessingEntry() {
        BuildJobQueueItem finished = buildJob(1, BuildStatus.SUCCESSFUL, "agent-1");

        assertThat(SharedQueueProcessingService.shouldPublishResult(null, finished)).isTrue();
    }

    @Test
    void internalRequeueWinsCompletionRaceForTheExactAttempt() {
        BuildJobQueueItem current = buildJob(1, null, "agent-1");
        BuildJobQueueItem replacement = buildJob(2, null, "");
        var attemptState = new SharedQueueProcessingService.BuildAttemptState(current);

        assertThat(attemptState.requestInternalRequeue(replacement)).isTrue();
        assertThat(attemptState.beginCompletion()).isTrue();
        assertThat(attemptState.requeuedBuildJob()).isSameAs(replacement);
        assertThat(attemptState.requestInternalRequeue(replacement)).isFalse();
    }

    @Test
    void normalCompletionWinsRaceBeforeInternalRequeueClaim() {
        BuildJobQueueItem current = buildJob(1, null, "agent-1");
        var attemptState = new SharedQueueProcessingService.BuildAttemptState(current);

        assertThat(attemptState.beginCompletion()).isFalse();
        assertThat(attemptState.requestInternalRequeue(buildJob(2, null, ""))).isFalse();
    }

    @Test
    void recognizesTypedDockerImagePullFailureThroughFutureWrappers() {
        Throwable failure = new CompletionException(new ExecutionException(new DockerImagePullException("pull failed", new IllegalStateException("registry unavailable"))));

        assertThat(SharedQueueProcessingService.isCausedByImagePullFailedException(failure)).isTrue();
        assertThat(SharedQueueProcessingService.isCausedByImagePullFailedException(new CompletionException(new IllegalStateException("other failure")))).isFalse();
    }

    private BuildJobQueueItem buildJob(int retryCount, BuildStatus status, String agentName) {
        return new BuildJobQueueItem("job-1", "job", new BuildAgentDTO(agentName, "address", agentName), 1, 2, 3, retryCount, 1, status, null, null, null, null);
    }
}
