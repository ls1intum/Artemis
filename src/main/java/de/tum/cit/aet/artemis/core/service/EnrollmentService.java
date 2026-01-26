package de.tum.cit.aet.artemis.core.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.CheckReturnValue;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.Organization;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;

/**
 * Service for checking enrollment and unenrollment authorization for courses.
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class EnrollmentService {

    private final CourseRepository courseRepository;

    private final Pattern allowedCourseEnrollmentUsernamePattern;

    public EnrollmentService(CourseRepository courseRepository,
            @Nullable @Value("${artemis.user-management.course-enrollment.allowed-username-pattern:#{null}}") Pattern allowedCourseEnrollmentUsernamePattern) {
        this.courseRepository = courseRepository;
        this.allowedCourseEnrollmentUsernamePattern = allowedCourseEnrollmentUsernamePattern;
    }

    /**
     * An enum that represents the different reasons why a user is not allowed to self enroll in a course,
     * or ALLOWED if the user is allowed to self enroll in the course.
     */
    public enum EnrollmentAuthorization {
        ALLOWED, USERNAME_PATTERN, ENROLLMENT_STATUS, ENROLLMENT_PERIOD, ONLINE, ORGANIZATIONS
    }

    /**
     * Checks if the user is allowed to self enroll in the given course.
     * Returns `EnrollmentAuthorization.ALLOWED` if the user is allowed to self enroll in the course,
     * or the reason why the user is not allowed to self enroll in the course otherwise.
     * See also: {@link #checkUserAllowedToEnrollInCourseElseThrow(User, Course)}
     *
     * @param user   The user that wants to self enroll
     * @param course The course to which the user wants to self enroll
     * @return `EnrollmentAuthorization.ALLOWED` if the user is allowed to self enroll in the course,
     *         or the reason why the user is not allowed to self enroll in the course otherwise
     */
    @CheckReturnValue
    public EnrollmentAuthorization getUserEnrollmentAuthorizationForCourse(User user, Course course) {
        if (allowedCourseEnrollmentUsernamePattern != null && !allowedCourseEnrollmentUsernamePattern.matcher(user.getLogin()).matches()) {
            return EnrollmentAuthorization.USERNAME_PATTERN;
        }
        if (!Boolean.TRUE.equals(course.isEnrollmentEnabled())) {
            return EnrollmentAuthorization.ENROLLMENT_STATUS;
        }
        if (!course.enrollmentIsActive()) {
            return EnrollmentAuthorization.ENROLLMENT_PERIOD;
        }
        Set<Organization> courseOrganizations = course.getOrganizations();
        if (courseOrganizations != null && !courseOrganizations.isEmpty() && !courseRepository.checkIfUserIsMemberOfCourseOrganizations(user, course)) {
            return EnrollmentAuthorization.ORGANIZATIONS;
        }
        if (course.isOnlineCourse()) {
            return EnrollmentAuthorization.ONLINE;
        }
        return EnrollmentAuthorization.ALLOWED;
    }

    /**
     * Checks if the user is allowed to self enroll in the given course.
     * See also: {@link #checkUserAllowedToEnrollInCourseElseThrow(User, Course)}
     *
     * @param user   The user that wants to self enroll
     * @param course The course to which the user wants to self enroll
     * @return boolean, true if the user is allowed to self enroll in the course, false otherwise
     */
    @CheckReturnValue
    public boolean isUserAllowedToSelfEnrollInCourse(User user, Course course) {
        return EnrollmentAuthorization.ALLOWED.equals(getUserEnrollmentAuthorizationForCourse(user, course));
    }

    /**
     * Checks if the user is allowed to self enroll in the given course.
     * Throws an AccessForbiddenException if the user is not allowed to self enroll in the course.
     * See also: {@link #getUserEnrollmentAuthorizationForCourse(User, Course)}
     *
     * @param user   The user that wants to self enroll
     * @param course The course to which the user wants to self enroll
     */
    public void checkUserAllowedToEnrollInCourseElseThrow(User user, Course course) throws AccessForbiddenException {
        EnrollmentAuthorization auth = getUserEnrollmentAuthorizationForCourse(user, course);
        switch (auth) {
            case USERNAME_PATTERN -> throw new AccessForbiddenException("Enrollment with this username is not allowed.");
            case ENROLLMENT_STATUS -> throw new AccessForbiddenException("The course does not allow enrollment.");
            case ENROLLMENT_PERIOD -> throw new AccessForbiddenException("The course does currently not allow enrollment.");
            case ORGANIZATIONS -> throw new AccessForbiddenException("User is not member of any organization of this course.");
            case ONLINE -> throw new AccessForbiddenException("Online courses cannot be enrolled in.");
        }
    }

    /**
     * An enum that represents the different reasons why a user is not allowed to unenroll from a course,
     * or ALLOWED if the user is allowed to unenroll from the course.
     */
    public enum UnenrollmentAuthorization {
        ALLOWED, UNENROLLMENT_STATUS, UNENROLLMENT_PERIOD, ONLINE
    }

    /**
     * Checks if the user is allowed to unenroll from the given course.
     * Returns `UnenrollmentAuthorization.ALLOWED` if the user is allowed to unenroll from the course,
     * or the reason why the user is not allowed to unenroll from the course otherwise.
     * See also: {@link #checkUserAllowedToUnenrollFromCourseElseThrow(Course)}
     *
     * @param course The course from which the user wants to unenroll
     * @return `UnenrollmentAuthorization.ALLOWED` if the user is allowed to self unenroll from the course,
     *         or the reason why the user is not allowed to self unenroll from the course otherwise
     */
    @CheckReturnValue
    public UnenrollmentAuthorization getUserUnenrollmentAuthorizationForCourse(Course course) {
        if (!course.isUnenrollmentEnabled()) {
            return UnenrollmentAuthorization.UNENROLLMENT_STATUS;
        }
        if (!course.unenrollmentIsActive()) {
            return UnenrollmentAuthorization.UNENROLLMENT_PERIOD;
        }
        if (course.isOnlineCourse()) {
            return UnenrollmentAuthorization.ONLINE;
        }
        return UnenrollmentAuthorization.ALLOWED;
    }

    /**
     * Checks if the user is allowed to unenroll from the given course.
     * Throws an AccessForbiddenException if the user is not allowed to unenroll from the course.
     * See also: {@link #getUserUnenrollmentAuthorizationForCourse(Course)}
     *
     * @param course The course from which the user wants to unenroll
     */
    public void checkUserAllowedToUnenrollFromCourseElseThrow(Course course) throws AccessForbiddenException {
        UnenrollmentAuthorization auth = getUserUnenrollmentAuthorizationForCourse(course);
        switch (auth) {
            case UNENROLLMENT_STATUS, UNENROLLMENT_PERIOD -> throw new AccessForbiddenException("The course does currently not allow unenrollment.");
            case ONLINE -> throw new AccessForbiddenException("Online courses cannot be unenrolled from.");
        }
    }
}
