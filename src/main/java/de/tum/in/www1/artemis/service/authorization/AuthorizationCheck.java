package de.tum.in.www1.artemis.service.authorization;

import javax.annotation.Nullable;

import org.hibernate.Hibernate;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.AuthorizationRepository;

/**
 * Base interface for authorization checks.
 * <p>
 * Allows the inheritors to access the required repositories.
 */
public interface AuthorizationCheck {

    /**
     * Get the authorization repository.
     *
     * @return the authorization repository
     */
    AuthorizationRepository getAuthorizationRepository();

    /**
     * Check if the user is loaded properly for authorization checks.
     *
     * @param user the user to check
     * @return true if the user is loaded, false otherwise
     */
    default boolean userIsLoaded(@Nullable User user) {
        return user != null && user.getGroups() != null && Hibernate.isInitialized(user.getGroups());
    }
}
