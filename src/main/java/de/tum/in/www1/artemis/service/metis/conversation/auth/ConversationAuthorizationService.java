package de.tum.in.www1.artemis.service.metis.conversation.auth;

import javax.annotation.Nullable;
import javax.persistence.Persistence;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;

@Service
public abstract class ConversationAuthorizationService {

    protected final UserRepository userRepository;

    protected final AuthorizationCheckService authorizationCheckService;

    protected ConversationAuthorizationService(UserRepository userRepository, AuthorizationCheckService authorizationCheckService) {
        this.userRepository = userRepository;
        this.authorizationCheckService = authorizationCheckService;
    }

    protected User getUserIfNecessary(@Nullable User user) {
        var persistenceUtil = Persistence.getPersistenceUtil();
        if (user == null || !persistenceUtil.isLoaded(user, "authorities") || !persistenceUtil.isLoaded(user, "groups") || user.getGroups() == null
                || user.getAuthorities() == null) {
            user = userRepository.getUserWithGroupsAndAuthorities();
        }
        return user;
    }

}
