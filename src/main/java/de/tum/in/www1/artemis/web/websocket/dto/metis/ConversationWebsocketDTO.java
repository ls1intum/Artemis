package de.tum.in.www1.artemis.web.websocket.dto.metis;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.metis.conversation.Conversation;

/**
 * DTO that is included as payload for conversation related websocket messages
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ConversationWebsocketDTO {

    private Conversation conversation;

    private MetisCrudAction metisCrudAction;

    public ConversationWebsocketDTO(Conversation conversation, MetisCrudAction metisCrudAction) {
        this.conversation = conversation;
        this.metisCrudAction = metisCrudAction;
    }

    public Conversation getConversation() {
        return conversation;
    }

    public void setConversation(Conversation conversation) {
        this.conversation = conversation;
    }

    public MetisCrudAction getCrudAction() {
        return metisCrudAction;
    }

    public void setCrudAction(MetisCrudAction metisCrudAction) {
        this.metisCrudAction = metisCrudAction;
    }
}
