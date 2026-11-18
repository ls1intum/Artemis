package de.tum.cit.aet.artemis.exercise.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;

/**
 * A minimal DTO containing only the information needed for exercise deletion/reset progress tracking.
 * This avoids loading full Exercise entities with all their associations.
 *
 * @param id    the exercise ID
 * @param title the exercise title (for progress messages)
 * @param type  the exercise type (to determine weight calculation)
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExerciseDeletionInfoDTO(long id, String title, ExerciseType type) {

    /**
     * Checks if this exercise is a programming exercise.
     *
     * @return true if this is a programming exercise
     */
    public boolean isProgrammingExercise() {
        return type == ExerciseType.PROGRAMMING;
    }
}
