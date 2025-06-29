package de.tum.cit.aet.artemis.core.security;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.service.user.PasswordService;

@Profile(PROFILE_CORE)
@Component
@Lazy
@ConditionalOnProperty(value = "artemis.user-management.use-external", havingValue = "false")
public class ArtemisInternalAuthenticationProvider implements ArtemisAuthenticationProvider {

    private final PasswordService passwordService;

    private final UserRepository userRepository;

    public ArtemisInternalAuthenticationProvider(PasswordService passwordService, UserRepository userRepository) {
        this.passwordService = passwordService;
        this.userRepository = userRepository;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        final var user = userRepository.findOneWithGroupsAndAuthoritiesByLogin(authentication.getName());
        if (user.isEmpty()) {
            throw new AuthenticationServiceException(String.format("User %s does not exist in the Artemis database!", authentication.getName()));
        }
        if (!user.get().getActivated()) {
            throw new UserNotActivatedException("User " + user.get().getLogin() + " was not activated");
        }
        if (!passwordService.checkPasswordMatch(authentication.getCredentials().toString(), user.get().getPassword())) {
            throw new AuthenticationServiceException("Invalid password for user " + user.get().getLogin());
        }
        return new UsernamePasswordAuthenticationToken(user.get().getLogin(), user.get().getPassword(), user.get().getGrantedAuthorities());
    }

    @Override
    public Optional<String> getUsernameForEmail(String email) {
        return userRepository.findOneByEmailIgnoreCase(email).flatMap(user -> Optional.of(user.getLogin()));
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return true;
    }
}
