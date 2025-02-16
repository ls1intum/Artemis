package de.tum.cit.aet.artemis.atlas.api;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.atlas.dto.metrics.StudentMetricsDTO;
import de.tum.cit.aet.artemis.atlas.service.LearningMetricsService;

@Controller
@Profile(PROFILE_CORE)
public class LearningMetricsApi extends AbstractAtlasApi {

    private final LearningMetricsService metricsService;

    public LearningMetricsApi(LearningMetricsService metricsService) {
        this.metricsService = metricsService;
    }

    public StudentMetricsDTO getStudentCourseMetrics(long userId, long courseId) {
        return metricsService.getStudentCourseMetrics(userId, courseId);
    }
}
