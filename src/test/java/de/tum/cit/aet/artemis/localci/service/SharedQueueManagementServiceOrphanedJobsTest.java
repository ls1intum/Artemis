package de.tum.cit.aet.artemis.localci.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentDTO;
import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentInformation;
import de.tum.cit.aet.artemis.buildagent.dto.BuildConfig;
import de.tum.cit.aet.artemis.buildagent.dto.BuildJobQueueItem;
import de.tum.cit.aet.artemis.buildagent.dto.JobTimingInfo;
import de.tum.cit.aet.artemis.buildagent.dto.RepositoryInfo;
import de.tum.cit.aet.artemis.core.service.ProfileService;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.DistributedMap;
import de.tum.cit.aet.artemis.core.service.distributed.api.queue.DistributedQueue;
import de.tum.cit.aet.artemis.localci.test_repository.BuildJobTestRepository;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.domain.build.BuildStatus;

/**
 * Unit tests for taking over the build jobs of a build agent that disappeared.
 * <p>
 * When an agent dies mid-build its jobs stay in the processing map with nobody working on them. Nothing else notices:
 * the student waits for a result that will never arrive, and the job is not in the queue any more, so no other agent
 * picks it up. This sweep is the only thing that recovers those builds, which puts weight on the cases it must not get
 * wrong - re-queuing a job that another node already took over would build the same submission twice, and re-queuing a
 * job whose agent is merely slow to register would take the build away from an agent that is still running it.
 */
@ExtendWith(MockitoExtension.class)
class SharedQueueManagementServiceOrphanedJobsTest {

    private static final String VANISHED_AGENT = "agent-that-died";

    private static final String LIVE_AGENT = "agent-still-here";

    @Mock
    private BuildJobTestRepository buildJobRepository;

    @Mock
    private ProfileService profileService;

    @Mock
    private DistributedDataAccessService distributedDataAccessService;

    @Mock
    private LocalCIQueueWebsocketService localCIQueueWebsocketService;

    @Mock
    private DistributedQueue<BuildJobQueueItem> buildJobQueue;

    @Mock
    private DistributedMap<String, BuildJobQueueItem> processingJobs;

    private SharedQueueManagementService sharedQueueManagementService;

    @BeforeEach
    void setUp() {
        sharedQueueManagementService = new SharedQueueManagementService(buildJobRepository, profileService, distributedDataAccessService,
                Optional.of(localCIQueueWebsocketService));
        lenient().when(distributedDataAccessService.isConnectedToCluster()).thenReturn(true);
        lenient().when(distributedDataAccessService.getDistributedProcessingJobs()).thenReturn(processingJobs);
        lenient().when(distributedDataAccessService.getDistributedBuildJobQueue()).thenReturn(buildJobQueue);
    }

    /**
     * @param startedAt when the agent began the build; jobs started within the grace period are left alone because their
     *                      agent may simply not have registered itself yet
     */
    private static BuildJobQueueItem jobOn(String agentName, String id, int retryCount, ZonedDateTime startedAt) {
        return new BuildJobQueueItem(id, id, new BuildAgentDTO(agentName, "127.0.0.1:5701", agentName), 10L, 1L, 3L, retryCount, 1, BuildStatus.BUILDING,
                new RepositoryInfo("repo", RepositoryType.USER, RepositoryType.USER, "assignment", "tests", "solution", new String[0], new String[0]),
                new JobTimingInfo(startedAt.minusMinutes(1), startedAt, null, null, 60),
                new BuildConfig(null, null, "commit", "commit", "commit", "main", null, null, false, false, List.of(), 0, null, null, null, null), null, null);
    }

    private static BuildJobQueueItem longRunningJobOn(String agentName, String id) {
        return jobOn(agentName, id, 0, ZonedDateTime.now().minusMinutes(10));
    }

    private static BuildAgentInformation agentInformation(String name) {
        return new BuildAgentInformation(new BuildAgentDTO(name, "127.0.0.1:5701", name), 1, 0, List.of(), null, "ssh-key", null, 0);
    }

    private void withProcessingJobs(BuildJobQueueItem... jobs) {
        lenient().when(distributedDataAccessService.getProcessingJobs()).thenReturn(new ArrayList<>(List.of(jobs)));
    }

