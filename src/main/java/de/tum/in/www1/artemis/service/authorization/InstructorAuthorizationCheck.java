package de.tum.in.www1.artemis.service.authorization;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.service.authorization.db.InstructorDBAuthorizationCheck;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;

public interface InstructorAuthorizationCheck extends AdminAuthorizationCheck, InstructorDBAuthorizationCheck {

    /**
     * checks if the passed user is instructor in the given course
     *
     * @param course the course that needs to be checked
     * @param user   the user whose permissions should be checked
     * @return true, if user is instructor of this course, otherwise false
     */
    @CheckReturnValue
    default boolean isInstructorInCourse(@NotNull Course course, @Nullable User user) {
        if (!userIsLoaded(user)) {
            return isInstructorInCourse(course.getId());
        }
        return user.getGroups().contains(course.getInstructorGroupName());
    }

    /**
     * checks if the passed user is at least instructor in the given course
     *
     * @param course the course that needs to be checked
     * @param user   the user whose permissions should be checked
     * @return true if the passed user is at least instructor in the course (also if the user is admin), false otherwise
     */
    @CheckReturnValue
    default boolean isAtLeastInstructorInCourse(@NotNull Course course, @Nullable User user) {
        if (!userIsLoaded(user)) {
            return isAtLeastInstructorInCourse(course.getId());
        }
        return user.getGroups().contains(course.getInstructorGroupName()) || isAdmin(user);
    }

    /**
     * Checks if the passed user is at least instructor in the given course.
     * Throws an AccessForbiddenException if the user has no access which returns a 403
     *
     * @param course the course that needs to be checked
     * @param user   the user whose permissions should be checked
     */
    default void checkIsAtLeastInstructorInCourseElseThrow(@NotNull Course course, @Nullable User user) {
        if (!isAtLeastInstructorInCourse(course, user)) {
            throw new AccessForbiddenException("Course", course.getId());
        }
    }

    /**
     * Checks if the currently logged-in user is at least an instructor in the course of the given exercise.
     * The course is identified from either exercise. Course or exercise.exerciseGroup.exam.course
     *
     * @param exercise belongs to a course that will be checked for permission rights
     * @param user     the user whose permissions should be checked
     * @return true if the currently logged-in user is at least an instructor (or admin), false otherwise
     */
    @CheckReturnValue
    default boolean isAtLeastInstructorForExercise(@NotNull Exercise exercise, @Nullable User user) {
        return isAtLeastInstructorInCourse(exercise.getCourseViaExerciseGroupOrCourseMember(), user);
    }

    /**
     * checks if the currently logged-in user is at least an instructor in the course of the given exercise.
     *
     * @param exercise belongs to a course that will be checked for permission rights
     * @return true if the currently logged-in user is at least an instructor (or admin), false otherwise
     */
    @CheckReturnValue
    default boolean isAtLeastInstructorForExercise(@NotNull Exercise exercise) {
        return isAtLeastInstructorForExercise(exercise.getId());
    }
}
