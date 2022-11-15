package de.tum.in.www1.artemis.service.metis.conversation.auth;

import static de.tum.in.www1.artemis.domain.metis.conversation.ConversationSettings.MAX_GROUP_CHATS_PER_USER_PER_COURSE;
import static de.tum.in.www1.artemis.domain.metis.conversation.ConversationSettings.MAX_ONE_TO_ONE_CHATS_PER_USER_PER_COURSE;

import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.repository.metis.ConversationParticipantRepository;
import de.tum.in.www1.artemis.repository.metis.conversation.OneToOneChatRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;

@Service
public class OneToOneChatAuthorizationService extends ConversationAuthorizationService {

    private final OneToOneChatRepository oneToOneChatRepository;

    public OneToOneChatAuthorizationService(ConversationParticipantRepository conversationParticipantRepository, UserRepository userRepository,
            AuthorizationCheckService authorizationCheckService, OneToOneChatRepository oneToOneChatRepository) {
        super(conversationParticipantRepository, userRepository, authorizationCheckService);
        this.oneToOneChatRepository = oneToOneChatRepository;
    }

    public void isAllowedToCreateOneToOneChat(@NotNull Course course, @Nullable User user) {
        user = getUserIfNecessary(user);
        var createdOneToOneChats = oneToOneChatRepository.countByCreatorIdAndCourseId(user.getId(), course.getId());
        if (createdOneToOneChats >= MAX_ONE_TO_ONE_CHATS_PER_USER_PER_COURSE) {
            throw new IllegalArgumentException("You can only create " + MAX_GROUP_CHATS_PER_USER_PER_COURSE + "group chats per course");
        }
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, user);
    }

}
