package de.tum.cit.aet.artemis.exercise.dto;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;

/** Nullable scores distinguish missing evidence from an assessed zero. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExercisePeerScoreDTO(long exerciseId, String exerciseTitle, ExerciseType exerciseType, ZonedDateTime releaseDate, Double ownScore, Double peerAverage,
        long peerCount) {
}
