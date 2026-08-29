package de.tum.cit.aet.artemis.buildagent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.buildagent.BuildAgentConfiguration;
import de.tum.cit.aet.artemis.buildagent.dto.BuildJobQueueItem;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.DistributedMap;
import de.tum.cit.aet.artemis.core.service.distributed.api.queue.DistributedQueue;
import de.tum.cit.aet.artemis.localci.service.DistributedDataAccessService;

/**
 * Pausing a build agent while a job is still being submitted.
 * <p>
 * A job is registered as running before its public future exists, so for a short window the agent reports a running job
 * that nothing can be awaited on. The pause has to notice that and cancel the job anyway: skipping it left the build
 * running on an agent whose services were about to be closed, and the student saw the resulting build timeout as a
 * failed build.
 * <p>
 * A unit test rather than an integration test on purpose. The window is shorter than a millisecond, so the only way to
 * hit it in a running agent is to hold the submission open artificially; driving the pause against a build job
 * management service that reports exactly this state tests the same branch without a sleep in production code.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SharedQueueProcessingServicePauseTest {

    private static final String JOB_ID = "job-being-submitted";

    private static final String AGENT_NAME = "test-agent";

    @Mock
    private BuildAgentConfiguration buildAgentConfiguration;

    @Mock
    private BuildJobManagementService buildJobManagementService;

    @Mock
    private BuildAgentInformationService buildAgentInformationService;

    @Mock
    private DistributedDataAccessService distributedDataAccessService;

    @Mock
    private DistributedQueue<BuildJobQueueItem> buildJobQueue;

    @Mock
    private DistributedMap<String, BuildJobQueueItem> processingJobs;

    @Mock
    private BuildJobQueueItem runningJob;

    private SharedQueueProcessingService service;

    @BeforeEach
    void setUp() {
        service = new SharedQueueProcessingService(buildAgentConfiguration, buildJobManagementService, null, null, null, null, buildAgentInformationService,
                distributedDataAccessService);
        ReflectionTestUtils.setField(service, "buildAgentShortName", AGENT_NAME);
        ReflectionTestUtils.setField(service, "pauseGracePeriodSeconds", 1);
        ReflectionTestUtils.setField(service, "isPaused", new AtomicBoolean(false));

        when(distributedDataAccessService.getLocalMemberAddress()).thenReturn("localhost");
        when(distributedDataAccessService.isInstanceRunning()).thenReturn(false);
        when(distributedDataAccessService.getDistributedBuildJobQueue()).thenReturn(buildJobQueue);
        when(distributedDataAccessService.getDistributedProcessingJobs()).thenReturn(processingJobs);
        lenient().when(processingJobs.getAll(anySet())).thenReturn(Map.of(JOB_ID, runningJob));
    }

    private void pause() {
        ReflectionTestUtils.invokeMethod(service, "pauseBuildAgent", false);
    }

    @Test
    void cancelsAndRequeuesAJobThatIsStillBeingSubmitted() {
        // Registered as running, but its public future has not been published yet - the window this guards.
        when(buildJobManagementService.getRunningBuildJobIds()).thenReturn(Set.of(JOB_ID));
        when(buildJobManagementService.getRunningBuildJobFutureWrapper(JOB_ID)).thenReturn(null);
        when(buildJobManagementService.cancelBuildJob(JOB_ID)).thenReturn(true);

        pause();

        verify(buildJobManagementService).cancelBuildJob(JOB_ID);
        ArgumentCaptor<List<BuildJobQueueItem>> requeued = ArgumentCaptor.captor();
        verify(buildJobQueue).addAll(requeued.capture());
        assertThat(requeued.getValue()).as("the job has to go back on the queue so another agent picks it up").containsExactly(runningJob);
    }

    @Test
    void doesNotRequeueAJobThatFinishedBeforeItCouldBeCancelled() {
        when(buildJobManagementService.getRunningBuildJobIds()).thenReturn(Set.of(JOB_ID));
        when(buildJobManagementService.getRunningBuildJobFutureWrapper(JOB_ID)).thenReturn(null);
        // The job completed between the snapshot and the cancellation, so there was nothing left to stop.
        when(buildJobManagementService.cancelBuildJob(JOB_ID)).thenReturn(false);

        pause();

        verify(buildJobQueue, never()).addAll(any());
    }

    @Test
    void leavesAJobAloneWhenItsFutureCompletedDuringTheGracePeriod() {
        when(buildJobManagementService.getRunningBuildJobIds()).thenReturn(Set.of(JOB_ID));
        when(buildJobManagementService.getRunningBuildJobFutureWrapper(JOB_ID)).thenReturn(CompletableFuture.completedFuture(null));

        pause();

        verify(buildJobManagementService, never()).cancelBuildJob(anyString());
        verify(buildJobQueue, never()).addAll(any());
    }

    @Test
    void doesNotCloseTheServicesWhenTheAgentWasResumedWhilePausing() {
        when(buildJobManagementService.getRunningBuildJobIds()).thenReturn(Set.of());
        // A resume completed while the pause was between releasing the transition lock and closing the services.
        doAnswer(invocation -> {
            ((AtomicBoolean) ReflectionTestUtils.getField(service, "isPaused")).set(false);
            return null;
        }).when(buildAgentInformationService).updateLocalBuildAgentInformation(anyBoolean(), anyBoolean(), anyInt());

        pause();

        verify(buildAgentConfiguration, never()).closeBuildAgentServices();
    }
}
