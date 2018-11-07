package de.tum.in.www1.artemis.security;

import de.tum.in.www1.artemis.domain.User;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;

import java.util.Optional;

public interface ArtemisAuthenticationProvider extends AuthenticationProvider {

    public User getOrCreateUser(Authentication authentication, Boolean skipPasswordCheck);
    public void addUserToGroup(String username, String group);
    public Optional<String> getUsernameForEmail(String email);
    public Boolean checkIfGroupExists(String group);
}
