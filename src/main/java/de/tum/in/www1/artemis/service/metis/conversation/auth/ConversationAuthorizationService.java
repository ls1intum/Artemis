package de.tum.in.www1.artemis.service.metis.conversation.auth;

import javax.annotation.Nullable;
import javax.persistence.Persistence;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.repository.metis.ConversationParticipantRepository;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;

@Service
public class ConversationAuthorizationService {

    protected final ConversationParticipantRepository conversationParticipantRepository;

    protected final UserRepository userRepository;

    protected final AuthorizationCheckService authorizationCheckService;

    protected ConversationAuthorizationService(ConversationParticipantRepository conversationParticipantRepository, UserRepository userRepository,
            AuthorizationCheckService authorizationCheckService) {
        this.conversationParticipantRepository = conversationParticipantRepository;
        this.userRepository = userRepository;
        this.authorizationCheckService = authorizationCheckService;
    }

    protected User getUserIfNecessary(@Nullable User user) {
        var userToCheck = user;
        var persistenceUtil = Persistence.getPersistenceUtil();
        if (userToCheck == null || !persistenceUtil.isLoaded(userToCheck, "authorities") || !persistenceUtil.isLoaded(userToCheck, "groups") || userToCheck.getGroups() == null
                || userToCheck.getAuthorities() == null) {
            userToCheck = userRepository.getUserWithGroupsAndAuthorities();
        }
        return userToCheck;
    }

}
