package de.tum.cit.aet.artemis.localci.service;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentDTO;
import de.tum.cit.aet.artemis.buildagent.dto.BuildConfig;
import de.tum.cit.aet.artemis.buildagent.dto.BuildJobQueueItem;
import de.tum.cit.aet.artemis.buildagent.dto.JobTimingInfo;
import de.tum.cit.aet.artemis.buildagent.dto.RepositoryInfo;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.domain.build.BuildStatus;

/**
 * Covers the coalescing of the build queue broadcasts: the payload is the whole collection, so a change has to mark the
 * collection rather than send it, and the scheduled broadcast has to send exactly one snapshot per interval.
 */
@ExtendWith(MockitoExtension.class)
class LocalCIQueueWebsocketServiceTest {

    private static final long COURSE_ID = 1L;

    private static final ZonedDateTime SUBMISSION_DATE = ZonedDateTime.parse("2024-01-01T00:00:00Z");

    @Mock
    private LocalCIWebsocketMessagingService localCIWebsocketMessagingService;

    @Mock
    private DistributedDataAccessService distributedDataAccessService;

    @InjectMocks
    private LocalCIQueueWebsocketService localCIQueueWebsocketService;

    @Test
    void shouldNotSendAnythingUntilTheBroadcastRuns() {
        localCIQueueWebsocketService.queuedJobsChanged(COURSE_ID);
        localCIQueueWebsocketService.processingJobsChanged(COURSE_ID);
        localCIQueueWebsocketService.buildAgentSummaryChanged();

        verifyNoMoreInteractions(localCIWebsocketMessagingService, distributedDataAccessService);
    }

    @Test
    void shouldCollapseABurstOfQueueChangesIntoOneBroadcast() {
        when(distributedDataAccessService.getQueuedJobs()).thenReturn(new ArrayList<>(List.of(queuedJob("1"), queuedJob("2"))));

        for (int change = 0; change < 50; change++) {
            localCIQueueWebsocketService.queuedJobsChanged(COURSE_ID);
        }
        localCIQueueWebsocketService.broadcastPendingChanges();

        verify(distributedDataAccessService, times(1)).getQueuedJobs();
        verify(localCIWebsocketMessagingService, times(1)).sendQueuedBuildJobs(anyList());
        verify(localCIWebsocketMessagingService, times(1)).sendQueuedBuildJobsForCourse(eq(COURSE_ID), anyList());
    }

    @Test
    void shouldSendNothingWhenNothingChanged() {
        localCIQueueWebsocketService.broadcastPendingChanges();

        verifyNoMoreInteractions(localCIWebsocketMessagingService, distributedDataAccessService);
    }

    @Test
    void shouldSendAgainAfterTheNextChange() {
        when(distributedDataAccessService.getProcessingJobs()).thenReturn(new ArrayList<>(List.of(queuedJob("1"))));

        localCIQueueWebsocketService.processingJobsChanged(COURSE_ID);
        localCIQueueWebsocketService.broadcastPendingChanges();
        localCIQueueWebsocketService.broadcastPendingChanges();
        localCIQueueWebsocketService.processingJobsChanged(COURSE_ID);
        localCIQueueWebsocketService.broadcastPendingChanges();

        verify(localCIWebsocketMessagingService, times(2)).sendRunningBuildJobs(anyList());
    }

    @Test
    void shouldSendOnlyTheJobsOfTheCourseToTheCourseTopic() {
        when(distributedDataAccessService.getQueuedJobs()).thenReturn(new ArrayList<>(List.of(queuedJob("mine"), queuedJobOfOtherCourse("theirs"))));

        localCIQueueWebsocketService.queuedJobsChanged(COURSE_ID);
        localCIQueueWebsocketService.broadcastPendingChanges();

        ArgumentCaptor<List<BuildJobQueueItem>> forCourse = ArgumentCaptor.captor();
        verify(localCIWebsocketMessagingService).sendQueuedBuildJobsForCourse(eq(COURSE_ID), forCourse.capture());
        Assertions.assertThat(forCourse.getValue()).extracting(BuildJobQueueItem::id).containsExactly("mine");

        ArgumentCaptor<List<BuildJobQueueItem>> forAdmin = ArgumentCaptor.captor();
        verify(localCIWebsocketMessagingService).sendQueuedBuildJobs(forAdmin.capture());
        Assertions.assertThat(forAdmin.getValue()).extracting(BuildJobQueueItem::id).containsExactly("mine", "theirs");
    }

    @Test
    void shouldRetryOnTheNextRunAfterAFailedBroadcast() {
        when(distributedDataAccessService.getQueuedJobs()).thenThrow(new IllegalStateException("valkey is away")).thenReturn(new ArrayList<>(List.of(queuedJob("1"))));

        localCIQueueWebsocketService.queuedJobsChanged(COURSE_ID);
        localCIQueueWebsocketService.broadcastPendingChanges();
        verify(localCIWebsocketMessagingService, never()).sendQueuedBuildJobs(anyList());

        // the change has to survive the failed run, otherwise the queue views stay stale until an unrelated queue event
        localCIQueueWebsocketService.broadcastPendingChanges();
        verify(localCIWebsocketMessagingService, times(1)).sendQueuedBuildJobs(anyList());
        verify(localCIWebsocketMessagingService, times(1)).sendQueuedBuildJobsForCourse(eq(COURSE_ID), anyList());
    }

    @Test
    void shouldSendTheAdminSnapshotOnceWhenSeveralCoursesChanged() {
        when(distributedDataAccessService.getQueuedJobs()).thenReturn(new ArrayList<>(List.of(queuedJob("mine"), queuedJobOfOtherCourse("theirs"))));

        localCIQueueWebsocketService.queuedJobsChanged(COURSE_ID);
        localCIQueueWebsocketService.queuedJobsChanged(COURSE_ID + 1);
        localCIQueueWebsocketService.broadcastPendingChanges();

        // the admin topic carries the whole queue, so it is the same message for every course that changed
        verify(distributedDataAccessService, times(1)).getQueuedJobs();
        verify(localCIWebsocketMessagingService, times(1)).sendQueuedBuildJobs(anyList());
        verify(localCIWebsocketMessagingService, times(1)).sendQueuedBuildJobsForCourse(eq(COURSE_ID), anyList());
        verify(localCIWebsocketMessagingService, times(1)).sendQueuedBuildJobsForCourse(eq(COURSE_ID + 1), anyList());
    }

    private static BuildJobQueueItem queuedJob(String id) {
        return job(id, COURSE_ID);
    }

    private static BuildJobQueueItem queuedJobOfOtherCourse(String id) {
        return job(id, COURSE_ID + 1);
    }

    private static BuildJobQueueItem job(String id, long courseId) {
        return new BuildJobQueueItem(id, id, new BuildAgentDTO("agent", "127.0.0.1:5701", "agent"), 1L, courseId, 3L, 0, 1, BuildStatus.QUEUED,
                new RepositoryInfo("repo", RepositoryType.USER, RepositoryType.USER, "assignment", "tests", "solution", new String[0], new String[0]),
                new JobTimingInfo(SUBMISSION_DATE, null, null, null, 0),
                new BuildConfig(null, null, "commit", "commit", "commit", "main", null, null, false, false, List.of(), 0, null, null, null, null), null, null);
    }
}