    private void withRegisteredAgents(BuildAgentInformation... agents) {
        lenient().when(distributedDataAccessService.getBuildAgentInformation()).thenReturn(new ArrayList<>(List.of(agents)));
    }

    @Test
    void requeue_whenThisNodeIsNotConnectedToTheCluster_doesNothing() {
        // Without cluster membership the view of the agents is unreliable, and acting on it would re-queue live jobs.
        when(distributedDataAccessService.isConnectedToCluster()).thenReturn(false);

        sharedQueueManagementService.requeueJobsOfVanishedAgents();

        verifyNoInteractions(buildJobQueue, processingJobs, buildJobRepository);
    }

    @Test
    void requeue_whenEveryProcessingJobBelongsToARegisteredAgent_doesNothing() {
        withRegisteredAgents(agentInformation(LIVE_AGENT));
        withProcessingJobs(longRunningJobOn(LIVE_AGENT, "job-1"));

        sharedQueueManagementService.requeueJobsOfVanishedAgents();

        verify(buildJobQueue, never()).add(any());
        verify(processingJobs, never()).remove(anyString());
    }

    @Test
    void requeue_forAJobOfAnAgentThatIsGone_putsItBackOnTheQueueForAnyAgent() {
        withRegisteredAgents(agentInformation(LIVE_AGENT));
        withProcessingJobs(longRunningJobOn(VANISHED_AGENT, "orphan"));
        when(processingJobs.remove("orphan")).thenReturn(longRunningJobOn(VANISHED_AGENT, "orphan"));

        sharedQueueManagementService.requeueJobsOfVanishedAgents();

        ArgumentCaptor<BuildJobQueueItem> requeued = ArgumentCaptor.captor();
        verify(buildJobQueue).add(requeued.capture());
        assertThat(requeued.getValue().id()).as("the retry keeps the job id, so the clone token stays valid").isEqualTo("orphan");
        assertThat(requeued.getValue().retryCount()).as("the retry count is raised so the job cannot be re-queued forever").isEqualTo(1);
        // The dead agent must not be named on the retry, otherwise no other agent would consider the job to be theirs.
        assertThat(requeued.getValue().buildAgent().name()).isEmpty();
        // Taking the job out of the processing map is what stops a second node from re-queuing it as well.
        verify(processingJobs).remove("orphan");
    }

    @Test
    void requeue_forAJobThatAnotherNodeAlreadyTookOver_doesNotQueueItASecondTime() {
        // Every core node sees the same vanished agent, so the map removal is the only thing that decides who re-queues.
        withRegisteredAgents(agentInformation(LIVE_AGENT));
        withProcessingJobs(longRunningJobOn(VANISHED_AGENT, "orphan"));
        when(processingJobs.remove("orphan")).thenReturn(null);

        sharedQueueManagementService.requeueJobsOfVanishedAgents();

        verify(buildJobQueue, never()).add(any());
        verify(buildJobRepository, never()).updateBuildJobStatus(anyString(), any());
    }

    @Test
    void requeue_forAJobThatHasAlreadyBeenRetriedTooOften_marksItFailedInsteadOfRetryingForever() {
        // A job that fails on every agent would otherwise circle the cluster forever and never produce a result for the student.
        withRegisteredAgents(agentInformation(LIVE_AGENT));
        withProcessingJobs(jobOn(VANISHED_AGENT, "hopeless", 5, ZonedDateTime.now().minusMinutes(10)));
        when(processingJobs.remove("hopeless")).thenReturn(jobOn(VANISHED_AGENT, "hopeless", 5, ZonedDateTime.now().minusMinutes(10)));

        sharedQueueManagementService.requeueJobsOfVanishedAgents();

        verify(buildJobRepository).updateBuildJobStatus("hopeless", BuildStatus.FAILED);
        verify(buildJobQueue, never()).add(any());
    }

    @Test
    void requeue_leavesAJustStartedJobAlone() {
        // An agent that has only just come up may not be in the map yet; taking its fresh job away would kill a running build.
        withRegisteredAgents(agentInformation(LIVE_AGENT));
        withProcessingJobs(jobOn(VANISHED_AGENT, "just-started", 0, ZonedDateTime.now()));

        sharedQueueManagementService.requeueJobsOfVanishedAgents();

        verify(buildJobQueue, never()).add(any());
        verify(processingJobs, never()).remove(anyString());
    }

