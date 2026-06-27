package de.tum.cit.aet.artemis.communication.service.conversation.auth;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import jakarta.persistence.Persistence;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.communication.repository.ConversationParticipantRepository;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;

@Profile(PROFILE_CORE)
@Lazy
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
     * Returns a {@link User} object with authorities loaded.
     * Course roles are no longer needed here: all per-course authorization checks in
     * {@link de.tum.cit.aet.artemis.core.service.AuthorizationCheckService} issue direct DB point-lookups
     * against the user_course_role table instead of inspecting the in-memory collection.
     *
     * @param user the {@link User} object to ensure authorities are loaded for
     * @return the {@link User} object, potentially after loading the authorities
     * @throws IllegalArgumentException if the user parameter is null
     */
    protected User getUserIfNecessary(User user) {
        if (user == null) {
            throw new IllegalArgumentException("User must not be null");
        }
        var userToCheck = user;
        var persistenceUtil = Persistence.getPersistenceUtil();
        if (!persistenceUtil.isLoaded(userToCheck, "authorities") || userToCheck.getAuthorities() == null) {
            userToCheck = userRepository.getUserWithAuthorities(user.getLogin());
        }
        return userToCheck;
    }

}
