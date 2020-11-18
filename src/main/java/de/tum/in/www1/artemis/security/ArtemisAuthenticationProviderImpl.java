package de.tum.in.www1.artemis.security;

import org.springframework.beans.factory.annotation.Autowired;

import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.service.UserService;

public abstract class ArtemisAuthenticationProviderImpl implements ArtemisAuthenticationProvider {

    protected final UserRepository userRepository;

    protected UserService userService;

    public ArtemisAuthenticationProviderImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Autowired
    // break the dependency cycle
    public void setUserService(UserService userService) {
        this.userService = userService;
    }
}
