package de.tum.cit.aet.artemis.atlas.service.profile;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.domain.profile.CourseLearnerProfile;
import de.tum.cit.aet.artemis.atlas.repository.CourseLearnerProfileRepository;
import de.tum.cit.aet.artemis.atlas.repository.LearnerProfileRepository;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;

@Conditional(AtlasEnabled.class)
@Service
public class CourseLearnerProfileService {

    private final CourseLearnerProfileRepository courseLearnerProfileRepository;

    private final LearnerProfileRepository learnerProfileRepository;

    private final LearnerProfileService learnerProfileService;

    public CourseLearnerProfileService(CourseLearnerProfileRepository courseLearnerProfileRepository, LearnerProfileRepository learnerProfileRepository,
            LearnerProfileService learnerProfileService) {
        this.courseLearnerProfileRepository = courseLearnerProfileRepository;
        this.learnerProfileRepository = learnerProfileRepository;
        this.learnerProfileService = learnerProfileService;
    }

    /**
     * Create a course learner profile for a user and saves it in the database
     *
     * @param course the course for which the profile is created
     * @param user   the user for which the profile is created
     * @return Saved CourseLearnerProfile
     */
    public CourseLearnerProfile createCourseLearnerProfile(Course course, User user) {

        if (user.getLearnerProfile() == null) {
            learnerProfileService.createProfile(user);
        }

        var courseProfile = new CourseLearnerProfile();
        courseProfile.setCourse(course);

        // Initialize values in the middle of Likert scale
        courseProfile.setAimForGradeOrBonus(3);
        courseProfile.setRepetitionIntensity(3);
        courseProfile.setTimeInvestment(3);

        var learnerProfile = learnerProfileRepository.findByUserElseThrow(user);
        courseProfile.setLearnerProfile(learnerProfile);

        return courseLearnerProfileRepository.save(courseProfile);
    }

    /**
     * Create course learner profiles for a set of users and saves them in the database.
     *
     * @param course the course for which the profiles are created
     * @param users  the users for which the profiles are created with eagerly loaded learner profiles
     * @return A List of saved CourseLearnerProfiles
     */
    public List<CourseLearnerProfile> createCourseLearnerProfiles(Course course, Set<User> users) {

        users.stream().filter(user -> user.getLearnerProfile() == null).forEach(learnerProfileService::createProfile);

        Set<CourseLearnerProfile> courseProfiles = users.stream().map(user -> courseLearnerProfileRepository.findByLoginAndCourse(user.getLogin(), course).orElseGet(() -> {

            var courseProfile = new CourseLearnerProfile();
            courseProfile.setCourse(course);
            courseProfile.setLearnerProfile(learnerProfileRepository.findByUserElseThrow(user));

            // Initialize values in the middle of Likert scale
            courseProfile.setAimForGradeOrBonus(3);
            courseProfile.setRepetitionIntensity(3);
            courseProfile.setTimeInvestment(3);

            return courseProfile;
        })).collect(Collectors.toSet());

        return courseLearnerProfileRepository.saveAll(courseProfiles);
    }

    /**
     * Delete a course learner profile for a user
     *
     * @param course the course for which the profile is deleted
     * @param user   the user for which the profile is deleted
     */
    public void deleteCourseLearnerProfile(Course course, User user) {
        courseLearnerProfileRepository.deleteByCourseAndUser(course, user);
    }

    /**
     * Delete all course learner profiles for a course
     *
     * @param course the course for which the profiles are deleted
     */
    public void deleteAllForCourse(Course course) {
        courseLearnerProfileRepository.deleteAllByCourse(course);
    }
}
