package de.tum.cit.aet.artemis.atlas.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO for representing a single message in the conversation history.
 *
 * @param content The message content
 * @param isUser  Whether the message is from the user (true) or the agent (false)
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record AtlasAgentHistoryMessageDTO(String content, boolean isUser) {

}
