package de.tum.cit.aet.artemis.core.security;

import static de.tum.cit.aet.artemis.core.config.Constants.PASSWORD_MAX_LENGTH;
import static de.tum.cit.aet.artemis.core.config.Constants.PASSWORD_MIN_LENGTH;
import static de.tum.cit.aet.artemis.core.config.Constants.SIMPLE_EMAIL_REGEX;
import static de.tum.cit.aet.artemis.core.config.Constants.USERNAME_MAX_LENGTH;
import static de.tum.cit.aet.artemis.core.config.Constants.USERNAME_MIN_LENGTH;

import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.util.StringUtils;

import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;

/**
 * Utility class for Spring Security.
 */
public final class SecurityUtils {

    /**
     * Roles in descending order of precedence, for reporting which role a request acted with. This is a ranking, not the
     * role hierarchy: {@code SecurityConfiguration} no longer lets an administrator authority imply a teaching role.
     */
    private static final Role[] ROLES_BY_PRECEDENCE = { Role.SUPER_ADMIN, Role.ADMIN, Role.INSTRUCTOR, Role.EDITOR, Role.TEACHING_ASSISTANT, Role.STUDENT };

    private SecurityUtils() {
    }

    public static boolean isEmail(String input) {
        return input.matches(SIMPLE_EMAIL_REGEX);
    }

    /**
     * check that the username and password are not null and have the correct length
     * <p>
     * The empty/missing case is reported separately from the too-short case on purpose: a credential that is simply absent (e.g. a git client sending a username with no
     * password) is a very different situation from one that violates the length policy, and conflating them produces misleading "password too short" log entries for what are
     * actually empty requests. Length policy is the responsibility of the credential's own provider (e.g. an external LDAP/SSO); this method only guards against null/empty
     * (via {@link StringUtils#hasLength}, so a whitespace-only value still falls through to the length check) and the platform's own min/max bounds. Never include the credential
     * value itself in these messages, as they are logged.
     *
     * @param username the username which should be validated
     * @param password the password which should be validated
     */
    public static void checkUsernameAndPasswordValidity(String username, String password) {
        if (!StringUtils.hasLength(username)) {
            throw new AccessForbiddenException("No username provided");
        }
        else if (username.length() < USERNAME_MIN_LENGTH) {
            throw new AccessForbiddenException("The username has to be at least " + USERNAME_MIN_LENGTH + " characters long");
        }
        else if (username.length() > USERNAME_MAX_LENGTH) {
            throw new AccessForbiddenException("The username has to be less than " + USERNAME_MAX_LENGTH + " characters long");
        }
        if (!StringUtils.hasLength(password)) {
            throw new AccessForbiddenException("No password provided");
        }
        else if (password.length() < PASSWORD_MIN_LENGTH) {
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
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
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
     * behavior.
     */
    public static void setAuthorizationObject() {
        // Only stands in for a missing authentication, never replaces one. The stand-in carries no login, and the
        // context object is shared, so overwriting a real principal loses the identity that auditing
        // (SpringSecurityAuditorAware), UserRepository#getUser and every authorization rule resolving
        // authentication.name depend on for the rest of the request.
        if (isAuthenticated()) {
            return;
        }
        SecurityContext context = SecurityContextHolder.getContext();
        context.setAuthentication(makeAuthorizationObject(null));
    }

    /**
     * Runs background work as the system, regardless of what the current thread happens to hold.
     *
     * <p>
     * For an entry point with no caller to inherit from - a scheduled job, a message listener, startup work - the
     * thread is pooled and nothing clears it between tasks, so whatever the previous task left behind is still there.
     * {@link #setAuthorizationObject()} deliberately keeps an existing principal, which is right for a path that may
     * carry a real user and wrong here: leftover state is not an identity. This clears first so the stand-in is
     * installed deterministically, and restores afterwards so that calling it part way through a call chain does not
     * disturb the caller.
     *
     * @param work the background work to run
     */
    public static void runAsSystem(Runnable work) {
        SecurityContext previousContext = SecurityContextHolder.getContext();
        try {
            setSystemAuthorizationObject();
            work.run();
        }
        finally {
            SecurityContextHolder.setContext(previousContext);
        }
    }

    /**
     * Installs the system principal on the current thread, discarding whatever was there.
     *
     * <p>
     * The unscoped form of {@link #runAsSystem(Runnable)}, for a method that is itself the entry point and so owns the
     * thread for the rest of the task. Prefer {@code runAsSystem} where the work fits in a lambda; this exists because
     * some entry points declare checked exceptions or wrap their body in a try-with-resources, which a {@link Runnable}
     * cannot express.
     *
     * <p>
     * Do not call this from a path that may carry a real user: discarding their principal is exactly the bug
     * {@link #setAuthorizationObject()} was changed to stop causing.
     */
    public static void setSystemAuthorizationObject() {
        SecurityContextHolder.clearContext();
        setAuthorizationObject();
    }

    /**
     * Create an Authentication object to impersonate the specified user
     *
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
                return login;
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

    /**
     * Returns the highest global role of the current user.
     * <p>
     * This is a <i>global</i> authority, not a role within a particular course: a user who instructs any course carries
     * {@code ROLE_INSTRUCTOR} everywhere. Reads only the security context, so it costs no database access.
     * <p>
     * Written as a single pass over the authorities because the feature usage interceptor calls this on every API request.
     * Asking "does the user hold this role" once per role walked the authority collection up to six times and built a stream
     * for each, which is a lot of garbage to produce per request for a counter.
     *
     * @return the highest role held by the current user, or {@link Role#ANONYMOUS} if there is no authenticated user
     */
    public static Role getCurrentUserHighestRole() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return Role.ANONYMOUS;
        }
        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
        if (authorities == null) {
            return Role.ANONYMOUS;
        }
        int highest = ROLES_BY_PRECEDENCE.length;
        for (GrantedAuthority granted : authorities) {
            String authority = granted.getAuthority();
            // only the roles that would outrank what has already been found are still worth comparing
            for (int candidate = 0; candidate < highest; candidate++) {
                if (ROLES_BY_PRECEDENCE[candidate].getAuthority().equals(authority)) {
                    highest = candidate;
                    break;
                }
            }
            if (highest == 0) {
                break;
            }
        }
        return highest == ROLES_BY_PRECEDENCE.length ? Role.ANONYMOUS : ROLES_BY_PRECEDENCE[highest];
    }
}
