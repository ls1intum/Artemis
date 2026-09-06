package de.tum.cit.aet.artemis.localci.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentDTO;
import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentInformation;
import de.tum.cit.aet.artemis.buildagent.dto.BuildConfig;
import de.tum.cit.aet.artemis.buildagent.dto.BuildJobQueueItem;
import de.tum.cit.aet.artemis.buildagent.dto.JobTimingInfo;
import de.tum.cit.aet.artemis.buildagent.dto.RepositoryInfo;
import de.tum.cit.aet.artemis.buildagent.dto.ResultQueueItem;
import de.tum.cit.aet.artemis.core.dto.SortingOrder;
import de.tum.cit.aet.artemis.core.dto.pageablesearch.FinishedBuildJobPageableSearchDTO;
import de.tum.cit.aet.artemis.core.dto.pageablesearch.SearchTermPageableSearchDTO;
import de.tum.cit.aet.artemis.core.service.ProfileService;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.DistributedMap;
import de.tum.cit.aet.artemis.core.service.distributed.api.queue.DistributedQueue;
import de.tum.cit.aet.artemis.localci.domain.BuildJob;
import de.tum.cit.aet.artemis.localci.test_repository.BuildJobTestRepository;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.domain.build.BuildStatus;

/**
 * Unit tests for the queue position estimate a waiting student sees, and for the paging of the finished build jobs an
 * instructor browses.
 * <p>
 * The estimate is the only feedback a student gets while their submission sits in the queue, and it is pure arithmetic
 * over the queue, the agents' capacity and how far the running jobs have progressed. Every branch of it produces a
 * plausible-looking timestamp, so a mistake here is invisible unless the number itself is asserted: an estimate that is
 * too low has students reloading a result that is not coming, and one that is too high looks like the build is stuck.
 */
@ExtendWith(MockitoExtension.class)
class SharedQueueManagementServiceEstimationTest {

    private static final long PARTICIPATION_ID = 10L;

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
    private DistributedMap<String, ZonedDateTime> dockerImageCleanupInfo;

    @Mock
    private DistributedQueue<ResultQueueItem> buildResultQueue;

    @Mock
    private DistributedMap<String, BuildAgentInformation> buildAgentInformation;

    private SharedQueueManagementService sharedQueueManagementService;

    @BeforeEach
    void setUp() {
        sharedQueueManagementService = new SharedQueueManagementService(buildJobRepository, profileService, distributedDataAccessService,
                Optional.of(localCIQueueWebsocketService));
        lenient().when(distributedDataAccessService.getDistributedBuildJobQueue()).thenReturn(buildJobQueue);
    }

    /**
     * Puts the service into the state the estimate actually has to reason about: a queue that is not empty and no spare
     * capacity, which is the only situation in which the estimate is more than "now".
     */
    private void withBusyCluster(int capacity, int runningJobs) {
        ReflectionTestUtils.setField(sharedQueueManagementService, "buildAgentsCapacity", capacity);
        ReflectionTestUtils.setField(sharedQueueManagementService, "runningBuildJobCount", runningJobs);
        lenient().when(buildJobQueue.isEmpty()).thenReturn(false);
        lenient().when(distributedDataAccessService.getQueuedJobsSize()).thenReturn(capacity);
    }

    private static BuildJobQueueItem queuedJob(String id, long participationId, int priority, ZonedDateTime submittedAt, long estimatedDuration) {
        return new BuildJobQueueItem(id, id, new BuildAgentDTO("agent", "127.0.0.1:5701", "agent"), participationId, 1L, 3L, 0, priority, BuildStatus.QUEUED,
                new RepositoryInfo("repo", RepositoryType.USER, RepositoryType.USER, "assignment", "tests", "solution", new String[0], new String[0]),
                new JobTimingInfo(submittedAt, null, null, null, estimatedDuration),
                new BuildConfig(null, null, "commit", "commit", "commit", "main", null, null, false, false, List.of(), 0, null, null, null, null), null, null);
    }

    private static BuildJobQueueItem runningJob(String id, ZonedDateTime estimatedCompletionDate) {
        return new BuildJobQueueItem(id, id, new BuildAgentDTO("agent", "127.0.0.1:5701", "agent"), 99L, 1L, 3L, 0, 1, BuildStatus.BUILDING,
                new RepositoryInfo("repo", RepositoryType.USER, RepositoryType.USER, "assignment", "tests", "solution", new String[0], new String[0]),
                new JobTimingInfo(ZonedDateTime.now().minusMinutes(1), ZonedDateTime.now().minusMinutes(1), null, estimatedCompletionDate, 0),
                new BuildConfig(null, null, "commit", "commit", "commit", "main", null, null, false, false, List.of(), 0, null, null, null, null), null, null);
    }

