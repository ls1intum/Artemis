package de.tum.cit.aet.artemis.atlas.api;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Optional;

import jakarta.validation.constraints.NotNull;

import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.atlas.service.learningpath.LearningPathService;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;

@Profile(PROFILE_CORE)
@Controller
public class LearningPathApi extends AbstractAtlasApi {

    private final Optional<LearningPathService> optionalLearningPathService;

    public LearningPathApi(Environment environment, Optional<LearningPathService> optionalLearningPathService) {
        super(environment);
        this.optionalLearningPathService = optionalLearningPathService;
    }

    public void generateLearningPathForUser(@NotNull Course course, @NotNull User user) {
        optionalLearningPathService.ifPresent(service -> service.generateLearningPathForUser(course, user));
    }

    public void generateLearningPaths(@NotNull Course course) {
        optionalLearningPathService.ifPresent(service -> service.generateLearningPaths(course));
    }
}
