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

    public CourseLearnerProfileService(CourseLearnerProfileRepository courseLearnerProfileRepository, LearnerProfileService learnerProfileService,
            LearnerProfileRepository learnerProfileRepository) {
        this.courseLearnerProfileRepository = courseLearnerProfileRepository;
        this.learnerProfileRepository = learnerProfileRepository;
    }

    public void createCourseLearnerProfile(Course course, User user) {
        var courseProfile = new CourseLearnerProfile();
        courseProfile.setCourse(course);

        var learnerProfile = learnerProfileRepository.findByUserElseThrow(user);
        courseProfile.setLearnerProfile(learnerProfile);

        courseLearnerProfileRepository.save(courseProfile);
    }

    public void createCourseLearnerProfiles(Course course, Set<User> users) {
        Set<CourseLearnerProfile> courseProfiles = users.stream().map(user -> {
            var courseProfile = new CourseLearnerProfile();
            courseProfile.setCourse(course);
            courseProfile.setLearnerProfile(user.getLearnerProfile());

            return courseProfile;
        }).collect(Collectors.toSet());

        courseLearnerProfileRepository.saveAll(courseProfiles);
    }

    public void deleteCourseLearnerProfile(Course course, User user) {
        courseLearnerProfileRepository.deleteByCourseAndUser(course, user);
    }

    public void deleteAllForCourse(Course course) {
        courseLearnerProfileRepository.deleteAllByCourse(course);
    }
}
