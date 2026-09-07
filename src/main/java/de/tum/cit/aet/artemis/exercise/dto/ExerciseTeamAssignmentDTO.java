package de.tum.cit.aet.artemis.exercise.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The team assigned to the requesting student for one team exercise.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExerciseTeamAssignmentDTO(long exerciseId, long teamId) {
}
