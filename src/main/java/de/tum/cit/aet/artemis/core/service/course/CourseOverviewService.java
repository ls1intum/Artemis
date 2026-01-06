package de.tum.cit.aet.artemis.core.service.course;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;

/**
 * Service for retrieving courses for the course management overview.
 */
@Service
@Profile(PROFILE_CORE)
@Lazy
public class CourseOverviewService {

    private final CourseRepository courseRepository;

    private final AuthorizationCheckService authCheckService;

    private final UserRepository userRepository;

    public CourseOverviewService(CourseRepository courseRepository, AuthorizationCheckService authCheckService, UserRepository userRepository) {
        this.courseRepository = courseRepository;
        this.authCheckService = authCheckService;
        this.userRepository = userRepository;
    }

    /**
     * Fetches a list of Courses
     *
     * @param onlyActive Whether to include courses with a past endDate
     * @return A list of Courses for the course management overview
     */
    public List<Course> getAllCoursesForManagementOverview(boolean onlyActive) {
        var user = userRepository.getUserWithGroupsAndAuthorities();
        boolean isAdmin = authCheckService.isAdmin(user);
        if (isAdmin && !onlyActive) {
            // TODO: we should avoid using findAll() here, as it might return a huge amount of data
            return courseRepository.findAllWithExtendedSettings();
        }

        if (isAdmin) {
            return courseRepository.findAllNotEnded(ZonedDateTime.now());
        }
        var userGroups = new ArrayList<>(user.getGroups());

        if (onlyActive) {
            return courseRepository.findAllNotEndedCoursesByManagementGroupNames(ZonedDateTime.now(), userGroups);
        }

        return courseRepository.findAllCoursesByManagementGroupNames(userGroups);
    }
}
