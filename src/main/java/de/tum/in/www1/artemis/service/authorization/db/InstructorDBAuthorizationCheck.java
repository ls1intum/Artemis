package de.tum.in.www1.artemis.service.authorization.db;

import javax.annotation.CheckReturnValue;

import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.authorization.AuthorizationCheck;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;

public interface InstructorDBAuthorizationCheck extends AuthorizationCheck {

    /**
     * Checks if the current user is an instructor in the given course.
     *
     * @param courseId the id of the course that needs to be checked
     * @return true if the user is an instructor in the course, false otherwise
     */
    @CheckReturnValue
    default boolean isInstructorInCourse(long courseId) {
        final var login = SecurityUtils.getCurrentUserLogin();
        return login.filter(s -> getAuthorizationRepository().isInstructorInCourse(s, courseId)).isPresent();
    }

    /**
     * Checks if the current user is at least an instructor in the given course.
     *
     * @param courseId the id of the course that needs to be checked
     * @return true if the user is at least an instructor in the course, false otherwise
     */
    @CheckReturnValue
    default boolean isAtLeastInstructorInCourse(long courseId) {
        final var login = SecurityUtils.getCurrentUserLogin();
        return login.filter(s -> getAuthorizationRepository().isAtLeastInstructorInCourse(s, courseId)).isPresent();
    }

    /**
     * Checks if the current user is at least an instructor in the given course.
     * Throws an AccessForbiddenException if the user has no access which returns a 403
     *
     * @param courseId the id of the course that needs to be checked
     */
    default void checkIsAtLeastInstructorInCourseElseThrow(long courseId) {
        if (!isAtLeastInstructorInCourse(courseId)) {
            throw new AccessForbiddenException("Course", courseId);
        }
    }

    /**
     * Checks if the current user is at least an instructor for the given exercise.
     *
     * @param exerciseId the id of the exercise that needs to be checked
     * @return true if the user is at least an instructor in the course, false otherwise
     */
    @CheckReturnValue
    default boolean isAtLeastInstructorForExercise(long exerciseId) {
        final var login = SecurityUtils.getCurrentUserLogin();
        return login.filter(s -> getAuthorizationRepository().isAtLeastInstructorForExercise(s, exerciseId)).isPresent();
    }
}
