package de.tum.cit.aet.artemis.atlas.api;

import java.util.Optional;

import jakarta.validation.constraints.NotNull;

import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.atlas.service.learningpath.LearningPathService;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;

@Controller
public class LearningPathApi {

    private final Optional<LearningPathService> optionalLearningPathService;

    public LearningPathApi(Optional<LearningPathService> optionalLearningPathService) {
        this.optionalLearningPathService = optionalLearningPathService;
    }

    public void generateLearningPathForUser(@NotNull Course course, @NotNull User user) {
        optionalLearningPathService.ifPresent(service -> generateLearningPathForUser(course, user));
    }

    public void generateLearningPaths(@NotNull Course course) {
        optionalLearningPathService.ifPresent(service -> service.generateLearningPaths(course));
    }
}
