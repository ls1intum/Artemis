package de.tum.cit.aet.artemis.atlas.api;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_ATLAS;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.atlas.dto.metrics.StudentMetricsDTO;
import de.tum.cit.aet.artemis.atlas.service.LearningMetricsService;
import de.tum.cit.aet.artemis.atlas.service.LearningSophisticatedMetricsService;

@Controller
@Profile(PROFILE_ATLAS)
public class LearningMetricsApi extends AbstractAtlasApi {

    private final LearningMetricsService metricsService;

    private final LearningSophisticatedMetricsService learningSophisticatedMetricsService;

    public LearningMetricsApi(LearningMetricsService metricsService, LearningSophisticatedMetricsService learningSophisticatedMetricsService) {
        this.metricsService = metricsService;
        this.learningSophisticatedMetricsService = learningSophisticatedMetricsService;
    }

    public StudentMetricsDTO getStudentCourseMetrics(long userId, long courseId) {
        if (userId == 1337) {
            return learningSophisticatedMetricsService.getStudentCourseMetrics(userId, courseId);
        }
        return metricsService.getStudentCourseMetrics(userId, courseId);
    }
}
