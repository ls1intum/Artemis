package de.tum.cit.aet.artemis.core.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.lang.CheckReturnValue;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import de.tum.cit.aet.artemis.core.security.jwt.ElevationClaims;

/**
 * Separates administrator account classification from the request-bound capability to exercise administrator access.
 * <p>
 * An administrator without an approved passkey keeps explicitly assigned normal roles, but must not receive the global
 * administrator override. The decision has two halves: the session must prove the configured passkey requirement (see
 * {@link ElevationClaims}), and the account must still be an active administrator. Neither half throws, so list
 * endpoints can fall back to their role-filtered queries. Explicit administrator endpoints use
 * {@code PasskeyAuthenticationService} directly instead, because they need the failure reason to build the detailed
 * passkey error response rather than a boolean.
 */
@Profile(PROFILE_CORE)
@Lazy
@Service("elevatedAccessService")
public class ElevatedAccessService {

    private final UserRepository userRepository;

    private final boolean isPasskeyRequiredForAdministratorFeatures;

    public ElevatedAccessService(UserRepository userRepository,
            @Value("${" + Constants.PASSKEY_REQUIRE_FOR_ADMINISTRATOR_FEATURES_PROPERTY_NAME + ":false}") boolean isPasskeyRequiredForAdministratorFeatures) {
        this.userRepository = userRepository;
        this.isPasskeyRequiredForAdministratorFeatures = isPasskeyRequiredForAdministratorFeatures;
    }

    /**
     * @return whether the persisted, active current account is an administrator
     */
    @CheckReturnValue
    public boolean isCurrentUserAdministrator() {
        return SecurityUtils.getCurrentUserLogin().filter(userRepository::isAdmin).isPresent();
    }

    /**
     * @return whether the persisted, active current account is a super administrator
     */
    @CheckReturnValue
    public boolean isCurrentUserSuperAdministrator() {
        return SecurityUtils.getCurrentUserLogin().filter(userRepository::isSuperAdmin).isPresent();
    }

    /**
     * Returns whether the current request may use the global administrator override. This method is intentionally
     * non-throwing: normal endpoints use it to select either an unrestricted administrator query or the normal
     * role-filtered query.
     *
     * @return true only for an active administrator whose session satisfies the configured passkey requirement
     */
    @CheckReturnValue
    public boolean isAdminElevationActive() {
        return isAdminElevationActive(SecurityContextHolder.getContext().getAuthentication());
    }

    /**
     * The same decision for a caller that has no request-bound {@code SecurityContext}, such as a WebSocket
     * subscription arriving on the message channel.
     *
     * <p>
     * {@link ElevationClaims} answers the session-bound half from the claims the token carries, and the persisted
     * lookup is the account half - it rejects a session whose administrator was deactivated, deleted or demoted
     * afterwards. An administrator who signed in with a password alone satisfies neither.
     *
     * @param authentication the authentication the request or session was established with
     * @return true only for an active administrator whose session satisfies the configured passkey requirement
     */
    @CheckReturnValue
    public boolean isAdminElevationActive(@Nullable Authentication authentication) {
        // Reading the session first preserves the zero-query fast path for every ordinary request.
        if (authentication == null || !ElevationClaims.isRequestElevated(authentication, isPasskeyRequiredForAdministratorFeatures)) {
            return false;
        }
        String login = authentication.getName();
        if (!StringUtils.hasText(login)) {
            // A background thread standing in as the system carries the administrator authority but no identity, so
            // there is no account to confirm and nothing to ask the database.
            return false;
        }
        return userRepository.isAdmin(login);
    }

    /**
     * Method-security gate for endpoints whose required role is not tied to a resource identifier. The explicit authorities are inspected without Spring's role hierarchy so an
     * administrator authority alone cannot satisfy a teaching role. Resource-specific annotations use their authorization aspects instead.
     *
     * @param requiredRole required global role
     * @return whether the caller has an explicitly assigned sufficient normal role or active administrator elevation
     */
    @CheckReturnValue
    public boolean hasAtLeastRoleOrAdminAccess(Role requiredRole) {
        return switch (requiredRole) {
            case SUPER_ADMIN -> isAdminElevationActive() && isCurrentUserSuperAdministrator();
            case ADMIN -> isAdminElevationActive();
            case INSTRUCTOR -> SecurityUtils.hasCurrentUserAnyOfAuthorities(Role.INSTRUCTOR.getAuthority()) || isAdminElevationActive();
            case EDITOR -> SecurityUtils.hasCurrentUserAnyOfAuthorities(Role.INSTRUCTOR.getAuthority(), Role.EDITOR.getAuthority()) || isAdminElevationActive();
            case TEACHING_ASSISTANT ->
                SecurityUtils.hasCurrentUserAnyOfAuthorities(Role.INSTRUCTOR.getAuthority(), Role.EDITOR.getAuthority(), Role.TEACHING_ASSISTANT.getAuthority())
                        || isAdminElevationActive();
            case STUDENT -> SecurityUtils.isAuthenticated();
            case ANONYMOUS -> true;
        };
    }
}
