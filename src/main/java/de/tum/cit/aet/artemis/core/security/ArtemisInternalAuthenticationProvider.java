package de.tum.cit.aet.artemis.core.security;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Locale;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.service.user.PasswordService;

@Profile(PROFILE_CORE)
@Component
@Lazy
public class ArtemisInternalAuthenticationProvider implements ArtemisAuthenticationProvider {

    private static final Logger log = LoggerFactory.getLogger(ArtemisInternalAuthenticationProvider.class);

    private final PasswordService passwordService;

    private final UserRepository userRepository;

    public ArtemisInternalAuthenticationProvider(PasswordService passwordService, UserRepository userRepository) {
        this.passwordService = passwordService;
        this.userRepository = userRepository;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        log.info("Trying to authenticate user {} with internal authentication provider", authentication.getName());
        String lowercaseLoginOrEmail = authentication.getName().toLowerCase(Locale.ENGLISH);

        Optional<User> optionalUser;
        if (SecurityUtils.isEmail(lowercaseLoginOrEmail)) {
            // It's an email, try to find the user based on the email
            optionalUser = userRepository.findOneWithGroupsAndAuthoritiesByEmailAndInternal(authentication.getName(), true);
        }
        else {
            // It's a login, try to find the user based on the login
            optionalUser = userRepository.findOneWithGroupsAndAuthoritiesByLoginAndInternal(lowercaseLoginOrEmail, true);
        }
        if (optionalUser.isEmpty()) {
            log.warn("User " + lowercaseLoginOrEmail + " was not found in the database");
            // Internal user not found in the database. Skipping internal authentication.
            return null;
        }
        final var user = optionalUser.get();
        if (!user.getActivated()) {
            throw new UserNotActivatedException("User " + user.getLogin() + " was not activated");
        }
        if (!passwordService.checkPasswordMatch(authentication.getCredentials().toString(), user.getPassword())) {
            throw new AuthenticationServiceException("Invalid password for user " + user.getLogin());
        }
        return new UsernamePasswordAuthenticationToken(user.getLogin(), user.getPassword(), user.getGrantedAuthorities());
    }

    @Override
    public Optional<String> getUsernameForEmail(String email) {
        return userRepository.findOneByEmailIgnoreCase(email).flatMap(user -> Optional.of(user.getLogin()));
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
