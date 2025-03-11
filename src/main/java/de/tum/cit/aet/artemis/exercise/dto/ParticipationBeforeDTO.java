package de.tum.cit.aet.artemis.exercise.dto;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exercise.domain.InitializationState;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ParticipationBeforeDTO(Long id, InitializationState initializationState, ZonedDateTime initializationDate, ExerciseBeforeDTO exercise) {

    public static ParticipationBeforeDTO of(Participation participation) {
        return new ParticipationBeforeDTO(participation.getId(), participation.getInitializationState(), participation.getInitializationDate(),
                ExerciseBeforeDTO.of(participation.getExercise()));
    }

}
