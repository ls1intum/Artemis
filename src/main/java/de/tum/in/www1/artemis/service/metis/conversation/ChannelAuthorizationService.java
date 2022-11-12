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
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;

@Service
public class ChannelAuthorizationService {

    private final ConversationParticipantRepository conversationParticipantRepository;

    private final AuthorizationCheckService authorizationCheckService;

    private final UserRepository userRepository;

    public ChannelAuthorizationService(ConversationParticipantRepository conversationParticipantRepository, UserRepository userRepository,
            AuthorizationCheckService authorizationCheckService) {
        this.conversationParticipantRepository = conversationParticipantRepository;
        this.authorizationCheckService = authorizationCheckService;
        this.userRepository = userRepository;

    }

    public void isAllowedToCreateChannel(@NotNull Course course, @Nullable User user) {
        user = getUserIfNecessary(user);
        // ToDo: Discuss who else should be allowed to create channels
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, user);
    }

    public void isAllowedToUpdateChannel(@NotNull Channel channel, @Nullable User user) {
        user = getUserIfNecessary(user);
        var isAtLeastInstructor = authorizationCheckService.isAtLeastInstructorInCourse(channel.getCourse(), user);
        var isCreator = channel.getCreator().equals(user);
        if (!isAtLeastInstructor && !isCreator) {
            throw new AccessForbiddenException("You are not allowed to update this channel");
        }
    }

    public boolean isMember(Long channelId, Long userId) {
        return conversationParticipantRepository.findConversationParticipantByConversationIdAndUserId(channelId, userId).isPresent();
    }

    public void isAllowedToRegisterUsersToChannel(@NotNull Course course, @NotNull Channel channel, List<String> userLogins, @Nullable User user) {
        user = getUserIfNecessary(user);
        var isSelfRegistrationRequest = userLogins.size() == 1 && userLogins.get(0).equals(user.getLogin());
        // PUBLIC -> Self Registration or Instructor Registration
        if (Boolean.TRUE.equals(channel.getIsPublic())) {
            if (!isSelfRegistrationRequest) {
                var isChannelMember = isMember(channel.getId(), user.getId());
                if (!isChannelMember) {
                    throw new AccessForbiddenException("User is not a member of the channel");
                }
                authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, user);
            }
        }
        else { // PRIVATE -> Only Instructor Registration
            authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, user);
        }
    }

    public void isAllowedToDeregisterUsersFromChannel(@NotNull Course course, @NotNull Channel channel, List<String> userLogins, @Nullable User user) {
        user = getUserIfNecessary(user);

        var isChannelMember = isMember(channel.getId(), user.getId());
        if (!isChannelMember) {
            throw new AccessForbiddenException("User is not a member of the channel");
        }

        var isSelfDeRegistration = userLogins.size() == 1 && userLogins.get(0).equals(user.getLogin());
        if (isSelfDeRegistration) {
            authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, user);
        }
        else {
            authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, user);
        }
    }

    public void isAllowedToArchiveChannel(@NotNull Channel channel, @Nullable User user) {
        isAllowedToChangeArchivalStatus(channel, user);
    }

    public void isAllowedToUnArchiveChannel(@NotNull Channel channel, @Nullable User user) {
        isAllowedToChangeArchivalStatus(channel, user);
    }

    private void isAllowedToChangeArchivalStatus(Channel channel, @org.jetbrains.annotations.Nullable User user) {
        user = getUserIfNecessary(user);

        var isChannelMember = isMember(channel.getId(), user.getId());
        if (!isChannelMember) {
            throw new AccessForbiddenException("User is not a member of the channel");
        }

        var isCreator = channel.getCreator().equals(user);
        if (!isCreator) {
            authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, channel.getCourse(), user);
        }
    }

    private User getUserIfNecessary(@org.jetbrains.annotations.Nullable User user) {
        var persistenceUtil = Persistence.getPersistenceUtil();
        if (user == null || !persistenceUtil.isLoaded(user, "authorities") || !persistenceUtil.isLoaded(user, "groups") || user.getGroups() == null
                || user.getAuthorities() == null) {
            user = userRepository.getUserWithGroupsAndAuthorities();
        }
        return user;
    }

}
