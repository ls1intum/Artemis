package de.tum.cit.aet.artemis.security;

import static de.tum.cit.aet.artemis.config.Constants.PROFILE_CORE;

import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.domain.User;
import de.tum.cit.aet.artemis.repository.UserRepository;

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
        log.debug("Authenticating {}", loginOrEmail);
        String lowercaseLoginOrEmail = loginOrEmail.toLowerCase(Locale.ENGLISH);

        User user;
        if (SecurityUtils.isEmail(lowercaseLoginOrEmail)) {
            // It's an email, try to find the user based on the email
            user = userRepository.findOneWithGroupsAndAuthoritiesByEmailAndIsInternal(lowercaseLoginOrEmail, true)
                    .orElseThrow(() -> new UsernameNotFoundException("User " + lowercaseLoginOrEmail + " was not found in the database"));
        }
        else {
            // It's a login, try to find the user based on the login
            user = userRepository.findOneWithGroupsAndAuthoritiesByLoginAndIsInternal(lowercaseLoginOrEmail, true)
                    .orElseThrow(() -> new UsernameNotFoundException("User " + lowercaseLoginOrEmail + " was not found in the database"));
        }

        if (!user.isInternal()) {
            throw new UsernameNotFoundException("User " + lowercaseLoginOrEmail + " is an external user and thus was not found as an internal user.");
        }
        return createSpringSecurityUser(lowercaseLoginOrEmail, user);
    }

    private org.springframework.security.core.userdetails.User createSpringSecurityUser(String lowercaseLogin, User user) {
        if (!user.getActivated()) {
            throw new UserNotActivatedException("User " + lowercaseLogin + " was not activated");
        }
        return new org.springframework.security.core.userdetails.User(user.getLogin(), user.getPassword(), user.getGrantedAuthorities());
    }
}
