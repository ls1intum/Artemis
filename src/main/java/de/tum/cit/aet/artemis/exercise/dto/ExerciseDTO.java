package de.tum.cit.aet.artemis.exercise.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExerciseDTO(long id, ExerciseType type, ExerciseAthenaConfigDTO athenaConfig) {

    /**
     * Converts an exercise to an exercise DTO.
     *
     * @param exercise the exercise to convert
     * @return the exercise DTO
     */
    public static ExerciseDTO of(Exercise exercise) {
        return new ExerciseDTO(exercise.getId(), exercise.getExerciseType(), ExerciseAthenaConfigDTO.from(exercise.getAthenaConfig()));
    }
}
