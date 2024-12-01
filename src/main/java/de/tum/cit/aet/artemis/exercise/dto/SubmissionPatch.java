package de.tum.cit.aet.artemis.exercise.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;

/**
 * DTO for a submission patch. Represents changes conducted by a peer while collaborating
 * submission of an exercise. The patch is a JSON array in format of a JSON patch (RFC 6902).
 *
 * @param participation the participation the patch is related to
 * @param patch         the patch that is to be applied to the submission
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record SubmissionPatch(Participation participation, JsonNode patch) {
}
