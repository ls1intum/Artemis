package de.tum.cit.aet.artemis.atlas.service.profile;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.domain.profile.CourseLearnerProfile;
import de.tum.cit.aet.artemis.atlas.domain.profile.LearnerProfile;
import de.tum.cit.aet.artemis.atlas.repository.CourseLearnerProfileRepository;
import de.tum.cit.aet.artemis.atlas.repository.LearnerProfileRepository;
import de.tum.cit.aet.artemis.course.domain.Course;

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

        // Ask the database rather than the association: User#learnerProfile is lazy, so a user loaded without it looks
        // profile-less even when a profile exists, and creating a second one leaves the first reachable from nowhere.
        var learnerProfile = learnerProfileService.getOrCreateLearnerProfile(user);

        var courseProfile = new CourseLearnerProfile();
        courseProfile.setCourse(course);

        // Initialize values in the middle of Likert scale
        courseProfile.setAimForGradeOrBonus(3);
        courseProfile.setRepetitionIntensity(3);
        courseProfile.setTimeInvestment(3);

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

        // Ask the database per user rather than reading the lazy association, for the reason given above.
        users.forEach(learnerProfileService::getOrCreateLearnerProfile);

        Set<LearnerProfile> learnerProfiles = learnerProfileRepository.findAllByUserIn(users);

        Set<CourseLearnerProfile> courseProfiles = users.stream().map(user -> courseLearnerProfileRepository.findByLoginAndCourse(user.getLogin(), course).orElseGet(() -> {

            CourseLearnerProfile courseProfile = new CourseLearnerProfile();
            courseProfile.setCourse(course);
            LearnerProfile learnerProfile = learnerProfiles.stream().filter(profile -> profile.getUser().getId().equals(user.getId())).findFirst()
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
