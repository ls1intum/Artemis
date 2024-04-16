package de.tum.in.www1.artemis.security;

import java.util.Optional;

import org.springframework.security.authentication.AuthenticationProvider;

public interface ArtemisAuthenticationProvider extends AuthenticationProvider {

    /**
     * Searches for a user with the given email address.
     *
     * @param email The email address to search for
     * @return An Optional containing the username if there is a user with the provided email, an empty Optional otherwise
     */
    Optional<String> getUsernameForEmail(String email);

}
