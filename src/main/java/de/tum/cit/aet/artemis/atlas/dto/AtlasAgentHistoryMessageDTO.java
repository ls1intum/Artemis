package de.tum.cit.aet.artemis.atlas.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO for Atlas Agent conversation history messages.
 * Used to transfer chat history between backend and frontend.
 * @param isUser  Whether the message is from the user (true) or the agent (false)
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record AtlasAgentHistoryMessageDTO(String content, boolean isUser) {
}