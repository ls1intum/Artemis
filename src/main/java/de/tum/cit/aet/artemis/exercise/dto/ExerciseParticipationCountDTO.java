package de.tum.cit.aet.artemis.exercise.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The number of participations of one exercise.
 *
 * @param exerciseId the id of the exercise
 * @param count      the number of participations in the exercise
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY) // Note: Primitive long will be included even if 0
public record ExerciseParticipationCountDTO(long exerciseId, long count) {
}
