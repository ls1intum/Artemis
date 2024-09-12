package de.tum.cit.aet.artemis.communication.service.conversation.auth;

import static de.tum.cit.aet.artemis.communication.ConversationSettings.MAX_GROUP_CHATS_PER_USER_PER_COURSE;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import jakarta.validation.constraints.NotNull;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.communication.domain.conversation.GroupChat;
import de.tum.cit.aet.artemis.communication.repository.ConversationParticipantRepository;
import de.tum.cit.aet.artemis.communication.repository.conversation.GroupChatRepository;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;

@Profile(PROFILE_CORE)
@Service
public class GroupChatAuthorizationService extends ConversationAuthorizationService {

    private final GroupChatRepository groupChatRepository;

    public GroupChatAuthorizationService(ConversationParticipantRepository conversationParticipantRepository, UserRepository userRepository,
            AuthorizationCheckService authorizationCheckService, GroupChatRepository groupChatRepository) {
        super(conversationParticipantRepository, userRepository, authorizationCheckService);
        this.groupChatRepository = groupChatRepository;
    }

    /**
     * Checks if a user is a member of a group chat
     *
     * @param groupChatId the id of the group chat
     * @param userId      the id of the user
     * @return true if the user is a member of the group chat, false otherwise
     */
    public boolean isMember(Long groupChatId, Long userId) {
        return conversationParticipantRepository.findConversationParticipantByConversationIdAndUserId(groupChatId, userId).isPresent();
    }

    /**
     * Checks if a user is allowed to create a group chat in a course or throws an exception if not
     *
     * @param course the course the group chat should be created in
     * @param user   the user that wants to create the group chat
     */
    public void isAllowedToCreateGroupChat(@NotNull Course course, @NotNull User user) {
        var userToCheck = getUserIfNecessary(user);
        var createdGroupChats = groupChatRepository.countByCreatorIdAndCourseId(userToCheck.getId(), course.getId());
        if (createdGroupChats >= MAX_GROUP_CHATS_PER_USER_PER_COURSE) {
            throw new IllegalArgumentException("You can only create " + MAX_GROUP_CHATS_PER_USER_PER_COURSE + " group chats per course");
        }
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, userToCheck);
    }

    /**
     * Checks if a user is allowed to add a user to a group chat or throws an exception if not
     *
     * @param groupChat the group chat the user should be added to
     * @param user      the user that wants to add the other user
     */
    public void isAllowedToAddUsersToGroupChat(@NotNull GroupChat groupChat, @NotNull User user) {
        var userToCheck = getUserIfNecessary(user);
        if (!isMember(groupChat.getId(), userToCheck.getId())) {
            throw new AccessForbiddenException("You are not a member of this group chat");
        }
    }

    /**
     * Checks if a user is allowed to edit a group chat or throws an exception if not
     *
     * @param groupChat the group chat that should be edited
     * @param user      the user that wants to edit the group chatNotNull
     */
    public void isAllowedToUpdateGroupChat(@NotNull GroupChat groupChat, @NotNull User user) {
        var userToCheck = getUserIfNecessary(user);
        if (!isMember(groupChat.getId(), userToCheck.getId())) {
            throw new AccessForbiddenException("You are not a member of this group chat");
        }
    }

    /**
     * Checks if a user is allowed to remove a user from a group chat or throws an exception if not
     *
     * @param groupChat the group chat the user should be removed from
     * @param user      the user that wants to remove the other user
     */
    public void isAllowedToRemoveUsersFromGroupChat(@NotNull GroupChat groupChat, @NotNull User user) {
        var userToCheck = getUserIfNecessary(user);
        if (!isMember(groupChat.getId(), userToCheck.getId())) {
            throw new AccessForbiddenException("You are not a member of this group chat");
        }
    }

}
