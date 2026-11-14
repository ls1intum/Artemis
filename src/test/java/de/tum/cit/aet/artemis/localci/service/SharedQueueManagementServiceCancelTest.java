package de.tum.cit.aet.artemis.localci.service;

import static org.mockito.ArgumentMatchers.any;
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
 * Unit tests for the cancellation of a build job on behalf of a course. The course is compared with the job that is cancelled, so a job of another course is left alone, also
 * while it is being handed over from the queue to an agent.
 */
@ExtendWith(MockitoExtension.class)
class SharedQueueManagementServiceCancelTest {

    private static final long COURSE_ID = 1L;

    private static final long OTHER_COURSE_ID = 2L;

    @Mock
    private BuildJobTestRepository buildJobRepository;

    @Mock
    private ProfileService profileService;

    @Mock
    private DistributedDataAccessService distributedDataAccessService;

    @Mock
    private DistributedQueue<BuildJobQueueItem> buildJobQueue;

    @Mock
    private DistributedMap<String, BuildJobQueueItem> processingJobs;

    @Mock
    private DistributedTopic<String> canceledBuildJobsTopic;

    private SharedQueueManagementService sharedQueueManagementService;

    @BeforeEach
    void setUp() {
        sharedQueueManagementService = new SharedQueueManagementService(buildJobRepository, profileService, distributedDataAccessService, Optional.empty());
        lenient().when(distributedDataAccessService.getDistributedBuildJobQueue()).thenReturn(buildJobQueue);
        lenient().when(distributedDataAccessService.getDistributedProcessingJobs()).thenReturn(processingJobs);
        lenient().when(distributedDataAccessService.getCanceledBuildJobsTopic()).thenReturn(canceledBuildJobsTopic);
    }

    private static BuildJobQueueItem job(String id, long courseId) {
        return new BuildJobQueueItem(id, id, new BuildAgentDTO("agent", "127.0.0.1:5701", "agent"), 10L, courseId, 3L, 0, 1, BuildStatus.QUEUED,
                new RepositoryInfo("repo", RepositoryType.USER, RepositoryType.USER, "assignment", "tests", "solution", new String[0], new String[0]),
                new JobTimingInfo(ZonedDateTime.now(), null, null, null, 0),
                new BuildConfig(null, null, "commit", "commit", "commit", "main", null, null, false, false, List.of(), 0, null, null, null, null), null, null);
    }

    @Test
    void cancelBuildJobOfCourse_removesAQueuedJobOfTheCourse() {
        var queued = job("job-1", COURSE_ID);
        when(distributedDataAccessService.getQueuedJobs()).thenReturn(new ArrayList<>(List.of(queued)));

        sharedQueueManagementService.cancelBuildJobOfCourse("job-1", COURSE_ID);

        verify(buildJobQueue).removeAll(List.of(queued));
    }

    @Test
    void cancelBuildJobOfCourse_leavesAQueuedJobOfAnotherCourseAlone() {
        when(distributedDataAccessService.getQueuedJobs()).thenReturn(new ArrayList<>(List.of(job("job-1", OTHER_COURSE_ID))));
        when(processingJobs.get("job-1")).thenReturn(null);

        sharedQueueManagementService.cancelBuildJobOfCourse("job-1", COURSE_ID);

        verify(buildJobQueue, never()).removeAll(any());
        verify(processingJobs, never()).remove(any(String.class));
        verify(canceledBuildJobsTopic, never()).publish(any());
    }

    @Test
    void cancelBuildJobOfCourse_leavesAProcessingJobOfAnotherCourseAlone() {
        when(distributedDataAccessService.getQueuedJobs()).thenReturn(new ArrayList<>());
        when(processingJobs.get("job-1")).thenReturn(job("job-1", OTHER_COURSE_ID));

        sharedQueueManagementService.cancelBuildJobOfCourse("job-1", COURSE_ID);

        verify(processingJobs, never()).remove(any(String.class));
        verify(canceledBuildJobsTopic, never()).publish(any());
    }

    @Test
    void cancelBuildJobOfCourse_doesNothingForAJobThatIsInNeitherPlaceWhileItIsHandedOver() {
        when(distributedDataAccessService.getQueuedJobs()).thenReturn(new ArrayList<>());
        when(processingJobs.get("job-1")).thenReturn(null);

        sharedQueueManagementService.cancelBuildJobOfCourse("job-1", COURSE_ID);

        verify(buildJobQueue, never()).removeAll(any());
        verify(processingJobs, never()).remove(any(String.class));
        verify(canceledBuildJobsTopic, never()).publish(any());
    }

    @Test
    void cancelBuildJobOfCourse_stopsAProcessingJobOfTheCourse() {
        var processing = job("job-1", COURSE_ID);
        when(distributedDataAccessService.getQueuedJobs()).thenReturn(new ArrayList<>());
        when(processingJobs.get("job-1")).thenReturn(processing);
        when(processingJobs.remove("job-1")).thenReturn(processing);

        sharedQueueManagementService.cancelBuildJobOfCourse("job-1", COURSE_ID);

        verify(canceledBuildJobsTopic).publish("job-1");
    }
}
