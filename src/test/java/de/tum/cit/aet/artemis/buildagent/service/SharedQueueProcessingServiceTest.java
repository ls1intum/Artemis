package de.tum.cit.aet.artemis.buildagent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import java.util.UUID;
import java.util.concurrent.ScheduledFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.TaskScheduler;

import de.tum.cit.aet.artemis.buildagent.BuildAgentConfiguration;
import de.tum.cit.aet.artemis.programming.service.localci.DistributedDataAccessService;
import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.queue.DistributedQueue;
import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.topic.DistributedTopic;

/**
 * Unit tests for {@link SharedQueueProcessingService#pauseForMaintenance()} and
 * {@link SharedQueueProcessingService#resumeFromMaintenance()}. The class has many collaborators but the surface we
 * care about for this test is small: the wrappers must propagate the boolean transition signal from
 * {@code pauseBuildAgent}, and any exception thrown after {@code isPaused} has flipped must trigger a rollback so
 * the agent does not silently leak the pause.
 */
class SharedQueueProcessingServiceTest {

    private BuildAgentConfiguration buildAgentConfiguration;

    private BuildJobManagementService buildJobManagementService;

    private BuildLogsMap buildLogsMap;

    private TaskScheduler taskScheduler;

    private BuildAgentDockerService buildAgentDockerService;

    private BuildJobContainerService buildJobContainerService;

    private BuildAgentInformationService buildAgentInformationService;

    private DistributedDataAccessService distributedDataAccessService;

    private SharedQueueProcessingService service;

    @BeforeEach
    void setUp() {
        buildAgentConfiguration = mock(BuildAgentConfiguration.class);
        buildJobManagementService = mock(BuildJobManagementService.class);
        buildLogsMap = mock(BuildLogsMap.class);
        taskScheduler = mock(TaskScheduler.class);
        buildAgentDockerService = mock(BuildAgentDockerService.class);
        buildJobContainerService = mock(BuildJobContainerService.class);
        buildAgentInformationService = mock(BuildAgentInformationService.class);
        distributedDataAccessService = mock(DistributedDataAccessService.class);

        // Pre-stub the distributed surface that resumeBuildAgent touches so the resume path completes without NPE
        // when a test exercises it. Tests that need other behaviour override these lenient stubs.
        DistributedQueue queue = mock(DistributedQueue.class);
        lenient().when(distributedDataAccessService.getDistributedBuildJobQueue()).thenReturn(queue);
        lenient().when(queue.addItemListener(any())).thenReturn(UUID.randomUUID());
        DistributedTopic topic = mock(DistributedTopic.class);
        lenient().when(distributedDataAccessService.getPauseBuildAgentTopic()).thenReturn(topic);
        lenient().when(distributedDataAccessService.getResumeBuildAgentTopic()).thenReturn(topic);
        lenient().when(distributedDataAccessService.getCanceledBuildJobsTopic()).thenReturn(topic);
        lenient().when(distributedDataAccessService.isInstanceRunning()).thenReturn(false);
        lenient().when(taskScheduler.scheduleAtFixedRate(any(Runnable.class), any(java.time.Duration.class))).thenReturn(mock(ScheduledFuture.class));

        service = new SharedQueueProcessingService(buildAgentConfiguration, buildJobManagementService, buildLogsMap, taskScheduler, buildAgentDockerService,
                buildJobContainerService, buildAgentInformationService, distributedDataAccessService);
    }

    @Test
    void pauseForMaintenanceReturnsTrueWhenItActuallyTransitionsAgentToPaused() {
        assertThat(service.isPaused()).isFalse();

        boolean transitioned = service.pauseForMaintenance();

        assertThat(transitioned).isTrue();
        assertThat(service.isPaused()).isTrue();
    }

    @Test
    void pauseForMaintenanceIsIdempotentAndReturnsFalseWhenAlreadyPaused() {
        // First call transitions; second call observes already-paused and reports false.
        service.pauseForMaintenance();

        boolean secondCallTransitioned = service.pauseForMaintenance();

        assertThat(secondCallTransitioned).isFalse();
        assertThat(service.isPaused()).isTrue();
    }

    @Test
    void pauseForMaintenanceRollsBackIfCollaboratorThrowsAfterFlagFlip() {
        // Simulate a Hazelcast hiccup during the distributed status update — buildAgentInformationService.update is
        // invoked AFTER isPaused.set(true). Without the rollback, the agent would be left paused with no caller
        // owning the pause; the wrapper's catch block must call resumeBuildAgent (which sets isPaused=false) before
        // rethrowing the original exception.
        doThrow(new RuntimeException("simulated Hazelcast write failure")).when(buildAgentInformationService).updateLocalBuildAgentInformation(anyBoolean(), anyBoolean(),
                anyInt());

        assertThatThrownBy(() -> service.pauseForMaintenance()).isInstanceOf(RuntimeException.class).hasMessageContaining("simulated Hazelcast write failure");

        // The critical invariant: agent is back to un-paused even though the pause attempt crashed mid-way.
        assertThat(service.isPaused()).isFalse();
    }

    @Test
    void resumeFromMaintenanceIsANoOpWhenAgentIsAlreadyRunning() {
        assertThat(service.isPaused()).isFalse();

        // Must not throw and must leave the state unchanged.
        service.resumeFromMaintenance();

        assertThat(service.isPaused()).isFalse();
    }

    @Test
    void resumeFromMaintenanceTransitionsBackToRunning() {
        service.pauseForMaintenance();
        assertThat(service.isPaused()).isTrue();

        service.resumeFromMaintenance();

        assertThat(service.isPaused()).isFalse();
    }
}