    private void withQueuedJobs(BuildJobQueueItem... jobs) {
        lenient().when(distributedDataAccessService.getQueuedJobs()).thenReturn(new ArrayList<>(List.of(jobs)));
    }

    private void withRunningJobs(BuildJobQueueItem... jobs) {
        lenient().when(distributedDataAccessService.getProcessingJobs()).thenReturn(new ArrayList<>(List.of(jobs)));
    }

    private static void assertIsAboutNow(ZonedDateTime actual) {
        assertThat(actual).isCloseTo(ZonedDateTime.now(), within(30, ChronoUnit.SECONDS));
    }

    @Test
    void estimatedStartDate_whenNothingIsQueued_isNow() {
        when(buildJobQueue.isEmpty()).thenReturn(true);

        assertIsAboutNow(sharedQueueManagementService.getBuildJobEstimatedStartDate(PARTICIPATION_ID));
    }

    @Test
    void estimatedStartDate_whenTheClusterStillHasSpareCapacity_isNow() {
        // An agent is free right now, so the job does not wait at all and the student should not be shown a delay.
        ReflectionTestUtils.setField(sharedQueueManagementService, "buildAgentsCapacity", 4);
        ReflectionTestUtils.setField(sharedQueueManagementService, "runningBuildJobCount", 1);
        when(buildJobQueue.isEmpty()).thenReturn(false);
        when(distributedDataAccessService.getQueuedJobsSize()).thenReturn(1);

        assertIsAboutNow(sharedQueueManagementService.getBuildJobEstimatedStartDate(PARTICIPATION_ID));
    }

    @Test
    void estimatedStartDate_whenTheParticipationHasNoQueuedJob_isNow() {
        // Nothing of this participation is waiting, so there is no queue position to report.
        withBusyCluster(1, 1);
        withQueuedJobs(queuedJob("someone-else", 999L, 1, ZonedDateTime.now(), 60));

        assertIsAboutNow(sharedQueueManagementService.getBuildJobEstimatedStartDate(PARTICIPATION_ID));
    }

    @Test
    void estimatedStartDate_whenOurJobIsNextAndAnAgentIsAboutToFinish_isWhenThatAgentFrees() {
        // One agent, busy for another 60 seconds, and our job is first in line: it starts when that agent is done.
        withBusyCluster(1, 1);
        withQueuedJobs(queuedJob("ours", PARTICIPATION_ID, 1, ZonedDateTime.now(), 30));
        withRunningJobs(runningJob("running", ZonedDateTime.now().plusSeconds(60)));

        ZonedDateTime estimate = sharedQueueManagementService.getBuildJobEstimatedStartDate(PARTICIPATION_ID);

        assertThat(estimate).isCloseTo(ZonedDateTime.now().plusSeconds(60), within(15, ChronoUnit.SECONDS));
    }

    @Test
    void estimatedStartDate_whenARunningJobIsAlreadyOverdue_treatsThatAgentAsFree() {
        // A job that has overrun its estimate must not push the estimate into the past, which would render as a negative wait.
        withBusyCluster(1, 1);
        withQueuedJobs(queuedJob("ours", PARTICIPATION_ID, 1, ZonedDateTime.now(), 30));
        withRunningJobs(runningJob("overdue", ZonedDateTime.now().minusMinutes(5)));

        assertIsAboutNow(sharedQueueManagementService.getBuildJobEstimatedStartDate(PARTICIPATION_ID));
    }

    @Test
    void estimatedStartDate_whenARunningJobHasNoEstimate_treatsThatAgentAsFree() {
        withBusyCluster(1, 1);
        withQueuedJobs(queuedJob("ours", PARTICIPATION_ID, 1, ZonedDateTime.now(), 30));
        withRunningJobs(runningJob("unknown-duration", null));

        assertIsAboutNow(sharedQueueManagementService.getBuildJobEstimatedStartDate(PARTICIPATION_ID));
    }

