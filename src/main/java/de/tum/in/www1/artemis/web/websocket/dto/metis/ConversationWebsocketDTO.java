package de.tum.in.www1.artemis.web.websocket.dto.metis;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.web.rest.metis.conversation.dtos.ConversationDTO;

/**
 * DTO that is included as payload for conversation related websocket messages
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ConversationWebsocketDTO(ConversationDTO conversation, MetisCrudAction metisCrudAction) {

}
