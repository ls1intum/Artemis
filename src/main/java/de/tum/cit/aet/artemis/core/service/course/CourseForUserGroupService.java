package de.tum.cit.aet.artemis.core.service.course;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;

@Service
@Profile(PROFILE_CORE)
public class CourseForUserGroupService {

    private final CourseRepository courseRepository;

    private final AuthorizationCheckService authCheckService;

    public CourseForUserGroupService(CourseRepository courseRepository, AuthorizationCheckService authCheckService) {
        this.courseRepository = courseRepository;
        this.authCheckService = authCheckService;
    }

    public List<Course> getCoursesForTutors(User user, boolean onlyActive) {
        List<Course> userCourses = courseRepository.findCoursesForAtLeastTutorWithGroups(user.getGroups(), authCheckService.isAdmin(user));
        if (onlyActive) {
            // only include courses that have NOT been finished
            final var now = ZonedDateTime.now();
            userCourses = userCourses.stream().filter(course -> course.getEndDate() == null || course.getEndDate().isAfter(now)).toList();
        }
        return userCourses;
    }
}
