package de.tum.cit.aet.artemis.exercise.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;

/**
 * DTO representing an exercise type and its associated course id.
 * Used for the optimized two-query approach to count active students via UserCourseRole.
 *
 * @param exerciseType the exercise type
 * @param courseId     the id of the course the exercise belongs to
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExerciseTypeCourseDTO(ExerciseType exerciseType, Long courseId) {

    public ExerciseTypeCourseDTO(Class<?> exerciseType, Long courseId) {
        this(ExerciseType.getExerciseTypeFromClass(exerciseType.asSubclass(Exercise.class)), courseId);
    }
}
