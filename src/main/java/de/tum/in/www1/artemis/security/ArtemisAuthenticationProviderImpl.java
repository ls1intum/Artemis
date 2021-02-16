package de.tum.in.www1.artemis.security;

import org.springframework.beans.factory.annotation.Autowired;

import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.service.user.PasswordService;
import de.tum.in.www1.artemis.service.user.UserService;

public abstract class ArtemisAuthenticationProviderImpl implements ArtemisAuthenticationProvider {

    protected UserService userService;

    protected final UserRepository userRepository;

    protected final PasswordService passwordService;

    public ArtemisAuthenticationProviderImpl(UserRepository userRepository, PasswordService passwordService) {
        this.userRepository = userRepository;
        this.passwordService = passwordService;
    }

    @Autowired // break the dependency cycle
    public void setUserService(UserService userService) {
        this.userService = userService;
    }
}
