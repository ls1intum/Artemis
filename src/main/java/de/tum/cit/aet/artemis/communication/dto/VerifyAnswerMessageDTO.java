package de.tum.cit.aet.artemis.communication.dto;

import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO sent by tutors when approving an Iris-generated answer message. The optional content field
 * allows a tutor to edit the answer in the same request that approves it.
 * <p>
 * The content is capped at 5000 characters. Keep this limit in sync with the client editor (MAX_CONTENT_LENGTH in posting-create-edit.directive.ts) and the limit declared on the
 * Posting entity.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public record VerifyAnswerMessageDTO(@Size(max = 5000) String content) {
}
