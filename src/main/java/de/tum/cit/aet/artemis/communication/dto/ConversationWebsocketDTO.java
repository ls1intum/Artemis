package de.tum.cit.aet.artemis.communication.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.communication.web.conversation.dtos.ConversationDTO;

/**
 * DTO that is included as payload for conversation related websocket messages
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ConversationWebsocketDTO(ConversationDTO conversation, MetisCrudAction action) {

}
