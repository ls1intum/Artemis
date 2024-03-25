package de.tum.in.www1.artemis.service.authorization;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.service.authorization.db.StudentDBAuthorizationCheck;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;

public interface StudentAuthorizationCheck extends TeachingAssistantAuthorizationCheck, StudentDBAuthorizationCheck {

    /**
     * checks if the currently logged-in user is student in the given course
     *
     * @param course the course that needs to be checked
     * @param user   the user whose permissions should be checked
     * @return true, if user is student of this course, otherwise false
     */
    @CheckReturnValue
    default boolean isStudentInCourse(@NotNull Course course, @Nullable User user) {
        if (!userIsLoaded(user)) {
            return isStudentInCourse(course.getId());
        }
        return user.getGroups().contains(course.getStudentGroupName());
    }

    /**
     * checks if the currently logged-in user is only a student of this course. This means the user is NOT a tutor, NOT an editor, NOT an instructor and NOT an ADMIN
     *
     * @param course the course that needs to be checked
     * @param user   the user whose permissions should be checked
     * @return true, if user is only student of this course, otherwise false
     */
    @CheckReturnValue
    default boolean isOnlyStudentInCourse(@NotNull Course course, @Nullable User user) {
        if (!userIsLoaded(user)) {
            return isStudentInCourse(course.getId()) && !isAtLeastTeachingAssistantInCourse(course.getId());
        }
        return user.getGroups().contains(course.getStudentGroupName()) && !isAtLeastTeachingAssistantInCourse(course, user);
    }

    /**
     * checks if the passed user is at least a student in the given course
     *
     * @param course the course that needs to be checked
     * @param user   the user whose permissions should be checked
     * @return true if the passed user is at least a student in the course (also if the user is teaching assistant, instructor or admin), false otherwise
     */
    @CheckReturnValue
    default boolean isAtLeastStudentInCourse(@NotNull Course course, @Nullable User user) {
        if (!userIsLoaded(user)) {
            return isAtLeastStudentInCourse(course.getId());
        }
        return isStudentInCourse(course, user) || isTeachingAssistantInCourse(course, user) || isEditorInCourse(course, user) || isInstructorInCourse(course, user)
                || isAdmin(user);
    }

    /**
     * Checks if the passed user is at least a student in the given course.
     * Throws an AccessForbiddenException if the user has no access which returns a 403
     *
     * @param course the course that needs to be checked
     * @param user   the user whose permissions should be checked
     */
    default void checkIsAtLeastStudentInCourseElseThrow(@NotNull Course course, @Nullable User user) {
        if (!isAtLeastStudentInCourse(course, user)) {
            throw new AccessForbiddenException("Course", course.getId());
        }
    }

    /**
     * checks if the currently logged-in user is at least a student in the course of the given exercise.
     *
     * @param exercise belongs to a course that will be checked for permission rights
     * @return true if the currently logged-in user is at least a student (also if the user is teaching assistant, instructor or admin), false otherwise
     */
    @CheckReturnValue
    default boolean isAtLeastStudentForExercise(@NotNull Exercise exercise) {
        return isAtLeastStudentForExercise(exercise.getId());
    }

    /**
     * checks if the currently logged-in user is at least a student in the course of the given exercise.
     *
     * @param exercise belongs to a course that will be checked for permission rights
     * @param user     the user whose permissions should be checked
     * @return true if the currently logged-in user is at least a student (also if the user is teaching assistant, instructor or admin), false otherwise
     */
    @CheckReturnValue
    default boolean isAtLeastStudentForExercise(@NotNull Exercise exercise, @Nullable User user) {
        if (!userIsLoaded(user)) {
            return isAtLeastStudentInCourse(exercise.getCourseViaExerciseGroupOrCourseMember().getId());
        }
        return isStudentInCourse(exercise.getCourseViaExerciseGroupOrCourseMember(), user) || isAtLeastTeachingAssistantForExercise(exercise, user);
    }
}
