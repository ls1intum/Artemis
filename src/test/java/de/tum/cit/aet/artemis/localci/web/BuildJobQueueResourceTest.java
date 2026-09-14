package de.tum.cit.aet.artemis.localci.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
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
import org.springframework.http.HttpStatus;

import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentDTO;
import de.tum.cit.aet.artemis.buildagent.dto.BuildConfig;
import de.tum.cit.aet.artemis.buildagent.dto.BuildJobQueueItem;
import de.tum.cit.aet.artemis.buildagent.dto.JobTimingInfo;
import de.tum.cit.aet.artemis.buildagent.dto.RepositoryInfo;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.DistributedMap;
import de.tum.cit.aet.artemis.core.test_repository.CourseTestRepository;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.localci.domain.BuildJob;
import de.tum.cit.aet.artemis.localci.service.DistributedDataAccessService;
import de.tum.cit.aet.artemis.localci.service.SharedQueueManagementService;
import de.tum.cit.aet.artemis.localci.test_repository.BuildJobTestRepository;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.domain.build.BuildStatus;

/**
 * Unit tests for the build queue endpoints an instructor uses to watch and stop the builds of their course.
 * <p>
 * Every one of these endpoints takes a course id from the path and acts on build jobs, so the course check is what keeps
 * one instructor from cancelling another course's builds. The lookup of a single job additionally has to search three
 * places - the jobs an agent is running, the jobs still queued, and the finished ones in the database - and has to refuse
 * a job that exists but belongs to a different course, because the id alone says nothing about who may see it.
 */
@ExtendWith(MockitoExtension.class)
class BuildJobQueueResourceTest {

    private static final long COURSE_ID = 1L;

    @Mock
    private SharedQueueManagementService localCIBuildJobQueueService;

    @Mock
    private AuthorizationCheckService authorizationCheckService;

    @Mock
    private CourseTestRepository courseRepository;

    @Mock
    private BuildJobTestRepository buildJobRepository;

    @Mock
    private DistributedDataAccessService distributedDataAccessService;

    @Mock
    private DistributedMap<String, BuildJobQueueItem> processingJobs;

    private BuildJobQueueResource buildJobQueueResource;

    private Course course;

    @BeforeEach
    void setUp() {
        buildJobQueueResource = new BuildJobQueueResource(localCIBuildJobQueueService, authorizationCheckService, courseRepository, buildJobRepository,
                distributedDataAccessService);
        course = new Course();
        course.setId(COURSE_ID);
        lenient().when(courseRepository.findByIdElseThrow(COURSE_ID)).thenReturn(course);
    }

    private void asInstructorOfTheCourse() {
        lenient().when(authorizationCheckService.isAtLeastInstructorInCourse(course, null)).thenReturn(true);
    }

    private void asSomebodyElse() {
        when(authorizationCheckService.isAtLeastInstructorInCourse(course, null)).thenReturn(false);
    }

    private static BuildJobQueueItem job(String id, long courseId) {
        return new BuildJobQueueItem(id, id, new BuildAgentDTO("agent", "127.0.0.1:5701", "agent"), 10L, courseId, 3L, 0, 1, BuildStatus.QUEUED,
                new RepositoryInfo("repo", RepositoryType.USER, RepositoryType.USER, "assignment", "tests", "solution", new String[0], new String[0]),
                new JobTimingInfo(ZonedDateTime.now(), null, null, null, 0),
                new BuildConfig(null, null, "commit", "commit", "commit", "main", null, null, false, false, List.of(), 0, null, null, null, null), null, null);
    }

    /**
     * A build job as it is kept in the history, with the fields the DTO conversion reads.
     */
    private static BuildJob finishedBuildJob(String id, long courseId) {
        var finished = new BuildJob();
        finished.setBuildJobId(id);
        finished.setCourseId(courseId);
        finished.setParticipationId(10L);
        finished.setExerciseId(3L);
        finished.setName(id);
        finished.setBuildStatus(BuildStatus.SUCCESSFUL);
        finished.setBuildSubmissionDate(ZonedDateTime.now().minusMinutes(2));
        finished.setBuildStartDate(ZonedDateTime.now().minusMinutes(2));
        finished.setBuildCompletionDate(ZonedDateTime.now().minusMinutes(1));
        return finished;
    }

    private void withProcessingJobs() {
        lenient().when(distributedDataAccessService.getDistributedProcessingJobs()).thenReturn(processingJobs);
    }

