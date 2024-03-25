package de.tum.in.www1.artemis.service.authorization.db;

import javax.annotation.CheckReturnValue;

import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.authorization.AuthorizationCheck;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;

public interface EditorDBAuthorizationCheck extends AuthorizationCheck {

    /**
     * Checks if the current user is an editor in the given course.
     *
     * @param courseId the id of the course that needs to be checked
     * @return true if the user is an editor in the course, false otherwise
     */
    @CheckReturnValue
    default boolean isEditorInCourse(long courseId) {
        final var login = SecurityUtils.getCurrentUserLogin();
        return login.filter(s -> getAuthorizationRepository().isEditorInCourse(s, courseId)).isPresent();
    }

    /**
     * Checks if the current user is at least an editor in the given course.
     *
     * @param courseId the id of the course that needs to be checked
     * @return true if the user is at least an editor in the course, false otherwise
     */
    @CheckReturnValue
    default boolean isAtLeastEditorInCourse(long courseId) {
        final var login = SecurityUtils.getCurrentUserLogin();
        return login.filter(s -> getAuthorizationRepository().isAtLeastEditorInCourse(s, courseId)).isPresent();
    }

    /**
     * Checks if the current user is at least an editor in the given course.
     * Throws an AccessForbiddenException if the user has no access which returns a 403
     *
     * @param courseId the id of the course that needs to be checked
     */
    default void checkIsAtLeastEditorInCourseElseThrow(long courseId) {
        if (!isAtLeastEditorInCourse(courseId)) {
            throw new AccessForbiddenException("Course", courseId);
        }
    }

    /**
     * Checks if the current user is at least an editor for the given exercise.
     *
     * @param exerciseId the id of the exercise that needs to be checked
     * @return true if the user is at least an instructor in the course, false otherwise
     */
    @CheckReturnValue
    default boolean isAtLeastEditorForExercise(long exerciseId) {
        final var login = SecurityUtils.getCurrentUserLogin();
        return login.filter(s -> getAuthorizationRepository().isAtLeastEditorForExercise(s, exerciseId)).isPresent();
    }
}
