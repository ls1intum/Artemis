package de.tum.in.www1.artemis.web.rest.localci;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.security.annotations.EnforceAtLeastInstructor;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.connectors.localci.SharedQueueManagementService;
import de.tum.in.www1.artemis.service.connectors.localci.dto.LocalCIBuildJobQueueItem;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;

@Profile("localci")
@RestController
@RequestMapping("api/")
public class BuildJobQueueResource {

    private static final Logger log = LoggerFactory.getLogger(BuildJobQueueResource.class);

    private final SharedQueueManagementService localCIBuildJobQueueService;

    private final AuthorizationCheckService authorizationCheckService;

    private final CourseRepository courseRepository;

    public BuildJobQueueResource(SharedQueueManagementService localCIBuildJobQueueService, AuthorizationCheckService authorizationCheckService, CourseRepository courseRepository) {
        this.localCIBuildJobQueueService = localCIBuildJobQueueService;
        this.authorizationCheckService = authorizationCheckService;
        this.courseRepository = courseRepository;
    }

    /**
     * Returns the queued build jobs for the given course.
     *
     * @param courseId the id of the course for which to get the queued build jobs
     * @return the queued build jobs
     */
    @GetMapping("courses/{courseId}/queued-jobs")
    @EnforceAtLeastInstructor
    public ResponseEntity<List<LocalCIBuildJobQueueItem>> getQueuedBuildJobsForCourse(@PathVariable long courseId) {
        log.debug("REST request to get the queued build jobs for course {}", courseId);
        Course course = courseRepository.findByIdElseThrow(courseId);
        if (!authorizationCheckService.isAtLeastInstructorInCourse(course, null)) {
            throw new AccessForbiddenException("You are not allowed to access queued build jobs of this course!");
        }
        List<LocalCIBuildJobQueueItem> buildJobQueue = localCIBuildJobQueueService.getQueuedJobsForCourse(courseId);
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
    public ResponseEntity<List<LocalCIBuildJobQueueItem>> getRunningBuildJobsForCourse(@PathVariable long courseId) {
        log.debug("REST request to get the running build jobs for course {}", courseId);
        Course course = courseRepository.findByIdElseThrow(courseId);
        if (!authorizationCheckService.isAtLeastInstructorInCourse(course, null)) {
            throw new AccessForbiddenException("You are not allowed to access running build jobs of this course!");
        }
        List<LocalCIBuildJobQueueItem> runningBuildJobs = localCIBuildJobQueueService.getProcessingJobsForCourse(courseId);
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

}
