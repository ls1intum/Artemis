package de.tum.cit.aet.artemis.atlas.api;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.atlas.dto.metrics.StudentMetricsDTO;
import de.tum.cit.aet.artemis.atlas.service.LearningMetricsService;

@Controller
@ConditionalOnProperty(name = "artemis.atlas.enabled", havingValue = "true")
public class LearningMetricsApi extends AbstractAtlasApi {

    private final LearningMetricsService metricsService;

    public LearningMetricsApi(LearningMetricsService metricsService) {
        this.metricsService = metricsService;
    }

    public StudentMetricsDTO getStudentCourseMetrics(long userId, long courseId) {
        return metricsService.getStudentCourseMetrics(userId, courseId);
    }
}
