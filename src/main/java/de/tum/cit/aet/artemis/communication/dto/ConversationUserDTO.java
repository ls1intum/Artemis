package de.tum.cit.aet.artemis.communication.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.dto.UserPublicInfoDTO;

/**
 * Extension of the UserPublicInfoDTO with special flags for the conversation context
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
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
