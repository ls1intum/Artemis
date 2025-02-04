package de.tum.cit.aet.artemis.atlas.profile.util;

import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.atlas.domain.profile.CourseLearnerProfile;
import de.tum.cit.aet.artemis.atlas.domain.profile.LearnerProfile;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.test_repository.UserTestRepository;

@Service
public class LearnerProfileUtilService {

    @Autowired
    private UserTestRepository userTestRepository;

    public void createLearnerProfilesForUsers(String userPrefix) {
        Set<User> users = userTestRepository.findAllByUserPrefix(userPrefix).stream().peek(user -> {
            LearnerProfile learnerProfile = new LearnerProfile();
            learnerProfile.setUser(user);
            user.setLearnerProfile(learnerProfile);
        }).collect(Collectors.toSet());
        userTestRepository.saveAll(users);
    }

    public void createCourseLearnerProfileForUsers(String userPrefix, Set<Course> courses) {
        Set<User> users = userTestRepository.findAllByUserPrefix(userPrefix).stream().peek(user -> {
            LearnerProfile learnerProfile = new LearnerProfile();
            learnerProfile.setUser(user);
            user.setLearnerProfile(learnerProfile);

            Set<CourseLearnerProfile> clps = courses.stream().map((course) -> {
                CourseLearnerProfile clp = new CourseLearnerProfile();
                clp.setCourse(course);
                clp.setLearnerProfile(learnerProfile);
                return clp;
            }).collect(Collectors.toSet());
            learnerProfile.setCourseLearnerProfiles(clps);
        }).collect(Collectors.toSet());

        userTestRepository.saveAll(users);
    }
}
