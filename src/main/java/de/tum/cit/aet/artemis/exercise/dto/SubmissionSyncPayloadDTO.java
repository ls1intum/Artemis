package de.tum.cit.aet.artemis.exercise.dto;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.dto.UserNameDTO;

/**
 * Broadcasts a team member's saved text submission to the rest of the team.
 *
 * @param submission the submission as it was saved
 * @param sender     the team member who saved it; the client ignores its own echo by matching the login
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record SubmissionSyncPayloadDTO(TeamTextSubmissionDTO submission, UserNameDTO sender) implements Serializable {
}
