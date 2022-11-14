package de.tum.in.www1.artemis.web.rest.metis.conversation.dtos;

import java.util.HashSet;
import java.util.Set;

import de.tum.in.www1.artemis.domain.metis.conversation.Conversation;
import de.tum.in.www1.artemis.service.dto.UserPublicInfoDTO;

public class OneToOneChatDTO extends ConversationDTO {

    public OneToOneChatDTO(Conversation conversation) {
        super(conversation, "oneToOneChat");
    }

    public Set<UserPublicInfoDTO> members = new HashSet<>();

    public Set<UserPublicInfoDTO> getMembers() {
        return members;
    }

    public void setMembers(Set<UserPublicInfoDTO> members) {
        this.members = members;
    }

    @Override
    public String toString() {
        return "OneToOneChatDTO{} " + super.toString();
    }
}
