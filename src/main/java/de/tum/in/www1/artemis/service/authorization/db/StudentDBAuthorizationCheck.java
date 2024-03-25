package de.tum.in.www1.artemis.service.authorization.db;

import javax.annotation.CheckReturnValue;

import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.authorization.AuthorizationCheck;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;

public interface StudentDBAuthorizationCheck extends AuthorizationCheck {

    /**
     * Checks if the current user is a student in the given course.
     *
     * @param courseId the id of the course that needs to be checked
     * @return true if the user is a student in the course, false otherwise
     */
    @CheckReturnValue
    default boolean isStudentInCourse(long courseId) {
        final var login = SecurityUtils.getCurrentUserLogin();
        return login.filter(s -> getAuthorizationRepository().isStudentInCourse(s, courseId)).isPresent();
    }

    /**
     * Checks if the current user is at least a student in the given course.
     *
     * @param courseId the id of the course that needs to be checked
     * @return true if the user is at least a student in the course, false otherwise
     */
    @CheckReturnValue
    default boolean isAtLeastStudentInCourse(long courseId) {
        final var login = SecurityUtils.getCurrentUserLogin();
        return login.filter(s -> getAuthorizationRepository().isAtLeastStudentInCourse(s, courseId)).isPresent();
    }

    /**
     * Checks if the current user is at least a student in the given course.
     * Throws an AccessForbiddenException if the user has no access which returns a 403
     *
     * @param courseId the id of the course that needs to be checked
     */
    default void checkIsAtLeastStudentInCourseElseThrow(long courseId) {
        if (!isAtLeastStudentInCourse(courseId)) {
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
    default boolean isAtLeastStudentForExercise(long exerciseId) {
        final var login = SecurityUtils.getCurrentUserLogin();
        return login.filter(s -> getAuthorizationRepository().isAtLeastStudentForExercise(s, exerciseId)).isPresent();
    }
}
