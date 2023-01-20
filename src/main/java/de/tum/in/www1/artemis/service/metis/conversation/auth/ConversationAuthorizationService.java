package de.tum.in.www1.artemis.service.metis.conversation.auth;

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

    /**
     * Returns a {@link User} object with authorities and groups loaded.
     *
     * @param user the {@link User} object to check for loaded authorities and groups
     * @return the {@link User} object, potentially after loading the authorities and groups
     * @throws IllegalArgumentException if the user parameter is null
     */
    protected User getUserIfNecessary(User user) {
        if (user == null) {
            throw new IllegalArgumentException("User must not be null");
        }
        var userToCheck = user;
        var persistenceUtil = Persistence.getPersistenceUtil();
        if (!persistenceUtil.isLoaded(userToCheck, "authorities") || !persistenceUtil.isLoaded(userToCheck, "groups") || userToCheck.getGroups() == null
                || userToCheck.getAuthorities() == null) {
            userToCheck = userRepository.getUserWithGroupsAndAuthorities(user.getLogin());
        }
        return userToCheck;
    }

}
