package de.tum.in.www1.artemis.web.rest.dto;

import de.tum.in.www1.artemis.domain.participation.Participation;

public record ParticipationDTO(long id, ExerciseDTO exercise) {

    public static ParticipationDTO of(Participation participation) {
        return new ParticipationDTO(participation.getId(), ExerciseDTO.of(participation.getExercise()));
    }
}
