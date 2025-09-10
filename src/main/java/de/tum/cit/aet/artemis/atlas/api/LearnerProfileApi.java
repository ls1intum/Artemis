package de.tum.cit.aet.artemis.atlas.api;

import java.util.Set;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.domain.profile.LearnerProfile;
import de.tum.cit.aet.artemis.atlas.repository.LearnerProfileRepository;
import de.tum.cit.aet.artemis.atlas.service.profile.CourseLearnerProfileService;
import de.tum.cit.aet.artemis.atlas.service.profile.LearnerProfileService;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;

@Controller
@Conditional(AtlasEnabled.class)
@Lazy
public class LearnerProfileApi extends AbstractAtlasApi {

    private final LearnerProfileService learnerProfileService;

    private final CourseLearnerProfileService courseLearnerProfileService;

    private final LearnerProfileRepository learnerProfileRepository;

    public LearnerProfileApi(LearnerProfileService learnerProfileService, CourseLearnerProfileService courseLearnerProfileService,
            LearnerProfileRepository learnerProfileRepository) {
        this.learnerProfileService = learnerProfileService;
        this.courseLearnerProfileService = courseLearnerProfileService;
        this.learnerProfileRepository = learnerProfileRepository;
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

    /**
     * Get or create a learner profile for a user
     *
     * @param user the user for which the profile is retrieved or created
     * @return Saved LearnerProfile
     */
    public LearnerProfile getOrCreateLearnerProfile(User user) {
        return learnerProfileService.getOrCreateLearnerProfile(user);
    }

    /**
     * Delete a learner profile by its user
     *
     * @param user the user for which the profile is deleted
     */
    public void deleteProfile(User user) {
        learnerProfileRepository.deleteByUser(user);
    }
}
