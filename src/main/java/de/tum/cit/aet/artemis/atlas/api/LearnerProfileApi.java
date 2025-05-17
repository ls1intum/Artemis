package de.tum.cit.aet.artemis.atlas.api;

import java.util.Set;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.service.profile.CourseLearnerProfileService;
import de.tum.cit.aet.artemis.atlas.service.profile.LearnerProfileService;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;

@Controller
@Lazy
@Conditional(AtlasEnabled.class)
public class LearnerProfileApi extends AbstractAtlasApi {

    private final LearnerProfileService learnerProfileService;

    private final CourseLearnerProfileService courseLearnerProfileService;

    public LearnerProfileApi(LearnerProfileService learnerProfileService, CourseLearnerProfileService courseLearnerProfileService) {
        this.learnerProfileService = learnerProfileService;
        this.courseLearnerProfileService = courseLearnerProfileService;
    }

    public void deleteAllForCourse(Course course) {
        courseLearnerProfileService.deleteAllForCourse(course);
    }

    public void createCourseLearnerProfile(Course course, User user) {
        courseLearnerProfileService.createCourseLearnerProfile(course, user);
    }

    public void createCourseLearnerProfiles(Course course, Set<User> students) {
        courseLearnerProfileService.createCourseLearnerProfiles(course, students);
    }

    public void deleteCourseLearnerProfile(Course course, User user) {
        courseLearnerProfileService.deleteCourseLearnerProfile(course, user);
    }

    public void createProfile(User user) {
        learnerProfileService.createProfile(user);
    }

    public void deleteProfile(User user) {
        learnerProfileService.deleteProfile(user);
    }
}
