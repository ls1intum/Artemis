package de.tum.cit.aet.artemis.exercise.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * We use it in {@link de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository#findExerciseAndGroupIdsByExerciseIds}
 * which makes it fine to use long instead of Long, as we are sending the entity from the server to the client.
 *
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExerciseWithExerciseGroupIdDTO(long exerciseId, long exerciseGroupId) {
}
