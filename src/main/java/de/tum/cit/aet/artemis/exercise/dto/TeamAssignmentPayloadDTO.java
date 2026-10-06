package de.tum.cit.aet.artemis.exercise.dto;

import java.io.Serializable;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Tells a student that they were assigned to, or removed from, a team of one exercise.
 * <p>
 * An unassignment carries no team and no participations.
 *
 * @param exerciseId            the exercise the assignment is about
 * @param teamId                the team the student now belongs to, or {@code null} when they were unassigned
 * @param studentParticipations the participations of that team, which the client renders in place of its own
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TeamAssignmentPayloadDTO(long exerciseId, @Nullable Long teamId, List<TeamParticipationDTO> studentParticipations) implements Serializable {
}
