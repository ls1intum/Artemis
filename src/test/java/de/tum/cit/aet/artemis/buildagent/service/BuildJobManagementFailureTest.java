package de.tum.cit.aet.artemis.buildagent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.buildagent.BuildAgentConfiguration;
import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentDTO;
import de.tum.cit.aet.artemis.buildagent.dto.BuildConfig;
import de.tum.cit.aet.artemis.buildagent.dto.BuildJobQueueItem;
import de.tum.cit.aet.artemis.buildagent.dto.BuildLogDTO;
import de.tum.cit.aet.artemis.buildagent.dto.BuildResult;
import de.tum.cit.aet.artemis.buildagent.dto.DockerRunConfig;
import de.tum.cit.aet.artemis.buildagent.dto.JobTimingInfo;
import de.tum.cit.aet.artemis.buildagent.dto.RepositoryInfo;
import de.tum.cit.aet.artemis.buildagent.service.runner.BuildJobRunner;
import de.tum.cit.aet.artemis.core.service.distributed.api.topic.DistributedTopic;
import de.tum.cit.aet.artemis.localci.service.DistributedDataAccessService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;

class BuildJobManagementFailureTest {

    private final DistributedDataAccessService distributed = mock(DistributedDataAccessService.class);

    private final BuildJobExecutionService execution = mock(BuildJobExecutionService.class);

    private final BuildAgentConfiguration configuration = mock(BuildAgentConfiguration.class);

    private final BuildJobRunner runner = mock(BuildJobRunner.class);

    private final BuildLogsMap logs = mock(BuildLogsMap.class);

    private final TaskScheduler scheduler = mock(TaskScheduler.class);

    private final ThreadPoolExecutor buildExecutor = mock(ThreadPoolExecutor.class);

    private final BuildJobManagementService service = new BuildJobManagementService(distributed, execution, configuration, runner, logs, scheduler);

    @BeforeEach
    void setUp() {
        when(configuration.getBuildExecutor()).thenReturn(buildExecutor);
        doAnswer(call -> {
            ((Runnable) call.getArgument(0)).run();
            return null;
        }).when(buildExecutor).execute(any());
        ReflectionTestUtils.setField(service, "timeoutSeconds", 60);
        ReflectionTestUtils.setField(service, "runBuildJobsAsynchronously", false);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { "tar archive corrupt", "Could not copy to container", "Could not create tar", "Failed to retrieve archive", "ordinary execution failure" })
    void failedExecutionCancelsRunnerAndExplainsInfrastructureFailures(String message) {
        when(execution.runBuildJob(any())).thenThrow(new IllegalStateException(message));
        CompletableFuture<BuildResult> result = service.executeBuildJob(job());
        assertThat(result).isCompletedExceptionally();
        assertThatThrownBy(result::join).hasRootCauseInstanceOf(IllegalStateException.class);
        var captured = ArgumentCaptor.forClass(BuildLogDTO.class);
        verify(logs).appendBuildLogEntry(eq("job"), captured.capture());
        boolean infrastructure = message != null && (message.contains("tar") || message.contains("container") || message.contains("archive"));
        if (infrastructure) {
            assertThat(captured.getValue().log()).isEqualTo("Build failed due to a temporary infrastructure issue while preparing the build environment. "
                    + "This is not caused by your code. Please try rerunning your build.");
        }
        else {
            assertThat(captured.getValue().log()).contains("Error while executing build job job:");
        }
        verify(runner).cancel("job");
        assertThat(service.getRunningBuildJobIds()).containsExactly("job");
        assertThat(service.getRunningBuildJobFutureWrapper("job")).isSameAs(result);
        service.releaseBuildJob(result);
        service.releaseBuildJob(result);
        assertThat(service.getRunningBuildJobIds()).isEmpty();
        assertThat(service.getRunningBuildJobFutureWrapper("job")).isNull();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { "tar failed", "IOException while reading", "other error" })
    void deeplyNestedFailurePreservesDiagnosticsWithoutClassifyingUnrelatedOuterFailureAsInfrastructure(String causeMessage) {
        when(execution.runBuildJob(any())).thenThrow(new IllegalStateException("unrelated execution failure", new RuntimeException(causeMessage)));
        var result = service.executeBuildJob(job());
        assertThat(result).isCompletedExceptionally();
        var captured = ArgumentCaptor.forClass(BuildLogDTO.class);
        verify(logs).appendBuildLogEntry(eq("job"), captured.capture());
        assertThat(captured.getValue().log()).contains("Error while executing build job job:", "unrelated execution failure");
        if (causeMessage != null && !causeMessage.isEmpty()) {
            assertThat(captured.getValue().log()).contains(causeMessage);
        }
        assertThat(captured.getValue().log()).doesNotContain("Build failed due to a temporary infrastructure issue");
        service.releaseBuildJob(result);
    }

