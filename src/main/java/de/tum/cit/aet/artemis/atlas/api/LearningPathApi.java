package de.tum.cit.aet.artemis.atlas.api;

import org.jspecify.annotations.NonNull;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.service.learningpath.LearningPathService;
import de.tum.cit.aet.artemis.course.domain.Course;

@Controller
@Conditional(AtlasEnabled.class)
@Lazy
public class LearningPathApi extends AbstractAtlasApi {

    private final LearningPathService learningPathService;

    public LearningPathApi(LearningPathService learningPathService) {
        this.learningPathService = learningPathService;
    }

    public void generateLearningPathForUser(@NonNull Course course, @NonNull User user) {
        learningPathService.generateLearningPathForUser(course, user);
    }

    public void generateLearningPaths(@NonNull Course course) {
        learningPathService.generateLearningPaths(course);
    }
}
