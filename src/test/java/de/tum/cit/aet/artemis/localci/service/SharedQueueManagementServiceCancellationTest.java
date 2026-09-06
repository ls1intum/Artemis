package de.tum.cit.aet.artemis.localci.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
import de.tum.cit.aet.artemis.buildagent.dto.BuildConfig;
import de.tum.cit.aet.artemis.buildagent.dto.BuildJobQueueItem;
import de.tum.cit.aet.artemis.buildagent.dto.JobTimingInfo;
import de.tum.cit.aet.artemis.buildagent.dto.RepositoryInfo;
import de.tum.cit.aet.artemis.core.service.ProfileService;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.DistributedMap;
import de.tum.cit.aet.artemis.core.service.distributed.api.queue.DistributedQueue;
import de.tum.cit.aet.artemis.core.service.distributed.api.topic.DistributedTopic;
import de.tum.cit.aet.artemis.localci.test_repository.BuildJobTestRepository;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.domain.build.BuildStatus;

/**
 * Unit tests for cancelling build jobs across the cluster.
 * <p>
 * A build job lives in one of two places: the shared queue while it waits, and the map of processing jobs once an agent
 * picked it up. Cancelling has to reach the right one - removing a queued job from the queue, and asking the agent to
 * stop a running one over the cancellation topic - and it has to record the cancellation in the database, because a job
 * that disappears from the queue without being marked stays "queued" in the build history forever. The selective
 * variants additionally must not touch the jobs of another course or participation.
 */
@ExtendWith(MockitoExtension.class)
class SharedQueueManagementServiceCancellationTest {

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

    @Mock
    private DistributedTopic<String> canceledBuildJobsTopic;

    private SharedQueueManagementService sharedQueueManagementService;

    @BeforeEach
    void setUp() {
        sharedQueueManagementService = new SharedQueueManagementService(buildJobRepository, profileService, distributedDataAccessService,
                Optional.of(localCIQueueWebsocketService));
        lenient().when(distributedDataAccessService.getDistributedBuildJobQueue()).thenReturn(buildJobQueue);
        lenient().when(distributedDataAccessService.getDistributedProcessingJobs()).thenReturn(processingJobs);
        lenient().when(distributedDataAccessService.getCanceledBuildJobsTopic()).thenReturn(canceledBuildJobsTopic);
        // The websocket notification refetches the job; nothing in these tests depends on what it finds.
        lenient().when(buildJobRepository.findByBuildJobId(anyString())).thenReturn(Optional.empty());
    }

    private static BuildJobQueueItem job(String id, long courseId, long participationId) {
        return new BuildJobQueueItem(id, id, new BuildAgentDTO("agent", "127.0.0.1:5701", "agent"), participationId, courseId, 3L, 0, 1, BuildStatus.QUEUED,
                new RepositoryInfo("repo", RepositoryType.USER, RepositoryType.USER, "assignment", "tests", "solution", new String[0], new String[0]),
                new JobTimingInfo(ZonedDateTime.now(), null, null, null, 0),
                new BuildConfig(null, null, "commit", "commit", "commit", "main", null, null, false, false, List.of(), 0, null, null, null, null), null, null);
    }

    private void withQueuedJobs(BuildJobQueueItem... jobs) {
        when(distributedDataAccessService.getQueuedJobs()).thenReturn(new ArrayList<>(List.of(jobs)));
    }

    private void withRunningJobs(BuildJobQueueItem... jobs) {
        when(distributedDataAccessService.getProcessingJobs()).thenReturn(new ArrayList<>(List.of(jobs)));
    }

    @Test
    void cancelBuildJob_forAQueuedJob_removesItFromTheQueueAndRecordsTheCancellation() {
        BuildJobQueueItem queued = job("job-1", 1L, 10L);
        withQueuedJobs(queued, job("job-2", 1L, 11L));

        sharedQueueManagementService.cancelBuildJob("job-1");

        ArgumentCaptor<List<BuildJobQueueItem>> removed = ArgumentCaptor.captor();
        verify(buildJobQueue).removeAll(removed.capture());
        assertThat(removed.getValue()).as("only the job that was cancelled is removed").containsExactly(queued);
        // A job that vanishes from the queue without being marked stays "queued" in the build history forever.
        verify(buildJobRepository).updateBuildJobStatus("job-1", BuildStatus.CANCELLED);
        verify(canceledBuildJobsTopic, never()).publish(anyString());
    }

