package de.tum.cit.aet.artemis.atlas.api;

import jakarta.validation.constraints.NotNull;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.atlas.service.learningpath.LearningPathService;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;

@Controller
@ConditionalOnProperty(name = "artemis.atlas.enabled", havingValue = "true")
@Lazy
public class LearningPathApi extends AbstractAtlasApi {

    private final LearningPathService learningPathService;

    public LearningPathApi(LearningPathService learningPathService) {
        this.learningPathService = learningPathService;
    }

    public void generateLearningPathForUser(@NotNull Course course, @NotNull User user) {
        learningPathService.generateLearningPathForUser(course, user);
    }

    public void generateLearningPaths(@NotNull Course course) {
        learningPathService.generateLearningPaths(course);
    }
}
