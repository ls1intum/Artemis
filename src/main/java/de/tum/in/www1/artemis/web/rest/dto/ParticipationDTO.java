package de.tum.in.www1.artemis.web.rest.dto;

import de.tum.in.www1.artemis.domain.participation.Participation;

/**
 * A DTO representing a participation.
 *
 * @param id       the id of the participation
 * @param exercise the exercise DTO, the participation belongs to
 */
public record ParticipationDTO(long id, ExerciseDTO exercise) {

    /**
     * Converts a participation to a participation DTO.
     *
     * @param participation the participation to convert
     * @return the participation DTO
     */
    public static ParticipationDTO of(Participation participation) {
        return new ParticipationDTO(participation.getId(), ExerciseDTO.of(participation.getExercise()));
    }
}
