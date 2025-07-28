package de.tum.cit.aet.artemis.communication.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.dto.UserPublicInfoDTO;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ConversationUserDTO(UserPublicInfoDTO publicInfo, Boolean isChannelModerator, Boolean isRequestingUser) {

    public ConversationUserDTO(User user) {
        this(new UserPublicInfoDTO(user), null, null);
    }

    public ConversationUserDTO(User user, Boolean isChannelModerator, Boolean isRequestingUser) {
        this(new UserPublicInfoDTO(user), isChannelModerator, isRequestingUser);
    }

    public String login() {
        return publicInfo.login();
    }

    /**
     * Creates a new UserPublicInfoDTO with role properties assigned based on the
     * given course and user
     *
     * @param course the course to check the roles for
     * @param user   the user to check the roles for
     * @return a new UserPublicInfoDTO with assigned roles
     */
    public ConversationUserDTO withRoles(Course course, User user) {
        return new ConversationUserDTO(publicInfo.withRoles(course, user), this.isChannelModerator, this.isRequestingUser);
    }

    /**
     * Creates a new ConversationUserDTO with updated channel moderator status
     *
     * @param isChannelModerator the new channel moderator status
     * @return a new ConversationUserDTO with updated channel moderator status
     */
    public ConversationUserDTO withChannelModerator(Boolean isChannelModerator) {
        return new ConversationUserDTO(this.publicInfo, isChannelModerator, this.isRequestingUser);
    }

}
