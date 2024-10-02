package de.tum.cit.aet.artemis.core.security;

import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.service.user.PasswordService;
import de.tum.cit.aet.artemis.core.service.user.UserCreationService;

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
