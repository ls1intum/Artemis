package de.tum.in.www1.artemis.web.rest;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.security.annotations.EnforceAtLeastInstructor;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.connectors.localci.LocalCISharedBuildJobQueueService;
import de.tum.in.www1.artemis.service.connectors.localci.dto.LocalCIBuildJobQueueItem;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;

@Profile("localci")
@RestController
@RequestMapping("/api")
public class BuildJobQueueResource {

    private final Logger log = LoggerFactory.getLogger(BuildJobQueueResource.class);

    private final LocalCISharedBuildJobQueueService localCIBuildJobQueueService;

    private final AuthorizationCheckService authorizationCheckService;

    private final CourseRepository courseRepository;

    public BuildJobQueueResource(LocalCISharedBuildJobQueueService localCIBuildJobQueueService, AuthorizationCheckService authorizationCheckService,
            CourseRepository courseRepository) {
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
    @GetMapping("/build-job-queue/queued/{courseId}")
    @EnforceAtLeastInstructor
    public ResponseEntity<List<LocalCIBuildJobQueueItem>> getQueuedBuildJobsForCourse(Long courseId) {
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
    @GetMapping("/build-job-queue/running/{courseId}")
    @EnforceAtLeastInstructor
    public ResponseEntity<List<LocalCIBuildJobQueueItem>> getRunningBuildJobsForCourse(Long courseId) {
        log.debug("REST request to get the running build jobs for course {}", courseId);
        Course course = courseRepository.findByIdElseThrow(courseId);
        if (!authorizationCheckService.isAtLeastInstructorInCourse(course, null)) {
            throw new AccessForbiddenException("You are not allowed to access running build jobs of this course!");
        }
        List<LocalCIBuildJobQueueItem> runningBuildJobs = localCIBuildJobQueueService.getProcessingJobsForCourse(courseId);
        return ResponseEntity.ok(runningBuildJobs);
    }

}
