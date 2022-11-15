package de.tum.in.www1.artemis.service.metis.conversation.auth;

import static de.tum.in.www1.artemis.domain.metis.conversation.ConversationSettings.MAX_GROUP_CHATS_PER_USER_PER_COURSE;

import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.metis.conversation.GroupChat;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.repository.metis.ConversationParticipantRepository;
import de.tum.in.www1.artemis.repository.metis.conversation.GroupChatRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;

@Service
public class GroupChatAuthorizationService extends ConversationAuthorizationService {

    private final GroupChatRepository groupChatRepository;

    public boolean isMember(Long channelId, Long userId) {
        return conversationParticipantRepository.findConversationParticipantByConversationIdAndUserId(channelId, userId).isPresent();
    }

    public GroupChatAuthorizationService(ConversationParticipantRepository conversationParticipantRepository, UserRepository userRepository,
            AuthorizationCheckService authorizationCheckService, GroupChatRepository groupChatRepository) {
        super(conversationParticipantRepository, userRepository, authorizationCheckService);
        this.groupChatRepository = groupChatRepository;
    }

    public void isAllowedToCreateGroupChat(@NotNull Course course, @Nullable User user) {
        user = getUserIfNecessary(user);
        var createdGroupChats = groupChatRepository.countByCreatorIdAndCourseId(user.getId(), course.getId());
        if (createdGroupChats >= MAX_GROUP_CHATS_PER_USER_PER_COURSE) {
            throw new IllegalArgumentException("You can only create " + MAX_GROUP_CHATS_PER_USER_PER_COURSE + "group chats per course");
        }
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, user);
    }

    public void isAllowedToAddUsersToGroupChat(@NotNull GroupChat groupChat, @Nullable User user) {
        user = getUserIfNecessary(user);
        if (!isMember(groupChat.getId(), user.getId())) {
            throw new AccessForbiddenException("You are not a member of this group chat");
        }
    }

    public void isAllowedToRemoveUsersFromGroupChat(@NotNull GroupChat groupChat, @Nullable User user) {
        user = getUserIfNecessary(user);
        if (!isMember(groupChat.getId(), user.getId())) {
            throw new AccessForbiddenException("You are not a member of this group chat");
        }
    }

}
