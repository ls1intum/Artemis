package de.tum.in.www1.artemis.web.rest.metis.conversation.dtos;

import java.util.Set;

import de.tum.in.www1.artemis.domain.metis.conversation.Conversation;

public class OneToOneChatDTO extends ConversationDTO {

    public OneToOneChatDTO(Conversation conversation) {
        super(conversation, "oneToOneChat");
    }

    public Set<ConversationUserDTO> members;

    public Set<ConversationUserDTO> getMembers() {
        return members;
    }

    public void setMembers(Set<ConversationUserDTO> members) {
        this.members = members;
    }

    @Override
    public String toString() {
        return "OneToOneChatDTO{" + "members=" + members + "} " + super.toString();
    }
}
