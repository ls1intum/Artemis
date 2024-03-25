package de.tum.in.www1.artemis.service.authorization.db;

import javax.annotation.CheckReturnValue;

import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.authorization.AuthorizationCheck;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;

public interface TeachingAssistantDBAuthorizationCheck extends AuthorizationCheck {

    /**
     * Checks if the current user is a teaching assistant in the given course.
     *
     * @param courseId the id of the course that needs to be checked
     * @return true if the user is a teaching assistant in the course, false otherwise
     */
    @CheckReturnValue
    default boolean isTeachingAssistantInCourse(long courseId) {
        final var login = SecurityUtils.getCurrentUserLogin();
        return login.filter(s -> getAuthorizationRepository().isTeachingAssistantInCourse(s, courseId)).isPresent();
    }

    /**
     * Checks if the current user is at least a teaching assistant in the given course.
     *
     * @param courseId the id of the course that needs to be checked
     * @return true if the user is at least a teaching assistant in the course, false otherwise
     */
    @CheckReturnValue
    default boolean isAtLeastTeachingAssistantInCourse(long courseId) {
        final var login = SecurityUtils.getCurrentUserLogin();
        return login.filter(s -> getAuthorizationRepository().isAtLeastTeachingAssistantInCourse(s, courseId)).isPresent();
    }

    /**
     * Checks if the current user is at least a teaching assistant in the given course.
     * Throws an AccessForbiddenException if the user has no access which returns a 403
     *
     * @param courseId the id of the course that needs to be checked
     */
    default void checkIsAtLeastTeachingAssistantInCourseElseThrow(long courseId) {
        if (!isAtLeastTeachingAssistantInCourse(courseId)) {
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
    default boolean isAtLeastTeachingAssistantForExercise(long exerciseId) {
        final var login = SecurityUtils.getCurrentUserLogin();
        return login.filter(s -> getAuthorizationRepository().isAtLeastTeachingAssistantForExercise(s, exerciseId)).isPresent();
    }
}
