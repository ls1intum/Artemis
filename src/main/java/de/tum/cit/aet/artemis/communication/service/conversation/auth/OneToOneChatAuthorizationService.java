package de.tum.cit.aet.artemis.communication.service.conversation.auth;

import static de.tum.cit.aet.artemis.communication.ConversationSettings.MAX_ONE_TO_ONE_CHATS_PER_USER_PER_COURSE;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import jakarta.validation.constraints.NotNull;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.communication.repository.ConversationParticipantRepository;
import de.tum.cit.aet.artemis.communication.repository.conversation.OneToOneChatRepository;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.web.rest.errors.AccessForbiddenException;

@Profile(PROFILE_CORE)
@Service
public class OneToOneChatAuthorizationService extends ConversationAuthorizationService {

    private final OneToOneChatRepository oneToOneChatRepository;

    public OneToOneChatAuthorizationService(ConversationParticipantRepository conversationParticipantRepository, UserRepository userRepository,
            AuthorizationCheckService authorizationCheckService, OneToOneChatRepository oneToOneChatRepository) {
        super(conversationParticipantRepository, userRepository, authorizationCheckService);
        this.oneToOneChatRepository = oneToOneChatRepository;
    }

    /**
     * Checks if a user is allowed to create a one to one chat in a course or throws an exception if not
     *
     * @param course the course the one to one chat should be created in
     * @param user   the user that wants to create the one to one chat
     */
    public void isAllowedToCreateOneToOneChat(@NotNull Course course, @NotNull User user) {
        var userToCheck = getUserIfNecessary(user);
        var createdOneToOneChats = oneToOneChatRepository.countByCreatorIdAndCourseId(userToCheck.getId(), course.getId());
        if (createdOneToOneChats >= MAX_ONE_TO_ONE_CHATS_PER_USER_PER_COURSE) {
            throw new AccessForbiddenException("You can only create " + MAX_ONE_TO_ONE_CHATS_PER_USER_PER_COURSE + " one to one chats per course");
        }
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, userToCheck);
    }

}
