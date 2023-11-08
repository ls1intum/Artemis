package de.tum.in.www1.artemis.web.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.enumeration.ExerciseType;

/**
 * A DTO representing an exercise.
 *
 * @param id   the id of the exercise
 * @param type the type of the exercise (programming, modeling, quiz, text, file-upload)
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExerciseDTO(long id, ExerciseType type) {

    /**
     * Converts an exercise to an exercise DTO.
     *
     * @param exercise the exercise to convert
     * @return the exercise DTO
     */
    public static ExerciseDTO of(Exercise exercise) {
        return new ExerciseDTO(exercise.getId(), exercise.getExerciseType());
    }
}
