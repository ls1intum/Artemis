package de.tum.in.www1.artemis.security;

import java.util.Optional;

import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.User;

public interface ArtemisAuthenticationProvider extends AuthenticationProvider {

    public User getOrCreateUser(Authentication authentication, Boolean skipPasswordCheck);

    public void addUserToGroup(String username, String group);

    public Optional<String> getUsernameForEmail(String email);

    public Boolean checkIfGroupExists(String group);

    public void registerUserForCourse(User user, Course course);
}
