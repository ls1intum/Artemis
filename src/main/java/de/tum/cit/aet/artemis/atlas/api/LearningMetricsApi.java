package de.tum.cit.aet.artemis.atlas.api;

import java.util.Optional;

import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.atlas.dto.metrics.StudentMetricsDTO;
import de.tum.cit.aet.artemis.atlas.service.LearningMetricsService;

@Controller
public class LearningMetricsApi {

    private final Optional<LearningMetricsService> optionalLearningMetricsService;

    public LearningMetricsApi(Optional<LearningMetricsService> optionalLearningMetricsService) {
        this.optionalLearningMetricsService = optionalLearningMetricsService;
    }

    public StudentMetricsDTO getStudentCourseMetrics(long userId, long courseId) {
        return getServiceOrThrow().getStudentCourseMetrics(userId, courseId);
    }

    // ToDo: Check for active Spring profiles?
    // ToDo: Inherit common class?
    public LearningMetricsService getServiceOrThrow() {
        if (optionalLearningMetricsService.isEmpty()) {
            throw new IllegalStateException("LearningMetricsService is not enabled");
        }

        return optionalLearningMetricsService.get();
    }
}
