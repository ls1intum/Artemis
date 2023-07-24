package de.tum.in.www1.artemis.web.rest.metis.conversation.dtos;

import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.metis.conversation.GroupChat;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class GroupChatDTO extends ConversationDTO {

    private String name;

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

    public GroupChatDTO(GroupChat groupChat) {
        super(groupChat, "groupChat");
        this.name = groupChat.getName();
    }

    public GroupChatDTO() {
        this.setType("groupChat");
    }

    @Override
    public String toString() {
        return "GroupChatDTO{" + "name='" + name + '\'' + "members=" + members + "} " + super.toString();
    }
}