    @Test
    void cancelBuildJob_forAJobAnAgentIsAlreadyRunning_asksTheAgentToStop() {
        // A running job is not in the queue any more, so removing it from the queue would do nothing: the agent has to be told over the topic.
        withQueuedJobs();
        when(processingJobs.remove("job-1")).thenReturn(job("job-1", 1L, 10L));

        sharedQueueManagementService.cancelBuildJob("job-1");

        verify(processingJobs).remove("job-1");
        verify(canceledBuildJobsTopic).publish("job-1");
    }

    @Test
    void cancelBuildJob_forAJobThatIsNeitherQueuedNorRunning_doesNothing() {
        withQueuedJobs();
        when(processingJobs.remove("job-1")).thenReturn(null);

        sharedQueueManagementService.cancelBuildJob("job-1");

        verify(canceledBuildJobsTopic, never()).publish(anyString());
        verify(buildJobRepository, never()).updateBuildJobStatus(anyString(), any());
    }

    @Test
    void cancelAllQueuedBuildJobs_clearsTheQueueAndMarksEveryJobItHeld() {
        withQueuedJobs(job("job-1", 1L, 10L), job("job-2", 2L, 11L));

        sharedQueueManagementService.cancelAllQueuedBuildJobs();

        verify(buildJobQueue).clear();
        verify(buildJobRepository).updateBuildJobStatus("job-1", BuildStatus.CANCELLED);
        verify(buildJobRepository).updateBuildJobStatus("job-2", BuildStatus.CANCELLED);
    }

    @Test
    void cancelAllQueuedBuildJobsForCourse_leavesTheJobsOfOtherCoursesQueued() {
        BuildJobQueueItem ofTheCourse = job("job-1", 1L, 10L);
        withQueuedJobs(ofTheCourse, job("job-2", 2L, 11L));

        sharedQueueManagementService.cancelAllQueuedBuildJobsForCourse(1L);

        ArgumentCaptor<List<BuildJobQueueItem>> removed = ArgumentCaptor.captor();
        verify(buildJobQueue).removeAll(removed.capture());
        assertThat(removed.getValue()).as("cancelling one course must not stop another course's builds").containsExactly(ofTheCourse);
        verify(buildJobRepository).updateBuildJobStatus("job-1", BuildStatus.CANCELLED);
        verify(buildJobRepository, never()).updateBuildJobStatus("job-2", BuildStatus.CANCELLED);
    }

    @Test
    void cancelAllRunningBuildJobsForCourse_stopsOnlyTheAgentsWorkingOnThatCourse() {
        withRunningJobs(job("job-1", 1L, 10L), job("job-2", 2L, 11L));
        withQueuedJobs();
        when(processingJobs.remove("job-1")).thenReturn(job("job-1", 1L, 10L));

        sharedQueueManagementService.cancelAllRunningBuildJobsForCourse(1L);

        verify(canceledBuildJobsTopic).publish("job-1");
        verify(canceledBuildJobsTopic, never()).publish("job-2");
    }

    @Test
    void cancelAllJobsForParticipation_reachesBothTheQueuedAndTheRunningJobsOfThatParticipation() {
        // A student who resets their repository can have one build waiting and one already running; leaving either behind produces a result for work they discarded.
        BuildJobQueueItem queued = job("job-queued", 1L, 10L);
        when(distributedDataAccessService.getQueuedJobs()).thenReturn(new ArrayList<>(List.of(queued, job("job-other", 1L, 99L))),
                new ArrayList<>(List.of(job("job-other", 1L, 99L))));
        withRunningJobs(job("job-running", 1L, 10L), job("job-other-running", 1L, 99L));
        when(processingJobs.remove("job-running")).thenReturn(job("job-running", 1L, 10L));

        sharedQueueManagementService.cancelAllJobsForParticipation(10L);

        ArgumentCaptor<List<BuildJobQueueItem>> removed = ArgumentCaptor.captor();
        verify(buildJobQueue).removeAll(removed.capture());
        assertThat(removed.getValue()).as("the waiting build of that participation is removed, and only that one").containsExactly(queued);
        verify(buildJobRepository).updateBuildJobStatus("job-queued", BuildStatus.CANCELLED);
        verify(canceledBuildJobsTopic).publish("job-running");
        verify(canceledBuildJobsTopic, never()).publish("job-other-running");
    }
}
