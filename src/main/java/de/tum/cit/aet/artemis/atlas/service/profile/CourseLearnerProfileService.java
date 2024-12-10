package de.tum.cit.aet.artemis.atlas.service.profile;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.atlas.domain.profile.CourseLearnerProfile;
import de.tum.cit.aet.artemis.atlas.repository.CourseLearnerProfileRepository;
import de.tum.cit.aet.artemis.atlas.repository.LearnerProfileRepository;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;

@Profile(PROFILE_CORE)
@Service
public class CourseLearnerProfileService {

    private final CourseLearnerProfileRepository courseLearnerProfileRepository;

    private final LearnerProfileRepository learnerProfileRepository;

    public CourseLearnerProfileService(CourseLearnerProfileRepository courseLearnerProfileRepository, LearnerProfileRepository learnerProfileRepository) {
        this.courseLearnerProfileRepository = courseLearnerProfileRepository;
        this.learnerProfileRepository = learnerProfileRepository;
    }

    /**
     * Create a course learner profile for a user and saves it in the database
     *
     * @param course the course for which the profile is created
     * @param user   the user for which the profile is created
     */
    public void createCourseLearnerProfile(Course course, User user) {
        var courseProfile = new CourseLearnerProfile();
        courseProfile.setCourse(course);

        var learnerProfile = learnerProfileRepository.findByUserElseThrow(user);
        courseProfile.setLearnerProfile(learnerProfile);

        courseLearnerProfileRepository.save(courseProfile);
    }

    /**
     * Create course learner profiles for a set of users and saves them in the database.
     *
     * @param course the course for which the profiles are created
     * @param users  the users for which the profiles are created with eagerly loaded learner profiles
     */
    public void createCourseLearnerProfiles(Course course, Set<User> users) {
        Set<CourseLearnerProfile> courseProfiles = users.stream().map(user -> {
            var courseProfile = new CourseLearnerProfile();
            courseProfile.setCourse(course);
            courseProfile.setLearnerProfile(user.getLearnerProfile());

            return courseProfile;
        }).collect(Collectors.toSet());

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
