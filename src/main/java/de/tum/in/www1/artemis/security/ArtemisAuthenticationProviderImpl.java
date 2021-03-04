package de.tum.in.www1.artemis.security;

import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.service.user.PasswordService;
import de.tum.in.www1.artemis.service.user.UserCreationService;

public abstract class ArtemisAuthenticationProviderImpl implements ArtemisAuthenticationProvider {

    protected final UserCreationService userCreationService;

    protected final UserRepository userRepository;

    protected final PasswordService passwordService;

    public ArtemisAuthenticationProviderImpl(UserRepository userRepository, PasswordService passwordService, UserCreationService userCreationService) {
        this.userRepository = userRepository;
        this.passwordService = passwordService;
        this.userCreationService = userCreationService;
    }
}
