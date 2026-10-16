package de.tum.cit.aet.artemis.atlas.profile.util;

import static de.tum.cit.aet.artemis.core.config.ArtemisConstants.SPRING_PROFILE_TEST;

import java.util.HashSet;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.atlas.domain.profile.CourseLearnerProfile;
import de.tum.cit.aet.artemis.atlas.domain.profile.LearnerProfile;
import de.tum.cit.aet.artemis.atlas.repository.CourseLearnerProfileRepository;
import de.tum.cit.aet.artemis.atlas.repository.LearnerProfileRepository;
import de.tum.cit.aet.artemis.course.domain.Course;

@Lazy
@Service
@Profile(SPRING_PROFILE_TEST)
public class LearnerProfileUtilService {

    @Autowired
    private UserTestRepository userTestRepository;

    @Autowired
    private LearnerProfileRepository learnerProfileRepository;

    @Autowired
    private CourseLearnerProfileRepository courseLearnerProfileRepository;

    /**
     * Gives every user of the given prefix a learner profile.
     *
     * @param userPrefix the prefix identifying the users of the current test
     */
    public void createLearnerProfilesForUsers(String userPrefix) {
        userTestRepository.findAllByUserPrefix(userPrefix).forEach(this::profileOf);
    }

    /**
     * Gives every user of the given prefix a learner profile with a per-course profile for each of the given courses.
     *
     * @param userPrefix the prefix identifying the users of the current test
     * @param courses    the courses to create a per-course profile for
     */
    public void createCourseLearnerProfileForUsers(String userPrefix, Set<Course> courses) {
        Set<CourseLearnerProfile> courseProfiles = new HashSet<>();
        for (User user : userTestRepository.findAllByUserPrefix(userPrefix)) {
            LearnerProfile learnerProfile = profileOf(user);
            for (Course course : courses) {
                if (courseLearnerProfileRepository.findByUserIdAndCourseId(user.getId(), course.getId()).isPresent()) {
                    continue;
                }
                CourseLearnerProfile courseLearnerProfile = new CourseLearnerProfile();
                courseLearnerProfile.setLearnerProfile(learnerProfile);
                courseLearnerProfile.setCourse(course);
                courseLearnerProfile.setAimForGradeOrBonus(1);
                courseLearnerProfile.setRepetitionIntensity(1);
                courseLearnerProfile.setTimeInvestment(1);
                courseProfiles.add(courseLearnerProfile);
            }
        }
        courseLearnerProfileRepository.saveAll(courseProfiles);
    }

    /**
     * The learner profile of a user, created if the user does not have one yet. A user may only ever have one, and
     * these methods are called once per test method against accounts that survive between them, so asking the
     * database is what keeps a second call from writing a duplicate.
     *
     * @param user the user whose profile is returned
     * @return the profile the user has
     */
    private LearnerProfile profileOf(User user) {
        return learnerProfileRepository.findByUser(user).orElseGet(() -> {
            LearnerProfile learnerProfile = new LearnerProfile();
            learnerProfile.setUser(user);
            return learnerProfileRepository.save(learnerProfile);
        });
    }
}
