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

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

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
import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentDTO;
import de.tum.cit.aet.artemis.buildagent.dto.BuildJobQueueItem;
import de.tum.cit.aet.artemis.buildagent.dto.JobTimingInfo;
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

    @Mock
    private BuildLogsMap buildLogsMap;

    private SharedQueueProcessingService service;

    /** The processing entries the distributed map is made to answer with, keyed by build job id. */
    private final Map<String, BuildJobQueueItem> registeredProcessingJobs = new HashMap<>();

    @BeforeEach
    void setUp() {
        // The nulls are the task scheduler and the build job runner, neither of which the pause path reaches. The
        // second one replaced the Docker service and container service this test used to pass separately.
        service = new SharedQueueProcessingService(buildAgentConfiguration, buildJobManagementService, buildLogsMap, null, null, buildAgentInformationService,
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
        BuildJobQueueItem job = registerActiveAttempt(0);
        when(buildJobManagementService.getRunningBuildJobIds()).thenReturn(Set.of(JOB_ID));
        when(buildJobManagementService.getRunningBuildJobFutureWrapper(JOB_ID)).thenReturn(null);

        pause();

        verify(buildJobManagementService).cancelBuildJob(JOB_ID);
        // The replacement attempt is claimed on the local attempt state and the distributed entry is dropped, which is
        // what lets another agent take the job; the queue write itself follows when the cancelled attempt completes.
        verify(processingJobs).remove(JOB_ID);
        assertThat(attemptStateFor(JOB_ID).requeuedBuildJob()).as("the replacement has to carry the next retry of the same job")
                .extracting(BuildJobQueueItem::id, BuildJobQueueItem::retryCount).containsExactly(job.id(), job.retryCount() + 1);
    }

    @Test
    void doesNotRequeueAJobThatFinishedBeforeItCouldBeCancelled() {
        // The job completed between the snapshot and the cancellation, so there is nothing left to stop: claiming the
        // completion first is exactly what the running job does when its result arrives.
        registerActiveAttempt(0);
        attemptStateFor(JOB_ID).beginCompletion();
        when(buildJobManagementService.getRunningBuildJobIds()).thenReturn(Set.of(JOB_ID));
        when(buildJobManagementService.getRunningBuildJobFutureWrapper(JOB_ID)).thenReturn(null);

        pause();

        verify(processingJobs, never()).remove(JOB_ID);
        verify(buildJobQueue, never()).addAll(any());
    }

    /**
     * Puts a job on the agent the way {@code processBuild} does, so the pause finds a local attempt to act on.
     *
     * @param retryCount which attempt this is
     * @return the job that was registered
     */
    private BuildJobQueueItem registerActiveAttempt(int retryCount) {
        return registerActiveAttempt(JOB_ID, retryCount);
    }

    /**
     * Puts a job with the given id on the agent, for the cases that need more than one running attempt.
     *
     * @param buildJobId the id to register the attempt under
     * @param retryCount which attempt this is
     * @return the job that was registered
     */
    private BuildJobQueueItem registerActiveAttempt(String buildJobId, int retryCount) {
        JobTimingInfo timingInfo = new JobTimingInfo(ZonedDateTime.now(), ZonedDateTime.now(), null, null, 0);
        BuildJobQueueItem job = new BuildJobQueueItem(buildJobId, "job", new BuildAgentDTO(AGENT_NAME, "address", AGENT_NAME), 1, 2, 3, retryCount, 1, null, null, timingInfo, null,
                null);
        activeBuildAttempts().put(buildJobId, new SharedQueueProcessingService.BuildAttemptState(job));
        registeredProcessingJobs.put(buildJobId, job);
        lenient().when(processingJobs.getAll(anySet())).thenAnswer(invocation -> ((Set<String>) invocation.getArgument(0)).stream().filter(registeredProcessingJobs::containsKey)
                .collect(Collectors.toMap(id -> id, registeredProcessingJobs::get)));
        lenient().when(processingJobs.get(buildJobId)).thenReturn(job);
        return job;
    }

    @SuppressWarnings("unchecked")
    private Map<String, SharedQueueProcessingService.BuildAttemptState> activeBuildAttempts() {
        return (Map<String, SharedQueueProcessingService.BuildAttemptState>) ReflectionTestUtils.getField(service, "activeBuildAttempts");
    }

    private SharedQueueProcessingService.BuildAttemptState attemptStateFor(String buildJobId) {
        return activeBuildAttempts().get(buildJobId);
    }

    private AtomicInteger localProcessingJobs() {
        return (AtomicInteger) ReflectionTestUtils.getField(service, "localProcessingJobs");
    }

    /** A queue item for this agent, without registering a local attempt for it. */
    private BuildJobQueueItem buildJob(int retryCount) {
        JobTimingInfo timingInfo = new JobTimingInfo(ZonedDateTime.now(), ZonedDateTime.now(), null, null, 0);
        return new BuildJobQueueItem(JOB_ID, "job", new BuildAgentDTO(AGENT_NAME, "address", AGENT_NAME), 1, 2, 3, retryCount, 1, null, null, timingInfo, null, null);
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
    void leavesTheJobsOfAnAgentThatWasResumedWhilePausingAlone() {
        when(buildJobManagementService.getRunningBuildJobIds()).thenReturn(Set.of(JOB_ID));
        when(buildJobManagementService.getRunningBuildJobFutureWrapper(JOB_ID)).thenReturn(null);
        resumeWhileThePauseIsInProgress();

        pause();

        // The resumed agent was told to keep running these jobs, so cancelling and re-queueing them here would throw
        // away builds that are legitimately in flight.
        verify(buildJobManagementService, never()).cancelBuildJob(anyString());
        verify(buildJobQueue, never()).addAll(any());
        verify(buildAgentConfiguration, never()).closeBuildAgentServices();
    }

    private void resumeWhileThePauseIsInProgress() {
        doAnswer(invocation -> {
            ((AtomicBoolean) ReflectionTestUtils.getField(service, "isPaused")).set(false);
            return null;
        }).when(buildAgentInformationService).updateLocalBuildAgentInformation(anyBoolean(), anyBoolean(), anyInt());
    }

    @Test
    void onlyCancelsTheRunningJobsThatCouldNotBeAwaited() {
        // Every awaited job finished, so only the ones the pause never had a future for may be cancelled. Cancelling an
        // awaited one as well would re-queue a build whose result is already on its way to being published.
        String awaitedJobId = "job-with-a-future";
        registerActiveAttempt(0);
        registerActiveAttempt(awaitedJobId, 0);
        when(buildJobManagementService.getRunningBuildJobIds()).thenReturn(Set.of(JOB_ID, awaitedJobId));
        when(buildJobManagementService.getRunningBuildJobFutureWrapper(JOB_ID)).thenReturn(null);
        when(buildJobManagementService.getRunningBuildJobFutureWrapper(awaitedJobId)).thenReturn(CompletableFuture.completedFuture(null));

        pause();

        verify(buildJobManagementService).cancelBuildJob(JOB_ID);
        verify(processingJobs).remove(JOB_ID);
        verify(buildJobManagementService, never()).cancelBuildJob(awaitedJobId);
        verify(processingJobs, never()).remove(awaitedJobId);
    }

    @Test
    void queuesTheReplacementWhenTheSubmissionFailsAfterThePauseClaimedTheAttempt() {
        // The pause claims an attempt that is still being submitted, and the submission then fails because the pause
        // has already shut the executors down. The completion callback that would queue the replacement is never
        // attached, so the submission failure itself has to hand the job back.
        ((AtomicBoolean) ReflectionTestUtils.getField(service, "isPaused")).set(true);
        localProcessingJobs().set(1);
        BuildJobQueueItem buildJob = buildJob(0);
        BuildJobQueueItem replacement = buildJob(1);
        when(buildJobManagementService.executeBuildJob(any())).thenAnswer(invocation -> {
            attemptStateFor(JOB_ID).requestInternalRequeue(replacement);
            throw new RejectedExecutionException("no build result executor");
        });

        ReflectionTestUtils.invokeMethod(service, "processBuild", buildJob);

        verify(buildJobQueue).add(replacement);
        assertThat(activeBuildAttempts()).doesNotContainKey(JOB_ID);
        assertThat(localProcessingJobs()).hasValue(0);
    }

    @Test
    void requeuesTheJobWhenAPauseClosesTheExecutorsBetweenTheAvailabilityCheckAndTheSubmission() {
        // The job is dequeued and registered as processing before it is submitted, so a pause landing in that window
        // must still hand it back. The rejection handler used to read the closed executor for its log line and died on
        // it before the requeue, which left the build in the processing map on an agent that was no longer running it.
        BuildJobQueueItem queuedJob = buildJob(0);
        ThreadPoolExecutor buildExecutor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(1));
        AtomicBoolean executorsClosed = new AtomicBoolean(false);
        when(buildAgentConfiguration.getBuildExecutor()).thenAnswer(invocation -> executorsClosed.get() ? null : buildExecutor);
        when(distributedDataAccessService.isConnectedToCluster()).thenReturn(true);
        when(distributedDataAccessService.noDataMemberInClusterAvailable()).thenReturn(false);
        when(buildJobQueue.isEmpty()).thenReturn(false);
        when(buildJobQueue.poll()).thenReturn(queuedJob, (BuildJobQueueItem) null);
        when(buildJobManagementService.executeBuildJob(any())).thenAnswer(invocation -> {
            executorsClosed.set(true);
            throw new RejectedExecutionException("the build executors of this build agent are closed");
        });

        try {
            ReflectionTestUtils.invokeMethod(service, "checkAvailabilityAndProcessNextBuild");

            verify(processingJobs).remove(JOB_ID);
            ArgumentCaptor<BuildJobQueueItem> requeued = ArgumentCaptor.forClass(BuildJobQueueItem.class);
            verify(buildJobQueue).add(requeued.capture());
            assertThat(requeued.getValue()).extracting(BuildJobQueueItem::id, BuildJobQueueItem::retryCount).containsExactly(JOB_ID, 1);
            assertThat(localProcessingJobs()).as("the claimed job has to be released again").hasValue(0);
        }
        finally {
            buildExecutor.shutdownNow();
        }
    }

    @Test
    void doesNotCloseTheServicesWhenTheAgentWasResumedWhilePausing() {
        when(buildJobManagementService.getRunningBuildJobIds()).thenReturn(Set.of());
        // A resume completed while the pause was between releasing the transition lock and closing the services.
        resumeWhileThePauseIsInProgress();

        pause();

        verify(buildAgentConfiguration, never()).closeBuildAgentServices();
    }
}
