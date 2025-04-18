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

    // TODO find a better fix without changing the behaviour of this service

    @Override
    public UserDetails loadUserByUsername(final String loginOrEmail) {
        log.info("Authenticating {}", loginOrEmail);
        log.info("length of string {}", loginOrEmail.length());
        long printableCharCount = loginOrEmail.chars().filter(ch -> !Character.isISOControl(ch) && !Character.isWhitespace(ch)).count();
        log.info("Number of printable characters: {}", printableCharCount);
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

        log.debug("User found: login={}, email={}, activated={}, grantedAuthorities={}", user.getLogin(), user.getEmail(), user.getActivated(), user.getGrantedAuthorities());
        // if (!user.isInternal()) {
        // throw new UsernameNotFoundException("User " + lowercaseLoginOrEmail + " is an external user and thus was not found as an internal user.");
        // }
        return createSpringSecurityUser(lowercaseLoginOrEmail, user);
    }

    private org.springframework.security.core.userdetails.User createSpringSecurityUser(String lowercaseLogin, User user) {
        log.info("Checking if user is activated: lowercaseLogin={}, login={}, activated={}", lowercaseLogin, user.getLogin(), user.getActivated());

        if (!user.getActivated()) {
            throw new UserNotActivatedException("User " + lowercaseLogin + " was not activated");
        }
        log.info("user is activated, trying to create spring security user");

        String password = user.getPassword();
        if (password == null || password.isEmpty()) {
            log.warn("User {} has a null or empty password, using a default placeholder password", user.getLogin());
            password = ""; // Provide a default value or handle this case as needed
        }

        return new org.springframework.security.core.userdetails.User(user.getLogin(), password, user.getGrantedAuthorities());
    }
}
