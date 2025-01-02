package de.tum.cit.aet.artemis.atlas.api;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_ATLAS;

import jakarta.validation.constraints.NotNull;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.atlas.service.learningpath.LearningPathService;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;

@Controller
@Profile(PROFILE_ATLAS)
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
