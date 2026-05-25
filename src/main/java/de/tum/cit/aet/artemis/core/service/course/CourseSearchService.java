package de.tum.cit.aet.artemis.core.service.course;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.core.service.course.CourseServiceUtil.removeUserVariables;

import java.util.List;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.core.domain.Course;

/**
 * Service for searching users in a course.
 */
@Service
@Profile(PROFILE_CORE)
@Lazy
public class CourseSearchService {

    private final UserRepository userRepository;

    public CourseSearchService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Search for users of all user groups by login or name in course
     *
     * @param course     Course in which to search students
     * @param nameOfUser Login or name by which to search students
     * @return users whose login matched
     */
    public List<User> searchOtherUsersNameInCourse(Course course, String nameOfUser) {
        List<User> searchResult = userRepository.searchByNameInCourse(course.getId(), nameOfUser);
        removeUserVariables(searchResult);

        // users should not find themselves
        User searchingUser = userRepository.getUser();
        searchResult = searchResult.stream().distinct().filter(user -> !user.getId().equals(searchingUser.getId())).toList();

        return (searchResult);
    }
}
