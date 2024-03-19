package de.tum.in.www1.artemis.web.websocket.dto;

import com.fasterxml.jackson.databind.JsonNode;

import de.tum.in.www1.artemis.domain.participation.Participation;

/**
 * DTO for a submission patch. Represents changes conducted by a peer while collaborating
 * submission of an exercise. The patch is a JSON array in format of a JSON patch (RFC 6902).
 *
 * @param participation
 * @param patch
 */
public record SubmissionPatch(Participation participation, JsonNode patch) {
}
