package de.tum.cit.aet.artemis.buildagent.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentDTO;
import de.tum.cit.aet.artemis.buildagent.dto.BuildJobQueueItem;
import de.tum.cit.aet.artemis.localci.exception.DockerImagePullException;
import de.tum.cit.aet.artemis.programming.domain.build.BuildStatus;

class SharedQueueProcessingServiceTest {

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
