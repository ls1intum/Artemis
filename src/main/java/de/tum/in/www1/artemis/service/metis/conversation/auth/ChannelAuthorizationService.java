package de.tum.in.www1.artemis.service.metis.conversation.auth;

import java.util.List;

import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.metis.conversation.Channel;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.repository.metis.ConversationParticipantRepository;
import de.tum.in.www1.artemis.repository.metis.conversation.ChannelRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;

@Service
public class ChannelAuthorizationService extends ConversationAuthorizationService {

    private final ChannelRepository channelRepository;

    public ChannelAuthorizationService(UserRepository userRepository, AuthorizationCheckService authorizationCheckService,
            ConversationParticipantRepository conversationParticipantRepository, ChannelRepository channelRepository) {
        super(conversationParticipantRepository, userRepository, authorizationCheckService);
        this.channelRepository = channelRepository;
    }

    /**
     * Checks if a user is allowed to create a channel in a course or throws an exception if not
     *
     * @param course the course the channel should be created in
     * @param user   the user that wants to create the channel
     */
    public void isAllowedToCreateChannel(@NotNull Course course, @Nullable User user) {
        user = getUserIfNecessary(user);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, user);
    }

    /**
     * Checks if a user is allowed to edit a channel or throws an exception if not
     *
     * @param channel the channel that should be edited
     * @param user    the user that wants to edit the channel
     */
    public void isAllowedToUpdateChannel(@NotNull Channel channel, @Nullable User user) {
        user = getUserIfNecessary(user);
        if (!hasChannelAdminRights(channel.getId(), user)) {
            throw new AccessForbiddenException("You are not allowed to update this channel");
        }
    }

    /**
     * Checks if a user is allowed to delete a channel or throws an exception if not
     *
     * @param course the course in which the channel is located
     * @param user   the user that wants to delete the channel
     */
    public void isAllowedToDeleteChannel(@NotNull Course course, @Nullable User user) {
        user = getUserIfNecessary(user);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, user);
    }

    /**
     * Checks if a user is a member of a channel
     *
     * @param channelId the id of the channel
     * @param userId    the id of the user
     * @return true if the user is a member of the channel, false otherwise
     */
    public boolean isMember(Long channelId, Long userId) {
        return conversationParticipantRepository.findConversationParticipantByConversationIdAndUserId(channelId, userId).isPresent();
    }

    /**
     * Checks if a user is a admin of a channel
     *
     * @param channelId the id of the channel
     * @param userId    the id of the user
     * @return true if the user is a admin of the channel, false otherwise
     */
    public boolean isChannelAdmin(Long channelId, Long userId) {
        return conversationParticipantRepository.findAdminConversationParticipantByConversationIdAndUserId(channelId, userId).isPresent();
    }

    /**
     * Checks if a user has admin rights to a channel
     * <p>
     * Note: Either the user is a course instructor or a channel admin to have admin rights
     *
     * @param channelId the id of the channel
     * @param user      the user to check
     * @return true if the user has admin rights, false otherwise
     */
    public boolean hasChannelAdminRights(Long channelId, @Nullable User user) {
        user = getUserIfNecessary(user);
        var channel = channelRepository.findById(channelId);
        return isChannelAdmin(channelId, user.getId()) || authorizationCheckService.isAtLeastInstructorInCourse(channel.get().getCourse(), user);
    }

    /**
     * Checks if a user is allowed to register users to a channel or throws an exception if not
     *
     * @param channel    the channel the users should be registered to
     * @param userLogins the logins of the users that should be registered
     * @param user       the user that wants to register the users
     */
    public void isAllowedToRegisterUsersToChannel(@NotNull Channel channel, List<String> userLogins, @Nullable User user) {
        user = getUserIfNecessary(user);
        if (hasChannelAdminRights(channel.getId(), user)) {
            return;
        }
        var isSelfRegistration = userLogins.size() == 1 && userLogins.get(0).equals(user.getLogin());
        if (!isSelfRegistration) {
            throw new AccessForbiddenException("You are not allowed to registers other users to this channel");
        }
        if (isSelfRegistration && !channel.getIsPublic()) {
            throw new AccessForbiddenException("You are not allowed to register yourself to a private channel");
        }
    }

    /**
     * Checks if a user is allowed to grant channel admin rights to a user or throws an exception if not
     *
     * @param channel the channel the rights should be granted to
     * @param user    the user that wants to grant the channel admin rights
     */
    public void isAllowedToGrantChannelAdmin(@NotNull Channel channel, @Nullable User user) {
        user = getUserIfNecessary(user);
        if (!hasChannelAdminRights(channel.getId(), user)) {
            throw new AccessForbiddenException("You are not allowed to grant channel admin rights");
        }
    }

    /**
     * Checks if a user is allowed to revoke channel admin rights from a user or throws an exception if not
     *
     * @param channel the channel the rights should be revoked from
     * @param user    the user that wants to revoke the channel admin rights
     */
    public void isAllowedToRevokeChannelAdmin(@NotNull Channel channel, @Nullable User user) {
        user = getUserIfNecessary(user);
        if (!hasChannelAdminRights(channel.getId(), user)) {
            throw new AccessForbiddenException("You are not allowed to revoke channel admin rights");
        }
    }

    /**
     * Checks if a user is allowed to remove a user from a channel or throws an exception if not
     *
     * @param channel    the channel the user should be removed from
     * @param userLogins the logins of the users that should be removed
     * @param user       the user that wants to remove the users
     */
    public void isAllowedToDeregisterUsersFromChannel(@NotNull Channel channel, List<String> userLogins, @Nullable User user) {
        user = getUserIfNecessary(user);
        if (hasChannelAdminRights(channel.getId(), user)) {
            return;
        }
        var isChannelMember = isMember(channel.getId(), user.getId());
        if (!isChannelMember) {
            throw new AccessForbiddenException("User is not a member of the channel");
        }
        var isSelfDeRegistration = userLogins.size() == 1 && userLogins.get(0).equals(user.getLogin());
        if (!isSelfDeRegistration) {
            throw new AccessForbiddenException("You are not allowed to deregister other users from this channel");
        }
    }

    /**
     * Checks if a user is allowed to archive a channel or throws an exception if not
     *
     * @param channel the channel that should be archived
     * @param user    the user that wants to archive the channel
     */
    public void isAllowedToArchiveChannel(@NotNull Channel channel, @Nullable User user) {
        isAllowedToChangeArchivalStatus(channel, user);
    }

    /**
     * Checks if a user is allowed to unarchive a channel or throws an exception if not
     *
     * @param channel the channel that should be unarchived
     * @param user    the user that wants to unarchive the channel
     */
    public void isAllowedToUnArchiveChannel(@NotNull Channel channel, @Nullable User user) {
        isAllowedToChangeArchivalStatus(channel, user);
    }

    private void isAllowedToChangeArchivalStatus(Channel channel, @Nullable User user) {
        user = getUserIfNecessary(user);
        if (!hasChannelAdminRights(channel.getId(), user)) {
            throw new AccessForbiddenException("You are not allowed to update this channel");
        }
    }

}
