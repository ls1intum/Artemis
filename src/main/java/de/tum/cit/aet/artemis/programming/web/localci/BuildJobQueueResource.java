package de.tum.cit.aet.artemis.programming.web.localci;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALCI;

import java.time.ZonedDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import de.tum.cit.aet.artemis.buildagent.dto.BuildJobQueueItem;
import de.tum.cit.aet.artemis.buildagent.dto.BuildJobResultCountDTO;
import de.tum.cit.aet.artemis.buildagent.dto.BuildJobsStatisticsDTO;
import de.tum.cit.aet.artemis.buildagent.dto.FinishedBuildJobDTO;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.dto.pageablesearch.FinishedBuildJobPageableSearchDTO;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastInstructor;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceAtLeastInstructorInCourse;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.buildagent.domain.BuildJob;
import de.tum.cit.aet.artemis.programming.repository.BuildJobRepository;
import de.tum.cit.aet.artemis.programming.service.localci.SharedQueueManagementService;
import tech.jhipster.web.util.PaginationUtil;

@Profile(PROFILE_LOCALCI)
@RestController
@RequestMapping("api/")
public class BuildJobQueueResource {

    private static final Logger log = LoggerFactory.getLogger(BuildJobQueueResource.class);

    private final SharedQueueManagementService localCIBuildJobQueueService;

    private final AuthorizationCheckService authorizationCheckService;

    private final CourseRepository courseRepository;

    private final BuildJobRepository buildJobRepository;

    public BuildJobQueueResource(SharedQueueManagementService localCIBuildJobQueueService, AuthorizationCheckService authorizationCheckService, CourseRepository courseRepository,
            BuildJobRepository buildJobRepository) {
        this.localCIBuildJobQueueService = localCIBuildJobQueueService;
        this.authorizationCheckService = authorizationCheckService;
        this.courseRepository = courseRepository;
        this.buildJobRepository = buildJobRepository;
    }

    /**
     * Returns the queued build jobs for the given course.
     *
     * @param courseId the id of the course for which to get the queued build jobs
     * @return the queued build jobs
     */
    @GetMapping("courses/{courseId}/queued-jobs")
    @EnforceAtLeastInstructor
    public ResponseEntity<List<BuildJobQueueItem>> getQueuedBuildJobsForCourse(@PathVariable long courseId) {
        log.debug("REST request to get the queued build jobs for course {}", courseId);
        Course course = courseRepository.findByIdElseThrow(courseId);
        if (!authorizationCheckService.isAtLeastInstructorInCourse(course, null)) {
            throw new AccessForbiddenException("You are not allowed to access queued build jobs of this course!");
        }
        List<BuildJobQueueItem> buildJobQueue = localCIBuildJobQueueService.getQueuedJobsForCourse(courseId);
        return ResponseEntity.ok(buildJobQueue);
    }

    /**
     * Returns the running build jobs for the given course.
     *
     * @param courseId the id of the course for which to get the running build jobs
     * @return the running build jobs
     */
    @GetMapping("courses/{courseId}/running-jobs")
    @EnforceAtLeastInstructor
    public ResponseEntity<List<BuildJobQueueItem>> getRunningBuildJobsForCourse(@PathVariable long courseId) {
        log.debug("REST request to get the running build jobs for course {}", courseId);
        Course course = courseRepository.findByIdElseThrow(courseId);
        if (!authorizationCheckService.isAtLeastInstructorInCourse(course, null)) {
            throw new AccessForbiddenException("You are not allowed to access running build jobs of this course!");
        }
        List<BuildJobQueueItem> runningBuildJobs = localCIBuildJobQueueService.getProcessingJobsForCourse(courseId);
        return ResponseEntity.ok(runningBuildJobs);
    }

    /**
     * Cancels the build job for the given participation in the specified course.
     *
     * @param courseId   the id of the course
     * @param buildJobId the id of the build job to cancel
     * @return the ResponseEntity with the result of the cancellation
     */
    @DeleteMapping("courses/{courseId}/cancel-job/{buildJobId}")
    @EnforceAtLeastInstructor
    public ResponseEntity<Void> cancelBuildJob(@PathVariable long courseId, @PathVariable String buildJobId) {
        log.debug("REST request to cancel the build job for course {} and with id {}", courseId, buildJobId);
        Course course = courseRepository.findByIdElseThrow(courseId);
        if (!authorizationCheckService.isAtLeastInstructorInCourse(course, null)) {
            throw new AccessForbiddenException("You are not allowed to cancel the build job of this course!");
        }

        // Call the cancelBuildJob method in LocalCIBuildJobManagementService
        localCIBuildJobQueueService.cancelBuildJob(buildJobId);

        return ResponseEntity.noContent().build();
    }

