package de.tum.in.www1.artemis.security;

import static de.tum.in.www1.artemis.config.Constants.*;

import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.util.StringUtils;

import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;

/**
 * Utility class for Spring Security.
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    /**
     * check that the username and password are not null and have the correct length
     * @param username the username which should be validated
     * @param password the password which should be validated
     */
    public static void checkUsernameAndPasswordValidity(String username, String password) {
        if (!StringUtils.hasLength(username) || username.length() < USERNAME_MIN_LENGTH) {
            throw new AccessForbiddenException("The username has to be at least " + USERNAME_MIN_LENGTH + " characters long");
        }
        else if (username.length() > USERNAME_MAX_LENGTH) {
            throw new AccessForbiddenException("The username has to be less than " + USERNAME_MAX_LENGTH + " characters long");
        }
        if (!StringUtils.hasLength(password) || password.length() < PASSWORD_MIN_LENGTH) {
            throw new AccessForbiddenException("The password has to be at least " + PASSWORD_MIN_LENGTH + " characters long");
        }
        else if (password.length() > PASSWORD_MAX_LENGTH) {
            throw new AccessForbiddenException("The password has to be less than " + PASSWORD_MAX_LENGTH + " characters long");
        }
    }

    /**
     * Get the login of the current user.
     *
     * @return the login of the current user.
     */
    public static Optional<String> getCurrentUserLogin() {
        SecurityContext securityContext = SecurityContextHolder.getContext();
        return Optional.ofNullable(extractPrincipal(securityContext.getAuthentication()));
    }

    private static String extractPrincipal(Authentication authentication) {
        if (authentication == null) {
            return null;
        }
        else if (authentication.getPrincipal() instanceof UserDetails springSecurityUser) {
            return springSecurityUser.getUsername();
        }
        else if (authentication.getPrincipal() instanceof String) {
            return (String) authentication.getPrincipal();
        }
        return null;
    }

    /**
     * If the current user has a specific authority (security role).
     * <p>
     * The name of this method comes from the isUserInRole() method in the Servlet API
     *
     * @param authority the authority to check
     * @return true if the current user has the authority, false otherwise
     */
    public static boolean isCurrentUserInRole(String authority) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && getAuthorities(authentication).anyMatch(authority::equals);
    }

    private static Stream<String> getAuthorities(Authentication authentication) {
        return Optional.ofNullable(authentication.getAuthorities()).orElse(Set.of()).stream().map(GrantedAuthority::getAuthority);
    }

    /**
     * Check if a user is authenticated.
     *
     * @return true if the user is authenticated, false otherwise
     */
    public static boolean isAuthenticated() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && getAuthorities(authentication).noneMatch(Role.ANONYMOUS.getAuthority()::equals);
    }

    /**
     * This methods manually sets a dummy Authentication object that is always authenticated. When a request using a JpaRepository is made and the query associated with the method
     * is not automatically generated but manually specified, the Spring Data JPA expects the user performing the request to be authenticated. If the request to the JpaRepository
     * is made because of a REST-call from a server that is not authenticated within Spring, an InvalidDataAccessApiUsageException is raised. This method is a workaround for this
     * behaviour. See https://jira.spring.io/browse/DATAJPA-1357 for more details.
     */
    public static void setAuthorizationObject() {
        SecurityContext context = SecurityContextHolder.getContext();
        context.setAuthentication(makeAuthorizationObject(null));
    }

    /**
     * Create an Authentication object to impersonate the specified user
     * @param login The login of the user to impersonate
     * @return A new Authentication object
     */
    public static Authentication makeAuthorizationObject(@Nullable String login) {
        return new Authentication() {

            @Override
            public Collection<? extends GrantedAuthority> getAuthorities() {
                return Set.of(new SimpleGrantedAuthority("ROLE_ADMIN"));
            }

            @Override
            public Object getCredentials() {
                return null;
            }

            @Override
            public Object getDetails() {
                return null;
            }

            @Override
            public Object getPrincipal() {
                return login;
            }

            @Override
            public boolean isAuthenticated() {
                return true;
            }

            @Override
            public void setAuthenticated(boolean isAuthenticated) throws IllegalArgumentException {

            }

            @Override
            public String getName() {
                return null;
            }
        };
    }

    /**
     * Checks if the current user has any of the authorities.
     *
     * @param authorities the authorities to check.
     * @return true if the current user has any of the authorities, false otherwise.
     */
    public static boolean hasCurrentUserAnyOfAuthorities(String... authorities) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return (authentication != null && getAuthorities(authentication).anyMatch(authority -> Arrays.asList(authorities).contains(authority)));
    }

    /**
     * Checks if the current user has none of the authorities.
     *
     * @param authorities the authorities to check.
     * @return true if the current user has none of the authorities, false otherwise.
     */
    public static boolean hasCurrentUserNoneOfAuthorities(String... authorities) {
        return !hasCurrentUserAnyOfAuthorities(authorities);
    }

    /**
     * Checks if the current user has a specific authority.
     *
     * @param authority the authority to check.
     * @return true if the current user has the authority, false otherwise.
     */
    public static boolean hasCurrentUserThisAuthority(String authority) {
        return hasCurrentUserAnyOfAuthorities(authority);
    }
}
