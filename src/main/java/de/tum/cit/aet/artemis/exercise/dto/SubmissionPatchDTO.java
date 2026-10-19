package de.tum.cit.aet.artemis.exercise.dto;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import tools.jackson.databind.JsonNode;

/**
 * Changes a team member made to a shared modeling submission, as a JSON patch (RFC 6902).
 * <p>
 * The patch travels through the server unread: it is broadcast to the rest of the team exactly as it arrived.
 *
 * @param patch the patch to apply to the submission
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record SubmissionPatchDTO(JsonNode patch) implements Serializable {
}
