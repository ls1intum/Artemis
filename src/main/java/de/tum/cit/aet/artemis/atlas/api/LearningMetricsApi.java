package de.tum.cit.aet.artemis.atlas.api;

import java.util.Optional;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.atlas.dto.metrics.StudentMetricsDTO;
import de.tum.cit.aet.artemis.atlas.service.LearningMetricsService;

@Controller
public class LearningMetricsApi extends AbstractAtlasApi {

    private final Optional<LearningMetricsService> learningMetricsService;

    protected LearningMetricsApi(Environment environment, Optional<LearningMetricsService> learningMetricsService) {
        super(environment);
        this.learningMetricsService = learningMetricsService;
    }

    public StudentMetricsDTO getStudentCourseMetrics(long userId, long courseId) {
        return getOrThrow(learningMetricsService).getStudentCourseMetrics(userId, courseId);
    }
}
