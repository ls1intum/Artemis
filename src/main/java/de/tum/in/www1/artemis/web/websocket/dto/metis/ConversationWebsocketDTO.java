package de.tum.in.www1.artemis.web.websocket.dto.metis;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.web.rest.metis.conversation.dtos.ConversationDTO;

/**
 * DTO that is included as payload for conversation related websocket messages
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ConversationWebsocketDTO {

    private ConversationDTO conversation;

    private MetisCrudAction metisCrudAction;

    public ConversationWebsocketDTO(ConversationDTO conversationDTO, MetisCrudAction metisCrudAction) {
        this.conversation = conversationDTO;
        this.metisCrudAction = metisCrudAction;
    }

    public ConversationDTO getConversation() {
        return conversation;
    }

    public void setConversation(ConversationDTO conversation) {
        this.conversation = conversation;
    }

    public MetisCrudAction getMetisCrudAction() {
        return metisCrudAction;
    }

    public void setMetisCrudAction(MetisCrudAction metisCrudAction) {
        this.metisCrudAction = metisCrudAction;
    }

    public MetisCrudAction getCrudAction() {
        return metisCrudAction;
    }

    public void setCrudAction(MetisCrudAction metisCrudAction) {
        this.metisCrudAction = metisCrudAction;
    }
}
