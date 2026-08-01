package de.tum.cit.aet.artemis.communication.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO sent by tutors when approving an Iris-generated answer message. The optional content field
 * allows a tutor to edit the answer in the same request that approves it.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public record VerifyAnswerMessageDTO(String content) {
}