    @Test
    void requeue_forAJobThatNeverRecordedAStartDate_stillTakesItOver() {
        // A job with no start date has been sitting in the processing map since before the agent reported progress, so it is stuck.
        withRegisteredAgents(agentInformation(LIVE_AGENT));
        BuildJobQueueItem noStartDate = new BuildJobQueueItem("no-start", "no-start", new BuildAgentDTO(VANISHED_AGENT, "127.0.0.1:5701", VANISHED_AGENT), 10L, 1L, 3L, 0, 1,
                BuildStatus.BUILDING, new RepositoryInfo("repo", RepositoryType.USER, RepositoryType.USER, "assignment", "tests", "solution", new String[0], new String[0]),
                new JobTimingInfo(ZonedDateTime.now().minusMinutes(5), null, null, null, 60),
                new BuildConfig(null, null, "commit", "commit", "commit", "main", null, null, false, false, List.of(), 0, null, null, null, null), null, null);
        withProcessingJobs(noStartDate);
        when(processingJobs.remove("no-start")).thenReturn(noStartDate);

        sharedQueueManagementService.requeueJobsOfVanishedAgents();

        verify(buildJobQueue).add(any());
    }

    @Test
    void requeue_whenOneJobCannotBeHandled_stillHandlesTheOthers() {
        // One unusable entry must not stop the sweep, or a single bad job would strand every other orphaned build.
        withRegisteredAgents(agentInformation(LIVE_AGENT));
        withProcessingJobs(longRunningJobOn(VANISHED_AGENT, "explodes"), longRunningJobOn(VANISHED_AGENT, "recoverable"));
        when(processingJobs.remove("explodes")).thenThrow(new IllegalStateException("the distributed map is unavailable"));
        when(processingJobs.remove("recoverable")).thenReturn(longRunningJobOn(VANISHED_AGENT, "recoverable"));

        sharedQueueManagementService.requeueJobsOfVanishedAgents();

        ArgumentCaptor<BuildJobQueueItem> requeued = ArgumentCaptor.captor();
        verify(buildJobQueue).add(requeued.capture());
        assertThat(requeued.getValue().id()).isEqualTo("recoverable");
    }

    @Test
    void isSubmissionProcessing_reportsTheTimingOfTheBuildThatIsRunningForThatCommit() {
        ZonedDateTime startedAt = ZonedDateTime.now().minusSeconds(30);
        BuildJobQueueItem running = new BuildJobQueueItem("job", "job", new BuildAgentDTO(LIVE_AGENT, "127.0.0.1:5701", LIVE_AGENT), 10L, 1L, 3L, 0, 1, BuildStatus.BUILDING,
                new RepositoryInfo("repo", RepositoryType.USER, RepositoryType.USER, "assignment", "tests", "solution", new String[0], new String[0]),
                new JobTimingInfo(startedAt.minusSeconds(5), startedAt, null, startedAt.plusSeconds(60), 60),
                new BuildConfig(null, null, "abc123", "abc123", "commit", "main", null, null, false, false, List.of(), 0, null, null, null, null), null, null);
        withProcessingJobs(running);

        var timing = sharedQueueManagementService.isSubmissionProcessing(10L, "abc123");

        assertThat(timing).isNotNull();
        assertThat(timing.buildStartDate()).isEqualTo(startedAt);
        assertThat(timing.estimatedCompletionDate()).isEqualTo(startedAt.plusSeconds(60));
    }

    @Test
    void isSubmissionProcessing_forADifferentCommitOfTheSameParticipation_reportsNothing() {
        // A student who pushed again must not be shown the progress of the build of their previous commit.
        withProcessingJobs(longRunningJobOn(LIVE_AGENT, "job"));

        assertThat(sharedQueueManagementService.isSubmissionProcessing(10L, "a-different-commit")).isNull();
    }

    @Test
    void isSubmissionProcessing_whenNothingOfThatParticipationIsRunning_reportsNothing() {
        withProcessingJobs();

        assertThat(sharedQueueManagementService.isSubmissionProcessing(10L, "commit")).isNull();
    }
}
