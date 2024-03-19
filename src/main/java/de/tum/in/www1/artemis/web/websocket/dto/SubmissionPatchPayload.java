package de.tum.in.www1.artemis.web.websocket.dto;

/**
 * DTO for a submission patch payload. Is used to broadcast changes conducted by a peer while collaborating
 * on a submission of an exercise, to all collaborating peers.
 *
 * @param submissionPatch
 * @param sender
 */
public record SubmissionPatchPayload(SubmissionPatch submissionPatch, String sender) {
}
