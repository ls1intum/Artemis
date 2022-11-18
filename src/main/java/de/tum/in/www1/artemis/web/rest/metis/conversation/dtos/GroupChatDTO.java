package de.tum.in.www1.artemis.web.rest.metis.conversation.dtos;

import java.util.Set;

import de.tum.in.www1.artemis.domain.metis.conversation.GroupChat;

public class GroupChatDTO extends ConversationDTO {

    private String name;

    public GroupChatDTO(GroupChat groupChat) {
        super(groupChat, "groupChat");
        this.name = groupChat.getName();
    }

    public GroupChatDTO() {
        this.setType("groupChat");
    }

    public Set<ConversationUserDTO> members;

    public Set<ConversationUserDTO> getMembers() {
        return members;
    }

    public void setMembers(Set<ConversationUserDTO> members) {
        this.members = members;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return "GroupChatDTO{" + "name='" + name + '\'' + "members=" + members + "} " + super.toString();
    }
}
