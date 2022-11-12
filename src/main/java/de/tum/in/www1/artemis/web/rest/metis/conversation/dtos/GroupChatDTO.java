package de.tum.in.www1.artemis.web.rest.metis.conversation.dtos;

import java.util.List;

import de.tum.in.www1.artemis.domain.metis.conversation.Conversation;
import de.tum.in.www1.artemis.domain.metis.conversation.GroupChat;

public class GroupChatDTO extends ConversationDTO {

    // does not include the requesting user
    public List<String> namesOfOtherMembers;

    public GroupChatDTO(GroupChat groupChat) {
        super(groupChat, "groupChat");
    }

    public GroupChatDTO(Conversation conversation, String type) {
        super(conversation, type);
    }

    public List<String> getNamesOfOtherMembers() {
        return namesOfOtherMembers;
    }

    public void setNamesOfOtherMembers(List<String> namesOfOtherMembers) {
        this.namesOfOtherMembers = namesOfOtherMembers;
    }

    @Override
    public String toString() {
        return "GroupChatDTO{" + "namesOfOtherMembers=" + (namesOfOtherMembers == null ? "" : namesOfOtherMembers.toString()) + "} " + super.toString();
    }
}
