package de.tum.in.www1.artemis.service.metis.conversation.auth;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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
        var isArchivedChannel = !Objects.isNull(channel.getIsArchived()) && channel.getIsArchived();
        var userToCheck = getUserIfNecessary(user);
        var isChannelMember = isMember(channel.getId(), userToCheck.getId());
        if (isArchivedChannel) {
            throw new AccessForbiddenException("You are not allowed to create a new answer post in an archived channel.");
        }
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
        var isAnnouncementChannel = !Objects.isNull(channel.getIsAnnouncementChannel()) && channel.getIsAnnouncementChannel();
        var isArchivedChannel = !Objects.isNull(channel.getIsArchived()) && channel.getIsArchived();
        var userToCheck = getUserIfNecessary(user);

        if (isArchivedChannel) {
            throw new AccessForbiddenException("You are not allowed to create a new post in an archived channel.");
        }

        if (isAnnouncementChannel) {
            if (!hasChannelAdminRights(channel.getId(), userToCheck)) {
                throw new AccessForbiddenException("You are not allowed to post in this channel");
            }
        }
        else {
            var isChannelMember = isMember(channel.getId(), userToCheck.getId());
            if (!isChannelMember) {
                throw new AccessForbiddenException("User is not a member of the channel");
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
        if (!hasChannelAdminRights(channel.getId(), userToCheck)) {
            throw new AccessForbiddenException("You are not allowed to update this channel");
        }
    }

    /**
     * Checks if a user is allowed to delete a channel or throws an exception if not
     *
     * @param channel the channel that should be deleted
     * @param user    the user that wants to delete the channel
     */
    public void isAllowedToDeleteChannel(@NotNull Channel channel, @NotNull User user) {
        var userToCheck = getUserIfNecessary(user);
        // either instructor or admin who is also the creator
        var channelFromDb = channelRepository.findById(channel.getId()).orElseThrow();
        var isInstructor = authorizationCheckService.isAtLeastInstructorInCourse(channel.getCourse(), userToCheck);
        var isAdmin = isChannelAdmin(channelFromDb.getId(), userToCheck.getId());
        var isCreator = false;
        if (channelFromDb.getCreator() != null) {
            isCreator = channelFromDb.getCreator().equals(userToCheck);
        }
        if (!(isInstructor || (isAdmin && isCreator))) {
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
        return conversationParticipantRepository.findConversationParticipantByConversationIdAndUserId(channelId, userId).isPresent();
    }

    /**
     * Checks if a user is a admin of a channel
     *
     * @param channelId the id of the channel
     * @param userId    the id of the user
     * @return true if the user is an admin of the channel, false otherwise
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
    public boolean hasChannelAdminRights(@NotNull Long channelId, @NotNull User user) {
        var userToCheck = getUserIfNecessary(user);
        var channel = channelRepository.findById(channelId);
        return isChannelAdmin(channelId, userToCheck.getId()) || authorizationCheckService.isAtLeastInstructorInCourse(channel.get().getCourse(), userToCheck);
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
        var isJoinRequest = userLoginsToCheck.size() == 1 && userLoginsToCheck.get(0).equals(userToCheck.getLogin());
        var channelFromDb = channelRepository.findById(channel.getId());
        var isAtLeastInstructor = authorizationCheckService.isAtLeastInstructorInCourse(channelFromDb.get().getCourse(), userToCheck);
        var isChannelAdmin = isChannelAdmin(channel.getId(), userToCheck.getId());

        var isPrivateChannel = Boolean.FALSE.equals(channel.getIsPublic());
        if (isJoinRequest) {
            if (isPrivateChannel && !isAtLeastInstructor) {
                throw new AccessForbiddenException("You are not allowed to join this channel");
            }
        }
        else {
            if (!(isAtLeastInstructor || isChannelAdmin)) {
                throw new AccessForbiddenException("You are not allowed to register users to this channel");
            }
        }
    }

    /**
     * Checks if a user is allowed to grant channel admin rights to a user or throws an exception if not
     *
     * @param channel the channel the rights should be granted to
     * @param user    the user that wants to grant the channel admin rights
     */
    public void isAllowedToGrantChannelAdmin(@NotNull Channel channel, @NotNull User user) {
        var userToCheck = getUserIfNecessary(user);
        if (!hasChannelAdminRights(channel.getId(), userToCheck)) {
            throw new AccessForbiddenException("You are not allowed to grant channel admin rights");
        }
    }

    /**
     * Checks if a user is allowed to revoke channel admin rights from a user or throws an exception if not
     *
     * @param channel the channel the rights should be revoked from
     * @param user    the user that wants to revoke the channel admin rights
     */
    public void isAllowedToRevokeChannelAdmin(@NotNull Channel channel, @NotNull User user) {
        var userToCheck = getUserIfNecessary(user);
        if (!hasChannelAdminRights(channel.getId(), userToCheck)) {
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
    public void isAllowedToDeregisterUsersFromChannel(@NotNull Channel channel, @Nullable List<String> userLogins, @NotNull User user) {
        var userLoginsToCheck = Objects.requireNonNullElse(userLogins, new ArrayList<>());
        var userToCheck = getUserIfNecessary(user);
        if (hasChannelAdminRights(channel.getId(), userToCheck)) {
            return;
        }
        var isChannelMember = isMember(channel.getId(), userToCheck.getId());
        if (!isChannelMember) {
            throw new AccessForbiddenException("User is not a member of the channel");
        }
        var isSelfDeRegistration = userLoginsToCheck.size() == 1 && userLoginsToCheck.get(0).equals(userToCheck.getLogin());
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
        if (!hasChannelAdminRights(channel.getId(), userToCheck)) {
            throw new AccessForbiddenException("You are not allowed to archive/unarchive this channel");
        }
    }

}
