package de.tum.in.www1.artemis.web.rest.metis.conversation.dtos;

import java.util.Set;

import de.tum.in.www1.artemis.domain.metis.conversation.GroupChat;

public class GroupChatDTO extends ConversationDTO {

    public GroupChatDTO(GroupChat groupChat) {
        super(groupChat, "groupChat");
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
        return "GroupChatDTO{" + "members=" + members + "} " + super.toString();
    }
}
