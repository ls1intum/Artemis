package de.tum.cit.aet.artemis.atlas.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.atlas.dto.metrics.StudentMetricsDTO;
import de.tum.cit.aet.artemis.atlas.service.LearningMetricsService;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceAtLeastStudentInCourse;
import de.tum.cit.aet.artemis.core.util.TimeLogUtil;

@ConditionalOnProperty(name = "artemis.atlas.enabled", havingValue = "true")
@RestController
@RequestMapping("api/atlas/metrics/")
public class MetricsResource {

    private static final Logger log = LoggerFactory.getLogger(MetricsResource.class);

    private final LearningMetricsService learningMetricsService;

    private final UserRepository userRepository;

    public MetricsResource(LearningMetricsService learningMetricsService, UserRepository userRepository) {
        this.learningMetricsService = learningMetricsService;
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
        long start = System.nanoTime();
        final var userId = userRepository.getUserIdElseThrow(); // won't throw exception since EnforceRoleInResource checks existence of user
        log.debug("REST request to get the metrics for the user with id {} in the course with id {}", userId, courseId);
        final var studentMetrics = learningMetricsService.getStudentCourseMetrics(userId, courseId);
        log.info("Metrics for user with id {} in course with id {} fetched in {}", userId, courseId, TimeLogUtil.formatDurationFrom(start));
        return ResponseEntity.ok(studentMetrics);
    }
}
