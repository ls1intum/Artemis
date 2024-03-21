package de.tum.in.www1.artemis.service.authorization;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.service.authorization.db.TeachingAssistantDBAuthorizationCheck;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;

public interface TeachingAssistantAuthorizationCheck extends EditorAuthorizationCheck, TeachingAssistantDBAuthorizationCheck {

    /**
     * checks if the currently logged-in user is teaching assistant of this course
     *
     * @param course the course that needs to be checked
     * @param user   the user whose permissions should be checked
     * @return true, if user is teaching assistant of this course, otherwise false
     */
    @CheckReturnValue
    default boolean isTeachingAssistantInCourse(@NotNull Course course, @Nullable User user) {
        if (!userIsLoaded(user)) {
            return isTeachingAssistantInCourse(course.getId());
        }
        return user.getGroups().contains(course.getTeachingAssistantGroupName());
    }

    /**
     * Checks if the passed user is at least a teaching assistant in the given course.
     *
     * @param course the course that needs to be checked
     * @param user   the user whose permissions should be checked
     * @return true if the passed user is at least a teaching assistant in the course (also if the user is instructor or admin), false otherwise
     */
    @CheckReturnValue
    default boolean isAtLeastTeachingAssistantInCourse(@NotNull Course course, @Nullable User user) {
        if (!userIsLoaded(user)) {
            return isAtLeastTeachingAssistantInCourse(course.getId());
        }
        return isTeachingAssistantInCourse(course, user) || isEditorInCourse(course, user) || isInstructorInCourse(course, user) || isAdmin(user);
    }

    /**
     * Checks if the passed user is at least a teaching assistant in the given course.
     * Throws an AccessForbiddenException if the user has no access which returns a 403
     *
     * @param course the course that needs to be checked
     * @param user   the user whose permissions should be checked
     */
    default void checkIsAtLeastTeachingAssistantInCourseElseThrow(@NotNull Course course, @Nullable User user) {
        if (!isAtLeastTeachingAssistantInCourse(course, user)) {
            throw new AccessForbiddenException("Course", course.getId());
        }
    }

    /**
     * Checks if the currently logged-in user is at least a teaching assistant in the course of the given exercise.
     * The course is identified from either {@link Exercise#course(Course)} or {@link Exam#getCourse()}
     *
     * @param exercise belongs to a course that will be checked for permission rights
     * @return true if the currently logged-in user is at least a teaching assistant (also if the user is instructor or admin), false otherwise
     */
    @CheckReturnValue
    default boolean isAtLeastTeachingAssistantForExercise(@NotNull Exercise exercise) {
        return isAtLeastTeachingAssistantInCourse(exercise.getCourseViaExerciseGroupOrCourseMember().getId());
    }

    /**
     * Checks if the passed user is at least a teaching assistant in the course of the given exercise.
     * The course is identified from {@link Exercise#getCourseViaExerciseGroupOrCourseMember()}
     *
     * @param exercise the exercise that needs to be checked
     * @param user     the user whose permissions should be checked
     * @return true if the passed user is at least a teaching assistant (also if the user is instructor or admin), false otherwise
     */
    @CheckReturnValue
    default boolean isAtLeastTeachingAssistantForExercise(@NotNull Exercise exercise, @Nullable User user) {
        if (!userIsLoaded(user)) {
            return isAtLeastTeachingAssistantInCourse(exercise.getId());
        }
        return isAtLeastTeachingAssistantInCourse(exercise.getCourseViaExerciseGroupOrCourseMember(), user);
    }
}
