package de.tum.cit.aet.artemis.atlas.service.profile;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.domain.profile.CourseLearnerProfile;
import de.tum.cit.aet.artemis.atlas.domain.profile.LearnerProfile;
import de.tum.cit.aet.artemis.atlas.repository.CourseLearnerProfileRepository;
import de.tum.cit.aet.artemis.atlas.repository.LearnerProfileRepository;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;

@Conditional(AtlasEnabled.class)
@Lazy
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
     * If a profile already exists for this user and course, it returns the existing profile.
     *
     * @param course the course for which the profile is created
     * @param user   the user for which the profile is created
     * @return Saved CourseLearnerProfile
     */
    public CourseLearnerProfile createCourseLearnerProfile(Course course, User user) {

        // Check if a profile already exists for this user and course
        Optional<CourseLearnerProfile> existingProfile = courseLearnerProfileRepository.findByLoginAndCourse(user.getLogin(), course);
        if (existingProfile.isPresent()) {
            return existingProfile.get();
        }

        // Ensure that the user has a learner profile (lazy creation)
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
     */
    public void createCourseLearnerProfiles(Course course, Set<User> users) {

        // Ensure that all users have a learner profile (lazy creation)
        users.stream().filter(user -> user.getLearnerProfile() == null).forEach(learnerProfileService::createProfile);

        Set<LearnerProfile> learnerProfiles = learnerProfileRepository.findAllByUserIn(users);

        Set<CourseLearnerProfile> courseProfiles = users.stream().map(user -> courseLearnerProfileRepository.findByLoginAndCourse(user.getLogin(), course).orElseGet(() -> {

            CourseLearnerProfile courseProfile = new CourseLearnerProfile();
            courseProfile.setCourse(course);
            LearnerProfile learnerProfile = learnerProfiles.stream().filter(profile -> profile.getUser().equals(user)).findFirst()
                    .orElseThrow(() -> new IllegalStateException("Learner profile for user " + user.getLogin() + " not found"));

            courseProfile.setLearnerProfile(learnerProfile);

            // Initialize values in the middle of Likert scale
            courseProfile.setAimForGradeOrBonus(3);
            courseProfile.setRepetitionIntensity(3);
            courseProfile.setTimeInvestment(3);

            return courseProfile;
        })).collect(Collectors.toSet());

        courseLearnerProfileRepository.saveAll(courseProfiles);
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
