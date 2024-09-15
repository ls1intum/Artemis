package de.tum.cit.aet.artemis.communication.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO that is included as payload for conversation related websocket messages
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ConversationWebsocketDTO(ConversationDTO conversation, MetisCrudAction action) {

}