    @Test
    void getBuildJobById_withoutAnId_isABadRequest() {
        assertThat(buildJobQueueResource.getBuildJobById(COURSE_ID, "  ").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void getBuildJobById_findsAJobAnAgentIsCurrentlyRunning() {
        withProcessingJobs();
        var running = job("job-1", COURSE_ID);
        when(processingJobs.get("job-1")).thenReturn(running);

        var response = buildJobQueueResource.getBuildJobById(COURSE_ID, "job-1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(running);
    }

    @Test
    void getBuildJobById_ignoresTheSurroundingWhitespaceOfTheId() {
        withProcessingJobs();
        var running = job("job-1", COURSE_ID);
        when(processingJobs.get("job-1")).thenReturn(running);

        assertThat(buildJobQueueResource.getBuildJobById(COURSE_ID, " job-1 ").getBody()).isSameAs(running);
    }

    @Test
    void getBuildJobById_forARunningJobOfAnotherCourse_isNotFound() {
        // The id says nothing about who may see the job, so a job of another course must not be returned by its id alone.
        withProcessingJobs();
        when(processingJobs.get("job-1")).thenReturn(job("job-1", 999L));
        when(distributedDataAccessService.getQueuedJobs()).thenReturn(new ArrayList<>());
        when(buildJobRepository.findWithDataByBuildJobId("job-1")).thenReturn(Optional.empty());

        assertThat(buildJobQueueResource.getBuildJobById(COURSE_ID, "job-1").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void getBuildJobById_findsAJobThatIsStillWaitingInTheQueue() {
        withProcessingJobs();
        var queued = job("job-2", COURSE_ID);
        when(processingJobs.get("job-2")).thenReturn(null);
        when(distributedDataAccessService.getQueuedJobs()).thenReturn(new ArrayList<>(List.of(job("job-1", COURSE_ID), queued)));

        assertThat(buildJobQueueResource.getBuildJobById(COURSE_ID, "job-2").getBody()).isSameAs(queued);
    }

    @Test
    void getBuildJobById_forAQueuedJobOfAnotherCourse_isNotFound() {
        withProcessingJobs();
        when(processingJobs.get("job-1")).thenReturn(null);
        when(distributedDataAccessService.getQueuedJobs()).thenReturn(new ArrayList<>(List.of(job("job-1", 999L))));
        when(buildJobRepository.findWithDataByBuildJobId("job-1")).thenReturn(Optional.empty());

        assertThat(buildJobQueueResource.getBuildJobById(COURSE_ID, "job-1").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void getBuildJobById_fallsBackToTheFinishedJobsInTheDatabase() {
        // A build that finished while the page was open is neither running nor queued, but it is still the job that was asked for.
        withProcessingJobs();
        var finished = finishedBuildJob("job-3", COURSE_ID);
        when(processingJobs.get("job-3")).thenReturn(null);
        when(distributedDataAccessService.getQueuedJobs()).thenReturn(new ArrayList<>());
        when(buildJobRepository.findWithDataByBuildJobId("job-3")).thenReturn(Optional.of(finished));

        var response = buildJobQueueResource.getBuildJobById(COURSE_ID, "job-3");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
    }

    @Test
    void getBuildJobById_forAFinishedJobOfAnotherCourse_isNotFound() {
        withProcessingJobs();
        var finished = finishedBuildJob("job-3", 999L);
        when(processingJobs.get("job-3")).thenReturn(null);
        when(distributedDataAccessService.getQueuedJobs()).thenReturn(new ArrayList<>());
        when(buildJobRepository.findWithDataByBuildJobId("job-3")).thenReturn(Optional.of(finished));

        assertThat(buildJobQueueResource.getBuildJobById(COURSE_ID, "job-3").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void getQueuedBuildJobs_returnsOnlyTheJobsOfThatCourse() {
        asInstructorOfTheCourse();
        var ofTheCourse = List.of(job("job-1", COURSE_ID));
        when(distributedDataAccessService.getQueuedJobsForCourse(COURSE_ID)).thenReturn(ofTheCourse);

        assertThat(buildJobQueueResource.getQueuedBuildJobsForCourse(COURSE_ID).getBody()).isEqualTo(ofTheCourse);
    }

    @Test
    void getQueuedBuildJobs_forSomebodyWhoDoesNotTeachTheCourse_isRefused() {
        asSomebodyElse();

        assertThatExceptionOfType(AccessForbiddenException.class).isThrownBy(() -> buildJobQueueResource.getQueuedBuildJobsForCourse(COURSE_ID));
        verify(distributedDataAccessService, never()).getQueuedJobsForCourse(COURSE_ID);
    }

    @Test
    void getRunningBuildJobs_returnsOnlyTheJobsOfThatCourse() {
        asInstructorOfTheCourse();
        var ofTheCourse = List.of(job("job-1", COURSE_ID));
        when(distributedDataAccessService.getProcessingJobsForCourse(COURSE_ID)).thenReturn(ofTheCourse);

        assertThat(buildJobQueueResource.getRunningBuildJobsForCourse(COURSE_ID).getBody()).isEqualTo(ofTheCourse);
    }

    @Test
    void getRunningBuildJobs_forSomebodyWhoDoesNotTeachTheCourse_isRefused() {
        asSomebodyElse();

        assertThatExceptionOfType(AccessForbiddenException.class).isThrownBy(() -> buildJobQueueResource.getRunningBuildJobsForCourse(COURSE_ID));
        verify(distributedDataAccessService, never()).getProcessingJobsForCourse(COURSE_ID);
    }

    @Test
    void cancelBuildJob_cancelsAQueuedJobOfTheCourse() {
        asInstructorOfTheCourse();
        withProcessingJobs();
        when(processingJobs.get("job-1")).thenReturn(null);
        when(distributedDataAccessService.getQueuedJobs()).thenReturn(new ArrayList<>(List.of(job("job-1", COURSE_ID))));

        assertThat(buildJobQueueResource.cancelBuildJob(COURSE_ID, "job-1").getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(localCIBuildJobQueueService).cancelBuildJob("job-1");
    }

    @Test
    void cancelBuildJob_cancelsARunningJobOfTheCourse() {
        asInstructorOfTheCourse();
        withProcessingJobs();
        when(processingJobs.get("job-1")).thenReturn(job("job-1", COURSE_ID));

        assertThat(buildJobQueueResource.cancelBuildJob(COURSE_ID, "job-1").getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(localCIBuildJobQueueService).cancelBuildJob("job-1");
    }

    @Test
    void cancelBuildJob_forARunningJobOfAnotherCourse_isRefused() {
        // Build job ids are not course scoped, so without this an instructor could stop any other course's build.
        asInstructorOfTheCourse();
        withProcessingJobs();
        when(processingJobs.get("job-1")).thenReturn(job("job-1", 999L));

        assertThatExceptionOfType(AccessForbiddenException.class).isThrownBy(() -> buildJobQueueResource.cancelBuildJob(COURSE_ID, "job-1"));
        verify(localCIBuildJobQueueService, never()).cancelBuildJob(any());
    }

    @Test
    void cancelBuildJob_forAQueuedJobOfAnotherCourse_isRefused() {
        asInstructorOfTheCourse();
        withProcessingJobs();
        when(processingJobs.get("job-1")).thenReturn(null);
        when(distributedDataAccessService.getQueuedJobs()).thenReturn(new ArrayList<>(List.of(job("job-1", 999L))));

        assertThatExceptionOfType(AccessForbiddenException.class).isThrownBy(() -> buildJobQueueResource.cancelBuildJob(COURSE_ID, "job-1"));
        verify(localCIBuildJobQueueService, never()).cancelBuildJob(any());
    }

    @Test
    void cancelBuildJob_forAJobThatAlreadyFinished_isStillAccepted() {
        // Nothing is left to cancel, so there is nothing to protect and the request stays a no-op rather than an error.
        asInstructorOfTheCourse();
        withProcessingJobs();
        when(processingJobs.get("job-1")).thenReturn(null);
        when(distributedDataAccessService.getQueuedJobs()).thenReturn(new ArrayList<>());

        assertThat(buildJobQueueResource.cancelBuildJob(COURSE_ID, "job-1").getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(localCIBuildJobQueueService).cancelBuildJob("job-1");
    }

    @Test
    void cancelBuildJob_forSomebodyWhoDoesNotTeachTheCourse_isRefused() {
        // Build job ids are not course scoped, so without this check an instructor could stop another course's builds.
        asSomebodyElse();

        assertThatExceptionOfType(AccessForbiddenException.class).isThrownBy(() -> buildJobQueueResource.cancelBuildJob(COURSE_ID, "job-1"));
        verify(localCIBuildJobQueueService, never()).cancelBuildJob(any());
    }

    @Test
    void cancelAllQueuedBuildJobs_cancelsOnlyTheQueuedJobsOfThatCourse() {
        asInstructorOfTheCourse();

        assertThat(buildJobQueueResource.cancelAllQueuedBuildJobs(COURSE_ID).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(localCIBuildJobQueueService).cancelAllQueuedBuildJobsForCourse(COURSE_ID);
    }

    @Test
    void cancelAllQueuedBuildJobs_forSomebodyWhoDoesNotTeachTheCourse_isRefused() {
        asSomebodyElse();

        assertThatExceptionOfType(AccessForbiddenException.class).isThrownBy(() -> buildJobQueueResource.cancelAllQueuedBuildJobs(COURSE_ID));
        verify(localCIBuildJobQueueService, never()).cancelAllQueuedBuildJobsForCourse(COURSE_ID);
    }

    @Test
    void cancelAllRunningBuildJobs_cancelsOnlyTheRunningJobsOfThatCourse() {
        asInstructorOfTheCourse();

        assertThat(buildJobQueueResource.cancelAllRunningBuildJobs(COURSE_ID).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(localCIBuildJobQueueService).cancelAllRunningBuildJobsForCourse(COURSE_ID);
    }

    @Test
    void cancelAllRunningBuildJobs_forSomebodyWhoDoesNotTeachTheCourse_isRefused() {
        asSomebodyElse();

        assertThatExceptionOfType(AccessForbiddenException.class).isThrownBy(() -> buildJobQueueResource.cancelAllRunningBuildJobs(COURSE_ID));
        verify(localCIBuildJobQueueService, never()).cancelAllRunningBuildJobsForCourse(COURSE_ID);
    }

    @Test
    void getBuildJobStatistics_countsTheBuildsOfTheLastWeekByDefault() {
        when(buildJobRepository.getBuildJobsResultsStatisticsForCourse(any(ZonedDateTime.class), org.mockito.ArgumentMatchers.anyLong())).thenReturn(List.of());

        var response = buildJobQueueResource.getBuildJobStatistics(COURSE_ID, 7);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        var since = org.mockito.ArgumentCaptor.forClass(ZonedDateTime.class);
        verify(buildJobRepository).getBuildJobsResultsStatisticsForCourse(since.capture(), org.mockito.ArgumentMatchers.anyLong());
        assertThat(since.getValue()).isCloseTo(ZonedDateTime.now().minusDays(7), org.assertj.core.api.Assertions.within(1, java.time.temporal.ChronoUnit.MINUTES));
    }

    @Test
    void getFinishedBuildJobs_returnsThePageAndTheHeadersTheClientPagesWith() {
        // The client pages through the build history by following the link header, so the page alone is not enough.
        var request = new org.springframework.mock.web.MockHttpServletRequest("GET", "/api/programming/courses/1/finished-jobs");
        org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(new org.springframework.web.context.request.ServletRequestAttributes(request));
        try {
            var finished = finishedBuildJob("job-3", COURSE_ID);
            when(localCIBuildJobQueueService.getFilteredFinishedBuildJobs(any(), org.mockito.ArgumentMatchers.eq(COURSE_ID)))
                    .thenReturn(new org.springframework.data.domain.SliceImpl<>(List.of(finished), org.springframework.data.domain.PageRequest.of(0, 20), false));

            var response = buildJobQueueResource.getFinishedBuildJobsForCourse(COURSE_ID, null);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).hasSize(1);
            assertThat(response.getHeaders().containsHeader("Link")).isTrue();
        }
        finally {
            org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();
        }
    }

    @Test
    void getBuildJobEstimatedStartDate_forAParticipationIdThatCannotExist_isABadRequest() {
        // The id comes straight from a query parameter, and a non-positive one would search the queue for a job nobody owns.
        assertThat(buildJobQueueResource.getBuildJobEstimatedStartDate(0L).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(buildJobQueueResource.getBuildJobEstimatedStartDate(-1L).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(localCIBuildJobQueueService, never()).getBuildJobEstimatedStartDate(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void getBuildJobEstimatedStartDate_returnsTheEstimateForThatParticipation() {
        ZonedDateTime estimate = ZonedDateTime.now().plusMinutes(2);
        when(localCIBuildJobQueueService.getBuildJobEstimatedStartDate(10L)).thenReturn(estimate);

        assertThat(buildJobQueueResource.getBuildJobEstimatedStartDate(10L).getBody()).isEqualTo(estimate);
    }
}
