package de.tum.cit.aet.artemis.exercise.dto;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Broadcasts a team member's submission patch to the rest of the team.
 *
 * @param submissionPatch the patch to apply to the submission
 * @param sender          the login of the team member who made the change
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record SubmissionPatchPayloadDTO(SubmissionPatchDTO submissionPatch, String sender) implements Serializable {
}