    @Test
    void estimatedStartDate_countsOnlyTheJobsAheadOfOursInPriorityOrder() {
        // The queue is not FIFO: a higher priority job submitted later still runs first, so it has to be counted as ahead of ours.
        ZonedDateTime now = ZonedDateTime.now();
        withBusyCluster(1, 1);
        withQueuedJobs(queuedJob("ours", PARTICIPATION_ID, 5, now.minusMinutes(5), 30), queuedJob("urgent", 77L, 1, now, 100));
        withRunningJobs(runningJob("running", now.plusSeconds(10)));

        ZonedDateTime estimate = sharedQueueManagementService.getBuildJobEstimatedStartDate(PARTICIPATION_ID);

        // The single agent finishes its current job in 10s, then runs the urgent job for 100s before ours can start.
        assertThat(estimate).isCloseTo(now.plusSeconds(110), within(15, ChronoUnit.SECONDS));
    }

    @Test
    void estimatedStartDate_spreadsTheWaitingJobsOverAllAgents() {
        // Two agents means two jobs run at once, so four jobs ahead of ours cost two rounds, not four.
        ZonedDateTime now = ZonedDateTime.now();
        withBusyCluster(2, 2);
        withQueuedJobs(queuedJob("a", 1L, 1, now.minusSeconds(40), 50), queuedJob("b", 2L, 1, now.minusSeconds(30), 50), queuedJob("ours", PARTICIPATION_ID, 1, now, 50));
        withRunningJobs(runningJob("r1", now), runningJob("r2", now));

        ZonedDateTime estimate = sharedQueueManagementService.getBuildJobEstimatedStartDate(PARTICIPATION_ID);

        // Both agents are free now, each takes one of the two jobs ahead of ours, so ours starts after one 50s round.
        assertThat(estimate).isCloseTo(now.plusSeconds(50), within(15, ChronoUnit.SECONDS));
    }

    @Test
    void estimatedStartDate_whenMoreJobsAreRunningThanTheClusterShouldHold_usesOnlyTheAvailableAgents() {
        // Stale entries can leave more processing jobs than agents; the estimate must still be based on the real capacity.
        ZonedDateTime now = ZonedDateTime.now();
        withBusyCluster(1, 1);
        withQueuedJobs(queuedJob("ours", PARTICIPATION_ID, 1, now, 30));
        withRunningJobs(runningJob("r1", now.plusSeconds(20)), runningJob("r2", now.plusSeconds(300)));
        lenient().when(distributedDataAccessService.getBuildAgentInformation()).thenReturn(List.of());

        ZonedDateTime estimate = sharedQueueManagementService.getBuildJobEstimatedStartDate(PARTICIPATION_ID);

        // Sorted remaining durations are [20, 300]; with a capacity of one only the first agent counts.
        assertThat(estimate).isCloseTo(now.plusSeconds(20), within(15, ChronoUnit.SECONDS));
    }

    @Test
    void estimatedStartDate_forTheLatestJobOfAParticipationThatQueuedTwice() {
        // Pushing twice queues two jobs; the student waits for the newest one, so the estimate must follow that one.
        ZonedDateTime now = ZonedDateTime.now();
        withBusyCluster(1, 1);
        withQueuedJobs(queuedJob("older", PARTICIPATION_ID, 1, now.minusSeconds(60), 40), queuedJob("newer", PARTICIPATION_ID, 1, now, 40));
        withRunningJobs(runningJob("running", now));

        ZonedDateTime estimate = sharedQueueManagementService.getBuildJobEstimatedStartDate(PARTICIPATION_ID);

        // The older job of the same participation is still ahead of the newer one and occupies the only agent for 40s.
        assertThat(estimate).isCloseTo(now.plusSeconds(40), within(15, ChronoUnit.SECONDS));
    }

    private static FinishedBuildJobPageableSearchDTO search(int page, int pageSize, SortingOrder order) {
        SearchTermPageableSearchDTO<String> pageable = new SearchTermPageableSearchDTO<>();
        pageable.setPage(page);
        pageable.setPageSize(pageSize);
        pageable.setSortedColumn("id");
        pageable.setSortingOrder(order);
        pageable.setSearchTerm("");
        return new FinishedBuildJobPageableSearchDTO(null, null, null, null, null, null, pageable);
    }

    private static BuildJob buildJobWithId(long id) {
        BuildJob buildJob = new BuildJob();
        buildJob.setId(id);
        return buildJob;
    }

