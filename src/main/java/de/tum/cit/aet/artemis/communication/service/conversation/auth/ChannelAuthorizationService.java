package de.tum.cit.aet.artemis.communication.service.conversation.auth;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.communication.domain.ConversationParticipantSettingsView;
import de.tum.cit.aet.artemis.communication.domain.conversation.Channel;
import de.tum.cit.aet.artemis.communication.repository.ConversationParticipantRepository;
import de.tum.cit.aet.artemis.communication.repository.conversation.ChannelRepository;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;

@Profile(PROFILE_CORE)
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
    public void isAllowedToCreateChannel(@NotNull Course course, @NotNull User user) {
        var userToCheck = getUserIfNecessary(user);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.TEACHING_ASSISTANT, course, userToCheck);
    }

    /**
     * Checks if a user is allowed to create a new message in a channel or throws an exception if not
     *
     * @param channel the channel the message should be created
     * @param user    the user that wants to create the message
     */
    public void isAllowedToCreateNewAnswerPostInChannel(@NotNull Channel channel, @NotNull User user) {
        var isArchivedChannel = channel.getIsArchived() != null && channel.getIsArchived();
        var userToCheck = getUserIfNecessary(user);
        if (isArchivedChannel) {
            throw new AccessForbiddenException("You are not allowed to create a new answer post in an archived channel.");
        }
        var isChannelMember = isMember(channel.getId(), userToCheck.getId());
        if (!isChannelMember) {
            throw new AccessForbiddenException("User is not a member of the channel");
        }
    }

    /**
     * Checks if a user is allowed to create a new answer message in a channel or throws an exception if not
     *
     * @param channel the channel the answer message should be created
     * @param user    the user that wants to create answer the message
     */
    public void isAllowedToCreateNewPostInChannel(@NotNull Channel channel, @NotNull User user) {
        var isAnnouncementChannel = channel.getIsAnnouncementChannel() != null && channel.getIsAnnouncementChannel();
        var isArchivedChannel = channel.getIsArchived() != null && channel.getIsArchived();
        if (isArchivedChannel) {
            throw new AccessForbiddenException("You are not allowed to create a new post in an archived channel.");
        }
        var userToCheck = getUserIfNecessary(user);
        if (isAnnouncementChannel) {
            if (!hasChannelModerationRights(channel.getId(), userToCheck)) {
                throw new AccessForbiddenException("You are not allowed to post in this channel");
            }
        }
    }

    /**
     * Checks if a user is allowed to edit a channel or throws an exception if not
     *
     * @param channel the channel that should be edited
     * @param user    the user that wants to edit the channel
     */
    public void isAllowedToUpdateChannel(@NotNull Channel channel, @NotNull User user) {
        var userToCheck = getUserIfNecessary(user);
        if (!hasChannelModerationRights(channel.getId(), userToCheck)) {
            throw new AccessForbiddenException("You are not allowed to update this channel");
        }
    }

    /**
     * Checks if a user is allowed to edit or delete messages of other users in a channel
     *
     * @param channel the channel that should be edited
     * @param user    the user that wants to edit or delete messages
     * @return true if the user is allowed to edit or delete messages in the channel, false otherwise
     */
    public boolean isAllowedToEditOrDeleteMessagesOfOtherUsers(@NotNull Channel channel, @NotNull User user) {
        var userToCheck = getUserIfNecessary(user);
        return hasChannelModerationRights(channel.getId(), userToCheck);
    }

    /**
     * Checks if a user is allowed to delete a channel or throws an exception if not
     *
     * @param channel the channel that should be deleted
     * @param user    the user that wants to delete the channel
     */
    public void isAllowedToDeleteChannel(@NotNull Channel channel, @NotNull User user) {
        var userToCheck = getUserIfNecessary(user);
        // either instructor or moderator who is also the creator
        var channelFromDb = channelRepository.findById(channel.getId()).orElseThrow();
        var isInstructor = authorizationCheckService.isAtLeastInstructorInCourse(channel.getCourse(), userToCheck);
        var isModerator = isChannelModerator(channelFromDb.getId(), userToCheck.getId());
        var isCreator = false;
        if (channelFromDb.getCreator() != null) {
            isCreator = channelFromDb.getCreator().equals(userToCheck);
        }
        if (!(isInstructor || (isModerator && isCreator))) {
            throw new AccessForbiddenException("You are not allowed to delete this channel");
        }
    }

    /**
     * Checks if a user is a member of a channel
     *
     * @param channelId the id of the channel
     * @param userId    the id of the user
     * @return true if the user is a member of the channel, false otherwise
     */
    public boolean isMember(Long channelId, Long userId) {
        if (conversationParticipantRepository.existsByConversationIdAndUserId(channelId, userId)) {
            return true;
        }

        Channel channel = channelRepository.findByIdElseThrow(channelId);
        return channel.getIsCourseWide();
    }

    /**
     * Checks if a user is a member of a channel
     *
     * @param channel     the channel
     * @param participant optional participant for the user
     * @return true if the user is a member of the channel, false otherwise
     */
    public boolean isMember(Channel channel, Optional<ConversationParticipantSettingsView> participant) {
        return channel.getIsCourseWide() || participant.isPresent();
    }

    /**
     * Checks if a user is a moderator of a channel
     *
     * @param channelId the id of the channel
     * @param userId    the id of the user
     * @return true if the user is a moderator of the channel, false otherwise
     */
    public boolean isChannelModerator(Long channelId, Long userId) {
        return conversationParticipantRepository.findModeratorConversationParticipantByConversationIdAndUserId(channelId, userId).isPresent();
    }

    /**
     * Checks if a user has channel moderation rights to a channel
     * <p>
     * Note: Either the user is a course instructor or a channel moderator to have moderation rights
     *
     * @param channelId the id of the channel
     * @param user      the user to check
     * @return true if the user has moderation rights, false otherwise
     */
    public boolean hasChannelModerationRights(@NotNull Long channelId, @NotNull User user) {
        var userToCheck = getUserIfNecessary(user);
        var channel = channelRepository.findById(channelId);
        return isChannelModerator(channelId, userToCheck.getId()) || authorizationCheckService.isAtLeastInstructorInCourse(channel.orElseThrow().getCourse(), userToCheck);
    }

    /**
     * Checks if a user has channel moderation rights to a channel
     * <p>
     * Note: Either the user is a course instructor or a channel moderator to have moderation rights
     *
     * @param channel     the channel
     * @param user        the user
     * @param participant optional participant for the user
     * @return true if the user has moderation rights, false otherwise
     */
    public boolean hasChannelModerationRights(@NotNull Channel channel, @NotNull User user, Optional<ConversationParticipantSettingsView> participant) {
        return participant.map(ConversationParticipantSettingsView::isModerator).orElse(false) || authorizationCheckService.isAtLeastInstructorInCourse(channel.getCourse(), user);
    }

    /**
     * Checks if a user is allowed to register users to a channel or throws an exception if not
     *
     * @param channel    the channel the users should be registered to
     * @param userLogins the logins of the users that should be registered
     * @param user       the user that wants to register the users
     */
    public void isAllowedToRegisterUsersToChannel(@NotNull Channel channel, @Nullable List<String> userLogins, @NotNull User user) {
        var userLoginsToCheck = Objects.requireNonNullElse(userLogins, new ArrayList<>());
        var userToCheck = getUserIfNecessary(user);
        var isJoinRequest = userLoginsToCheck.size() == 1 && userLoginsToCheck.getFirst().equals(userToCheck.getLogin());
        var channelFromDb = channelRepository.findById(channel.getId());
        var isAtLeastInstructor = authorizationCheckService.isAtLeastInstructorInCourse(channelFromDb.orElseThrow().getCourse(), userToCheck);
        var isChannelModerator = isChannelModerator(channel.getId(), userToCheck.getId());

        var isPrivateChannel = Boolean.FALSE.equals(channel.getIsPublic());
        if (isJoinRequest) {
            if (isPrivateChannel && !isAtLeastInstructor) {
                throw new AccessForbiddenException("You are not allowed to join this channel");
            }
        }
        else {
            if (!(isAtLeastInstructor || isChannelModerator)) {
                throw new AccessForbiddenException("You are not allowed to register users to this channel");
            }
        }
    }

    /**
     * Checks if a user is allowed to grant the channel moderator role to a user or throws an exception if not
     *
     * @param channel the channel
     * @param user    the user that wants to grant the channel moderator role
     */
    public void isAllowedToGrantChannelModeratorRole(@NotNull Channel channel, @NotNull User user) {
        var userToCheck = getUserIfNecessary(user);
        if (!hasChannelModerationRights(channel.getId(), userToCheck)) {
            throw new AccessForbiddenException("You are not allowed to grant channel moderator role");
        }
    }

    /**
     * Checks if a user is allowed to revoke channel moderator role from a user or throws an exception if not
     *
     * @param channel the channel the rights should be revoked from
     * @param user    the user that wants to revoke the channel moderator role
     */
    public void isAllowedToRevokeChannelModeratorRole(@NotNull Channel channel, @NotNull User user) {
        var userToCheck = getUserIfNecessary(user);
        if (!hasChannelModerationRights(channel.getId(), userToCheck)) {
            throw new AccessForbiddenException("You are not allowed to revoke the channel moderator role");
        }
    }

    /**
     * Checks if a user is allowed to remove a user from a channel or throws an exception if not
     *
     * @param channel    the channel the user should be removed from
     * @param userLogins the logins of the users that should be removed
     * @param user       the user that wants to remove the users
     */
    public void isAllowedToDeregisterUsersFromChannel(@NotNull Channel channel, @Nullable List<String> userLogins, @NotNull User user) {
        var userLoginsToCheck = Objects.requireNonNullElse(userLogins, new ArrayList<>());
        var userToCheck = getUserIfNecessary(user);
        if (hasChannelModerationRights(channel.getId(), userToCheck)) {
            return;
        }
        var isChannelMember = isMember(channel.getId(), userToCheck.getId());
        if (!isChannelMember) {
            throw new AccessForbiddenException("User is not a member of the channel");
        }
        var isSelfDeRegistration = userLoginsToCheck.size() == 1 && userLoginsToCheck.getFirst().equals(userToCheck.getLogin());
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
    public void isAllowedToArchiveChannel(@NotNull Channel channel, @NotNull User user) {
        isAllowedToChangeArchivalStatus(channel, user);
    }

    /**
     * Checks if a user is allowed to unarchive a channel or throws an exception if not
     *
     * @param channel the channel that should be unarchived
     * @param user    the user that wants to unarchive the channel
     */
    public void isAllowedToUnArchiveChannel(@NotNull Channel channel, @NotNull User user) {
        isAllowedToChangeArchivalStatus(channel, user);
    }

    private void isAllowedToChangeArchivalStatus(@NotNull Channel channel, @NotNull User user) {
        var userToCheck = getUserIfNecessary(user);
        if (!hasChannelModerationRights(channel.getId(), userToCheck)) {
            throw new AccessForbiddenException("You are not allowed to archive/unarchive this channel");
        }
    }
}
