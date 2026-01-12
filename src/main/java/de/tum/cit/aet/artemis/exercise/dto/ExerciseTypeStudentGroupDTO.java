package de.tum.cit.aet.artemis.exercise.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;

/**
 * DTO representing an exercise type and its associated student group name.
 * Used for the two-query optimization approach to count active students.
 *
 * @param exerciseType     the exercise type
 * @param studentGroupName the student group name associated with the exercise's course
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExerciseTypeStudentGroupDTO(ExerciseType exerciseType, String studentGroupName) {

    public ExerciseTypeStudentGroupDTO(Class<?> exerciseType, String studentGroupName) {
        this(ExerciseType.getExerciseTypeFromClass(exerciseType.asSubclass(Exercise.class)), studentGroupName);
    }
}
