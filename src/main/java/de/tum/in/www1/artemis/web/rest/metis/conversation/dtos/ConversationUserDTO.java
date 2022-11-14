package de.tum.in.www1.artemis.web.rest.metis.conversation.dtos;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.service.dto.UserPublicInfoDTO;

/**
 * Extension of the UserPublicInfoDTO with special flags for the conversation context
 */
public class ConversationUserDTO extends UserPublicInfoDTO {

    private Boolean isChannelAdmin;

    public ConversationUserDTO(User user) {
        super(user);
    }

    public ConversationUserDTO() {
        // Empty constructor needed for Jackson.
    }

    public Boolean getIsChannelAdmin() {
        return isChannelAdmin;
    }

    public void setIsChannelAdmin(Boolean channelAdmin) {
        isChannelAdmin = channelAdmin;
    }

}
