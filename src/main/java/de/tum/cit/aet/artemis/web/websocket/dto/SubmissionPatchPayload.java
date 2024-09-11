package de.tum.cit.aet.artemis.web.websocket.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO for a submission patch payload. Is used to broadcast changes conducted by a peer while collaborating
 * on a submission of an exercise, to all collaborating peers.
 *
 * @param submissionPatch the patch that is to be applied to the submission
 * @param sender          the sender of the patch
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record SubmissionPatchPayload(SubmissionPatch submissionPatch, String sender) {
}
