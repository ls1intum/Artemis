package de.tum.in.www1.artemis.web.websocket.dto.metis;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.metis.Conversation;

/**
 * DTO that is included as payload for conversation related websocket messages
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ConversationDTO {

    private Conversation conversation;

    private CrudAction crudAction;

    public ConversationDTO(Conversation conversation, CrudAction crudAction) {
        this.conversation = conversation;
        this.crudAction = crudAction;
    }

    public Conversation getConversation() {
        return conversation;
    }

    public void setConversation(Conversation conversation) {
        this.conversation = conversation;
    }

    public CrudAction getCrudAction() {
        return crudAction;
    }

    public void setCrudAction(CrudAction crudAction) {
        this.crudAction = crudAction;
    }
}
