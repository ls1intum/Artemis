package de.tum.cit.aet.artemis.security;

import static de.tum.cit.aet.artemis.config.Constants.PROFILE_CORE;

import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.repository.UserRepository;
import de.tum.cit.aet.artemis.service.user.PasswordService;
import de.tum.cit.aet.artemis.service.user.UserCreationService;

@Profile(PROFILE_CORE)
@Component
@ConditionalOnProperty(value = "artemis.user-management.use-external", havingValue = "false")
public class ArtemisInternalAuthenticationProvider extends ArtemisAuthenticationProviderImpl implements ArtemisAuthenticationProvider {

    public ArtemisInternalAuthenticationProvider(UserRepository userRepository, PasswordService passwordService, UserCreationService userCreationService) {
        super(userRepository, passwordService, userCreationService);
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
