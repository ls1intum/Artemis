package de.tum.cit.aet.artemis.core.service.course;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;

/**
 * Service for determining whether a course is visible to a user.
 */
@Service
@Profile(PROFILE_CORE)
@Lazy
public class CourseVisibleService {

    private final AuthorizationCheckService authCheckService;

    public CourseVisibleService(AuthorizationCheckService authCheckService) {
        this.authCheckService = authCheckService;
    }

    /**
     * Checks if a course is visible for a user based on their role and the course's start date.
     *
     * @param user   the user for whom to check visibility
     * @param course the course to check visibility for
     * @return true if the course is visible for the user, false otherwise
     */
    public boolean isCourseVisibleForUser(User user, Course course) {
        // Instructors and TAs see all courses that have not yet finished
        if (authCheckService.isAtLeastTeachingAssistantInCourse(course, user)) {
            return true;
        }
        // Students see all courses that have already started (and not yet finished)
        if (user.getGroups().contains(course.getStudentGroupName())) {
            return course.getStartDate() == null || course.getStartDate().isBefore(ZonedDateTime.now());
        }

        return false;
    }
}
