package de.tum.in.www1.artemis.web.websocket.dto.metis;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.metis.ChatSession;

/**
 * DTO that is included as payload for post related websocket messages
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ChatSessionDTO {

    private ChatSession chatSession;

    private CrudAction crudAction;

    public ChatSessionDTO(ChatSession chatSession, CrudAction crudAction) {
        this.chatSession = chatSession;
        this.crudAction = crudAction;
    }

    public ChatSession getChatSession() {
        return chatSession;
    }

    public void setChatSession(ChatSession chatSession) {
        this.chatSession = chatSession;
    }

    public CrudAction getCrudAction() {
        return crudAction;
    }

    public void setCrudAction(CrudAction crudAction) {
        this.crudAction = crudAction;
    }
}