    /**
     * Cancels all queued build jobs for the given course.
     *
     * @param courseId the id of the course
     * @return the ResponseEntity with the result of the cancellation
     */
    @DeleteMapping("courses/{courseId}/cancel-all-queued-jobs")
    @EnforceAtLeastInstructor
    public ResponseEntity<Void> cancelAllQueuedBuildJobs(@PathVariable long courseId) {
        log.debug("REST request to cancel all queued build jobs for course {}", courseId);
        Course course = courseRepository.findByIdElseThrow(courseId);
        if (!authorizationCheckService.isAtLeastInstructorInCourse(course, null)) {
            throw new AccessForbiddenException("You are not allowed to cancel the build job of this course!");
        }
        // Call the cancelAllQueuedBuildJobs method in LocalCIBuildJobManagementService
        localCIBuildJobQueueService.cancelAllQueuedBuildJobsForCourse(courseId);

        return ResponseEntity.noContent().build();
    }

    /**
     * Cancels all running build jobs for the given course.
     *
     * @param courseId the id of the course
     * @return the ResponseEntity with the result of the cancellation
     */
    @DeleteMapping("courses/{courseId}/cancel-all-running-jobs")
    @EnforceAtLeastInstructor
    public ResponseEntity<Void> cancelAllRunningBuildJobs(@PathVariable long courseId) {
        log.debug("REST request to cancel all running build jobs for course {}", courseId);
        Course course = courseRepository.findByIdElseThrow(courseId);
        if (!authorizationCheckService.isAtLeastInstructorInCourse(course, null)) {
            throw new AccessForbiddenException("You are not allowed to cancel the build job of this course!");
        }
        // Call the cancelAllRunningBuildJobs method in LocalCIBuildJobManagementService
        localCIBuildJobQueueService.cancelAllRunningBuildJobsForCourse(courseId);

        return ResponseEntity.noContent().build();
    }

    /**
     * Get all finished build jobs for a course
     *
     * @param courseId the id of the course
     * @param search   the search criteria
     * @return the page of finished build jobs
     */
    @GetMapping("courses/{courseId}/finished-jobs")
    @EnforceAtLeastInstructorInCourse
    public ResponseEntity<List<FinishedBuildJobDTO>> getFinishedBuildJobsForCourse(@PathVariable long courseId, FinishedBuildJobPageableSearchDTO search) {
        log.debug("REST request to get the finished build jobs for course {}", courseId);
        Page<BuildJob> buildJobPage = localCIBuildJobQueueService.getFilteredFinishedBuildJobs(search, courseId);
        Page<FinishedBuildJobDTO> finishedBuildJobDTOs = FinishedBuildJobDTO.fromBuildJobsPage(buildJobPage);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), buildJobPage);
        return new ResponseEntity<>(finishedBuildJobDTOs.getContent(), headers, HttpStatus.OK);
    }

    /**
     * Returns the build job statistics.
     *
     * @param courseId the id of the course
     * @param span     the time span in days. The statistics will be calculated for the last span days. Default is 7 days.
     * @return the build job statistics
     */
    @GetMapping("courses/{courseId}/build-job-statistics")
    @EnforceAtLeastInstructorInCourse
    public ResponseEntity<BuildJobsStatisticsDTO> getBuildJobStatistics(@PathVariable long courseId, @RequestParam(required = false, defaultValue = "7") int span) {
        log.debug("REST request to get the build job statistics");
        List<BuildJobResultCountDTO> buildJobResultCountDtos = buildJobRepository.getBuildJobsResultsStatistics(ZonedDateTime.now().minusDays(span), courseId);
        BuildJobsStatisticsDTO buildJobStatistics = BuildJobsStatisticsDTO.of(buildJobResultCountDtos);
        return ResponseEntity.ok(buildJobStatistics);
    }
}