    @Test
    void retriesDisconnectedInitializationAndReplacesCancelListenerOnReconnection() {
        AtomicReference<Consumer<Boolean>> listener = new AtomicReference<>();
        doAnswer(call -> {
            listener.set(call.getArgument(0));
            return UUID.randomUUID();
        }).when(distributed).addConnectionStateListener(any());
        List<Runnable> retries = new ArrayList<>();
        ScheduledFuture<?> scheduled = mock(ScheduledFuture.class);
        doAnswer(call -> {
            retries.add(call.getArgument(0));
            return scheduled;
        }).when(scheduler).scheduleAtFixedRate(any(), any(Duration.class));
        @SuppressWarnings("unchecked")
        DistributedTopic<String> cancel = mock(DistributedTopic.class);
        when(distributed.getCanceledBuildJobsTopic()).thenReturn(cancel);
        UUID firstId = UUID.randomUUID();
        when(cancel.addMessageListener(any())).thenReturn(firstId, UUID.randomUUID());
        service.init();
        assertThat(retries).hasSize(1);
        listener.get().accept(false);
        assertThat(retries).hasSize(1);
        retries.getFirst().run();
        when(distributed.isConnectedToCluster()).thenReturn(true);
        retries.getFirst().run();
        verify(scheduled).cancel(false);
        listener.get().accept(true);
        listener.get().accept(false);
        verify(cancel).removeMessageListener(firstId);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Consumer<String>> callback = ArgumentCaptor.forClass(Consumer.class);
        verify(cancel, org.mockito.Mockito.times(2)).addMessageListener(callback.capture());
        callback.getValue().accept("unowned-job");
        verify(runner, never()).cancel(any());
    }

    @Test
    void earlyCancellationDoesNotRunCallableAndPreventsImmediateResubmission() {
        ReflectionTestUtils.setField(service, "runBuildJobsAsynchronously", true);
        ThreadPoolExecutor resultExecutor = mock(ThreadPoolExecutor.class);
        when(configuration.getBuildResultExecutor()).thenReturn(resultExecutor);
        List<Runnable> queuedBuilds = new ArrayList<>();
        List<Runnable> queuedResults = new ArrayList<>();
        doAnswer(call -> {
            queuedBuilds.add(call.getArgument(0));
            return null;
        }).when(buildExecutor).execute(any());
        doAnswer(call -> {
            queuedResults.add(call.getArgument(0));
            return null;
        }).when(resultExecutor).execute(any());
        var future = service.executeBuildJob(job());
        assertThat(service.cancelBuildJob("job")).isTrue();
        assertThat(service.cancelBuildJob("job")).isTrue();
        assertThatThrownBy(() -> service.executeBuildJob(job())).isInstanceOf(CompletionException.class).hasMessageContaining("cancelled before it was submitted");
        queuedBuilds.getFirst().run();
        queuedResults.getFirst().run();
        assertThat(future).isCompletedExceptionally();
        verify(execution, never()).runBuildJob(any());
        service.releaseBuildJob(future);
        assertThat(service.cancelBuildJob("missing")).isFalse();
    }

    private BuildJobQueueItem job() {
        var repositories = new RepositoryInfo("repository", RepositoryType.USER, RepositoryType.USER, "assignment", "tests", null, new String[0], new String[0]);
        var timing = new JobTimingInfo(ZonedDateTime.now(), null, null, null, 60);
        var config = new BuildConfig("script", "image", "commit", "assignment", "tests", "main", ProgrammingLanguage.JAVA, null, false, false, List.of(), 60, "assignment", "tests",
                "solution", new DockerRunConfig(List.of(), null, 0, 0, 0));
        return new BuildJobQueueItem("job", "job", new BuildAgentDTO("agent", "address", "agent"), 1, 2, 3, 0, 1, null, repositories, timing, config, null);
    }
}
