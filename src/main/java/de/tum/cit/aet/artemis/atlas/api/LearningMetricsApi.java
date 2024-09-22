package de.tum.cit.aet.artemis.atlas.api;

import java.util.Optional;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.atlas.dto.metrics.StudentMetricsDTO;
import de.tum.cit.aet.artemis.atlas.service.LearningMetricsService;

@Controller
public class LearningMetricsApi extends AbstractAtlasApi {

    private final Optional<LearningMetricsService> optionalLearningMetricsService;

    public LearningMetricsApi(Environment environment, Optional<LearningMetricsService> optionalLearningMetricsService) {
        super(environment);
        this.optionalLearningMetricsService = optionalLearningMetricsService;
    }

    public StudentMetricsDTO getStudentCourseMetrics(long userId, long courseId) {
        return getOrThrow(optionalLearningMetricsService).getStudentCourseMetrics(userId, courseId);
    }
}
