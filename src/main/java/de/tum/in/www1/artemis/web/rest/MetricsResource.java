package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.security.annotations.enforceRoleInCourse.EnforceAtLeastStudentInCourse;
import de.tum.in.www1.artemis.service.metrics.MetricsService;
import de.tum.in.www1.artemis.web.rest.dto.metrics.StudentMetricsDTO;

@Profile(PROFILE_CORE)
@RestController
@RequestMapping("api/metrics/")
public class MetricsResource {

    private static final Logger log = LoggerFactory.getLogger(MetricsResource.class);

    private final MetricsService metricsService;

    private final UserRepository userRepository;

    public MetricsResource(MetricsService metricsService, UserRepository userRepository) {
        this.metricsService = metricsService;
        this.userRepository = userRepository;
    }

    /**
     * GET course/:courseId/student : Gets the metrics of a course for the logged-in user.
     *
     * @param courseId the id of the course from which to get the metrics
     * @return the ResponseEntity with status 200 (OK) with body the student metrics for the course
     */
    @GetMapping("course/{courseId}/student")
    @EnforceAtLeastStudentInCourse
    public ResponseEntity<StudentMetricsDTO> getCourseMetricsForUser(@PathVariable long courseId) {
        final var userId = userRepository.getUserIdElseThrow(); // won't throw exception since EnforceRoleInResource checks existence of user
        log.debug("REST request to get the metrics for the user with id {} in the course with id {}", userId, courseId);
        final var studentMetrics = metricsService.getStudentCourseMetrics(userId, courseId);
        return ResponseEntity.ok(studentMetrics);
    }
}
