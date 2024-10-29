package de.tum.cit.aet.artemis.communication.dto;

import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.communication.domain.conversation.Conversation;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class OneToOneChatDTO extends ConversationDTO {

    public Set<ConversationUserDTO> members;

    public Set<ConversationUserDTO> getMembers() {
        return members;
    }

    public void setMembers(Set<ConversationUserDTO> members) {
        this.members = members;
    }

    public OneToOneChatDTO(Conversation conversation) {
        super(conversation, "oneToOneChat");
    }

    public OneToOneChatDTO() {
        super("oneToOneChat");
    }

    @Override
    public String toString() {
        return "OneToOneChatDTO{" + "members=" + members + "} " + super.toString();
    }
}
