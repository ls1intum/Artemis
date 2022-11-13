package de.tum.in.www1.artemis.service.metis.conversation;

import java.util.List;

import javax.annotation.Nullable;
import javax.persistence.Persistence;
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
public class ChannelAuthorizationService {

    private final ConversationParticipantRepository conversationParticipantRepository;

    private final AuthorizationCheckService authorizationCheckService;

    private final ChannelRepository channelRepository;

    private final UserRepository userRepository;

    public ChannelAuthorizationService(ConversationParticipantRepository conversationParticipantRepository, UserRepository userRepository,
            AuthorizationCheckService authorizationCheckService, ChannelRepository channelRepository) {
        this.conversationParticipantRepository = conversationParticipantRepository;
        this.authorizationCheckService = authorizationCheckService;
        this.userRepository = userRepository;
        this.channelRepository = channelRepository;
    }

    public void isAllowedToCreateChannel(@NotNull Course course, @Nullable User user) {
        user = getUserIfNecessary(user);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, user);
    }

    public void isAllowedToUpdateChannel(@NotNull Channel channel, @Nullable User user) {
        user = getUserIfNecessary(user);
        if (!hasChannelAdminRights(channel.getId(), user)) {
            throw new AccessForbiddenException("You are not allowed to update this channel");
        }
    }

    public void isAllowedToDeleteChannel(@NotNull Channel channel, @Nullable User user) {
        user = getUserIfNecessary(user);
        if (!hasChannelAdminRights(channel.getId(), user)) {
            throw new AccessForbiddenException("You are not allowed to update this channel");
        }
    }

    public boolean isMember(Long channelId, Long userId) {
        return conversationParticipantRepository.findConversationParticipantByConversationIdAndUserId(channelId, userId).isPresent();
    }

    public boolean isChannelAdmin(Long channelId, Long userId) {
        return conversationParticipantRepository.findAdminConversationParticipantByConversationIdAndUserId(channelId, userId).isPresent();
    }

    public boolean hasChannelAdminRights(Long channelId, @Nullable User user) {
        user = getUserIfNecessary(user);
        var channel = channelRepository.findById(channelId);
        return isChannelAdmin(channelId, user.getId()) || authorizationCheckService.isAtLeastInstructorInCourse(channel.get().getCourse(), user);
    }

    public void isAllowedToRegisterUsersToChannel(@NotNull Course course, @NotNull Channel channel, List<String> userLogins, @Nullable User user) {
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

    public void isAllowedToDeregisterUsersFromChannel(@NotNull Course course, @NotNull Channel channel, List<String> userLogins, @Nullable User user) {
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

    public void isAllowedToArchiveChannel(@NotNull Channel channel, @Nullable User user) {
        isAllowedToChangeArchivalStatus(channel, user);
    }

    public void isAllowedToUnArchiveChannel(@NotNull Channel channel, @Nullable User user) {
        isAllowedToChangeArchivalStatus(channel, user);
    }

    private void isAllowedToChangeArchivalStatus(Channel channel, @Nullable User user) {
        user = getUserIfNecessary(user);
        if (!hasChannelAdminRights(channel.getId(), user)) {
            throw new AccessForbiddenException("You are not allowed to update this channel");
        }
    }

    private User getUserIfNecessary(@Nullable User user) {
        var persistenceUtil = Persistence.getPersistenceUtil();
        if (user == null || !persistenceUtil.isLoaded(user, "authorities") || !persistenceUtil.isLoaded(user, "groups") || user.getGroups() == null
                || user.getAuthorities() == null) {
            user = userRepository.getUserWithGroupsAndAuthorities();
        }
        return user;
    }

}
