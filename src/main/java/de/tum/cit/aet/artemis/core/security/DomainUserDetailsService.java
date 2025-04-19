package de.tum.cit.aet.artemis.core.security;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.repository.UserRepository;

/**
 * Authenticate a user from the database.
 */
@Profile(PROFILE_CORE)
@Service("userDetailsService")
public class DomainUserDetailsService implements UserDetailsService {

    private static final Logger log = LoggerFactory.getLogger(DomainUserDetailsService.class);

    private final UserRepository userRepository;

    public DomainUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(final String loginOrEmail) {
        String lowercaseLoginOrEmail = loginOrEmail.toLowerCase(Locale.ENGLISH);

        User user;
        if (SecurityUtils.isEmail(lowercaseLoginOrEmail)) {
            // It's an email, try to find the user based on the email
            user = userRepository.findOneWithGroupsAndAuthoritiesByEmail(lowercaseLoginOrEmail)
                    .orElseThrow(() -> new UsernameNotFoundException("User " + lowercaseLoginOrEmail + " was not found by email in the database"));
        }
        else {
            // It's a login, try to find the user based on the login
            user = userRepository.findOneWithGroupsAndAuthoritiesByLogin(lowercaseLoginOrEmail)
                    .orElseThrow(() -> new UsernameNotFoundException("User " + lowercaseLoginOrEmail + " was not found by login in the database"));
        }

        return createSpringSecurityUser(lowercaseLoginOrEmail, user);
    }

    private org.springframework.security.core.userdetails.User createSpringSecurityUser(String lowercaseLogin, User user) {
        log.info("Checking if user is activated: lowercaseLogin={}, login={}, activated={}", lowercaseLogin, user.getLogin(), user.getActivated());

        if (!user.getActivated()) {
            throw new UserNotActivatedException("User " + lowercaseLogin + " was not activated");
        }

        String password = user.getPassword();
        boolean isExternalUserWithoutPassword = password == null && !user.isInternal();
        boolean usedTemporaryPasswordForCreation = false;
        if (isExternalUserWithoutPassword) {
            // SpringSecurity user cannot be created with a null password, so we need a temporary placeholder password
            // The temporary password should be erased after the user is created, to ensure that the user cannot log in with it (TODO does this assumption hold?)
            password = PasswordGenerator.generateTemporaryPassword(); // could be set to an empty string as well, but setting a temporary value might be less error-prone
            usedTemporaryPasswordForCreation = true;
        }

        org.springframework.security.core.userdetails.User springUser = new org.springframework.security.core.userdetails.User(user.getLogin(), password,
                user.getGrantedAuthorities());

        if (usedTemporaryPasswordForCreation) {
            springUser.eraseCredentials(); // TODO verify which effects this has (assumption: no login with password possible on that user object)
        }

        return springUser;
    }
}
