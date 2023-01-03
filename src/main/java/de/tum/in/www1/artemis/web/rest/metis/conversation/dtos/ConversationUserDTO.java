package de.tum.in.www1.artemis.web.rest.metis.conversation.dtos;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.service.dto.UserPublicInfoDTO;

/**
 * Extension of the UserPublicInfoDTO with special flags for the conversation context
 */
public class ConversationUserDTO extends UserPublicInfoDTO {

    private Boolean isChannelModerator;

    private Boolean isRequestingUser;

    public ConversationUserDTO(User user) {
        super(user);
    }

    public ConversationUserDTO() {
        // Empty constructor needed for Jackson.
    }

    public Boolean getIsChannelModerator() {
        return isChannelModerator;
    }

    public void setIsChannelModerator(Boolean isChannelModerator) {
        this.isChannelModerator = isChannelModerator;
    }

    public Boolean getIsRequestingUser() {
        return isRequestingUser;
    }

    public void setIsRequestingUser(Boolean requestingUser) {
        isRequestingUser = requestingUser;
    }
}
