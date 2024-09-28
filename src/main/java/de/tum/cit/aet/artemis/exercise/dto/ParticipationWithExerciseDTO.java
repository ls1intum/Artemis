package de.tum.cit.aet.artemis.exercise.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;

/**
 * A DTO representing a participation.
 *
 * @param id       the id of the participation
 * @param exercise the exercise DTO, the participation belongs to
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ParticipationWithExerciseDTO(long id, ExerciseDTO exercise) {

    /**
     * Converts a participation to a participation DTO.
     *
     * @param participation the participation to convert
     * @return the participation DTO
     */
    public static ParticipationWithExerciseDTO of(Participation participation) {
        return new ParticipationWithExerciseDTO(participation.getId(), ExerciseDTO.of(participation.getExercise()));
    }
}