    @Test
    void filteredFinishedBuildJobs_returnTheJobsInTheOrderTheQueryRanked() {
        // The second query fetches by "id IN (...)", which does not preserve order; without the re-ordering the page would
        // silently ignore the sorting the instructor asked for.
        when(buildJobRepository.findFinishedIdsByFilterCriteria(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new SliceImpl<>(List.of(3L, 1L, 2L), PageRequest.of(0, 20), false));
        when(buildJobRepository.findWithDataByIdIn(List.of(3L, 1L, 2L))).thenReturn(List.of(buildJobWithId(1L), buildJobWithId(2L), buildJobWithId(3L)));

        Slice<BuildJob> page = sharedQueueManagementService.getFilteredFinishedBuildJobs(search(1, 20, SortingOrder.DESCENDING), null);

        assertThat(page.getContent()).extracting(BuildJob::getId).containsExactly(3L, 1L, 2L);
    }

    @Test
    void filteredFinishedBuildJobs_translateTheOneBasedPageOfTheClientToAZeroBasedPageRequest() {
        // The client counts pages from one and Spring Data from zero; getting this wrong skips or repeats a whole page.
        when(buildJobRepository.findFinishedIdsByFilterCriteria(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new SliceImpl<>(List.of(), PageRequest.of(1, 20), false));

        sharedQueueManagementService.getFilteredFinishedBuildJobs(search(1, 20, SortingOrder.ASCENDING), null);
        sharedQueueManagementService.getFilteredFinishedBuildJobs(search(3, 20, SortingOrder.DESCENDING), null);

        ArgumentCaptor<PageRequest> pageRequest = ArgumentCaptor.captor();
        verify(buildJobRepository, times(2)).findFinishedIdsByFilterCriteria(any(), any(), any(), any(), any(), any(), any(), any(), pageRequest.capture());
        // The client's first page is Spring Data's page 0; an off-by-one here would skip or repeat a whole page of build jobs.
        assertThat(pageRequest.getAllValues()).extracting(PageRequest::getPageNumber).containsExactly(0, 2);
        assertThat(pageRequest.getValue().getPageSize()).isEqualTo(20);
        assertThat(pageRequest.getAllValues().getFirst().getSort().getOrderFor("id").isAscending()).isTrue();
        assertThat(pageRequest.getValue().getSort().getOrderFor("id").isDescending()).isTrue();
    }

    @Test
    void filteredFinishedBuildJobs_passTheBuildDurationBoundsOnAsDurations() {
        // The client sends seconds; the query compares durations, so the conversion has to happen here.
        when(buildJobRepository.findFinishedIdsByFilterCriteria(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new SliceImpl<>(List.of(), PageRequest.of(0, 20), false));
        SearchTermPageableSearchDTO<String> pageable = new SearchTermPageableSearchDTO<>();
        pageable.setPage(1);
        pageable.setPageSize(20);
        pageable.setSortedColumn("id");
        pageable.setSortingOrder(SortingOrder.ASCENDING);
        pageable.setSearchTerm("");

        sharedQueueManagementService.getFilteredFinishedBuildJobs(new FinishedBuildJobPageableSearchDTO(null, null, null, null, 30, 90, pageable), 5L);

        verify(buildJobRepository).findFinishedIdsByFilterCriteria(any(), any(), any(), any(), any(), eq(5L), eq(Duration.ofSeconds(30)), eq(Duration.ofSeconds(90)), any());
    }

    @Test
    void clearDistributedData_emptiesEveryStructureABuildLeavesBehind() {
        // An admin clears the queue to recover from a stuck cluster; leaving one structure behind keeps the cluster stuck.
        when(distributedDataAccessService.getDistributedProcessingJobs()).thenReturn(processingJobs);
        when(distributedDataAccessService.getDistributedDockerImageCleanupInfo()).thenReturn(dockerImageCleanupInfo);
        when(distributedDataAccessService.getDistributedBuildResultQueue()).thenReturn(buildResultQueue);
        when(distributedDataAccessService.getDistributedBuildAgentInformation()).thenReturn(buildAgentInformation);

        sharedQueueManagementService.clearDistributedData();

        verify(buildJobQueue).clear();
        verify(processingJobs).clear();
        verify(dockerImageCleanupInfo).clear();
        verify(buildResultQueue).clear();
        verify(buildAgentInformation).clear();
    }
}
