package de.tum.in.www1.artemis.security;

import org.springframework.beans.factory.annotation.Autowired;

import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.service.UserRetrievalService;
import de.tum.in.www1.artemis.service.UserService;

public abstract class ArtemisAuthenticationProviderImpl implements ArtemisAuthenticationProvider {

    protected UserService userService;

    protected final UserRetrievalService userRetrievalService;

    protected final UserRepository userRepository;

    public ArtemisAuthenticationProviderImpl(UserRepository userRepository, UserRetrievalService userRetrievalService) {
        this.userRepository = userRepository;
        this.userRetrievalService = userRetrievalService;
    }

    @Autowired
    // break the dependency cycle
    public void setUserService(UserService userService) {
        this.userService = userService;
    }
}
