package de.tum.in.www1.artemis.service.authorization;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.service.authorization.db.EditorDBAuthorizationCheck;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;

public interface EditorAuthorizationCheck extends InstructorAuthorizationCheck, EditorDBAuthorizationCheck {

    /**
     * checks if the passed user is editor in the given course
     *
     * @param course the course that needs to be checked
     * @param user   the user whose permissions should be checked
     * @return true, if user is an editor of this course, otherwise false
     */
    @CheckReturnValue
    default boolean isEditorInCourse(@NotNull Course course, @Nullable User user) {
        if (!userIsLoaded(user)) {
            return isEditorInCourse(course.getId());
        }
        return user.getGroups().contains(course.getEditorGroupName());
    }

    /**
     * Checks if the passed user is at least an editor in the given course.
     *
     * @param course the course that needs to be checked
     * @param user   the user whose permissions should be checked
     * @return true if the passed user is at least an editor in the course, false otherwise
     */
    @CheckReturnValue
    default boolean isAtLeastEditorInCourse(@NotNull Course course, @Nullable User user) {
        if (!userIsLoaded(user)) {
            return isAtLeastEditorInCourse(course.getId());
        }
        return isEditorInCourse(course, user) || isInstructorInCourse(course, user) || isAdmin(user);
    }

    /**
     * Checks if the currently logged-in user is at least an editor in the course of the given exercise.
     * The course is identified from either {@link Exercise#course(Course)} or {@link Exam#getCourse()}
     *
     * @param exercise belongs to a course that will be checked for permission rights
     * @return true if the currently logged-in user is at least an editor (also if the user is instructor or admin), false otherwise
     */
    @CheckReturnValue
    default boolean isAtLeastEditorForExercise(@NotNull Exercise exercise) {
        return isAtLeastEditorInCourse(exercise.getCourseViaExerciseGroupOrCourseMember().getId());
    }

    /**
     * Checks if the currently logged-in user is at least an editor in the course of the given exercise.
     * The course is identified from either exercise. Course or exercise.exerciseGroup.exam.course
     *
     * @param exercise belongs to a course that will be checked for permission rights
     * @param user     the user whose permissions should be checked
     * @return true if the currently logged-in user is at least an editor, false otherwise
     */
    @CheckReturnValue
    default boolean isAtLeastEditorForExercise(@NotNull Exercise exercise, @Nullable User user) {
        return isAtLeastEditorInCourse(exercise.getCourseViaExerciseGroupOrCourseMember(), user);
    }

    /**
     * Checks if the passed user is at least an editor in the given course.
     * Throws an AccessForbiddenException if the user has no access which returns a 403
     *
     * @param course the course that needs to be checked
     * @param user   the user whose permissions should be checked
     */
    default void checkIsAtLeastEditorInCourseElseThrow(@NotNull Course course, @Nullable User user) {
        if (!isAtLeastEditorInCourse(course, user)) {
            throw new AccessForbiddenException("Course", course.getId());
        }
    }
}
