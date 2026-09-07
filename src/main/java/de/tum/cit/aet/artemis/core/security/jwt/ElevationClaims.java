package de.tum.cit.aet.artemis.core.security.jwt;

import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import de.tum.cit.aet.artemis.core.security.Role;

/**
 * Answers the request-bound half of administrator elevation from the authentication itself.
 *
 * <p>
 * {@link TokenProvider#getAuthentication} attaches the passkey claims it has already verified to the authentication's
 * details, so both questions here are field reads: no token is parsed a second time, and no collaborator is needed.
 * That is what lets the authorization services agree on one definition without any of them reaching for
 * {@code PasskeyAuthenticationService}, which stays reserved for the explicit administrator annotations because only
 * they need the failure reason rather than a boolean.
 *
 * <p>
 * The authentication is left exactly as the token described it. An earlier design removed the administrator
 * authorities in {@link JWTFilter} instead, which made the security context disagree with the token and forced the
 * filter to recognise administrator endpoints so their annotation could still produce the structured passkey error.
 * Asking the question where the decision is made needs neither.
 */
public final class ElevationClaims {

    private static final Set<String> ADMINISTRATOR_AUTHORITIES = Set.of(Role.ADMIN.getAuthority(), Role.SUPER_ADMIN.getAuthority());

    private ElevationClaims() {
    }

    /**
     * Whether the session may exercise the global administrator override, as far as the session alone can tell.
     * <p>
     * Callers pair this with the persisted account status, which is what rejects a token that outlived the
     * administrator role it was issued for.
     *
     * @param authentication                            the authentication the request or WebSocket session carries
     * @param isPasskeyRequiredForAdministratorFeatures whether administrator access requires an approved passkey
     * @return whether the session carries an administrator authority and satisfies the configured passkey requirement
     */
    public static boolean isRequestElevated(@Nullable Authentication authentication, boolean isPasskeyRequiredForAdministratorFeatures) {
        if (authentication == null || !hasAdministratorAuthority(authentication)) {
            return false;
        }
        return !isPasskeyRequiredForAdministratorFeatures || isAuthenticatedWithApprovedPasskey(authentication);
    }

    /**
     * @param authentication the authentication to inspect
     * @return whether it carries the administrator or super-administrator authority, without applying the role hierarchy
     */
    public static boolean hasAdministratorAuthority(@Nullable Authentication authentication) {
        if (authentication == null) {
            return false;
        }
        Set<String> administratorAuthorities = Set.of(Role.ADMIN.getAuthority(), Role.SUPER_ADMIN.getAuthority());
        return authentication.getAuthorities() != null && authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).anyMatch(administratorAuthorities::contains);
    }

    /**
     * An authentication that did not come from a token carries no claims, so a background thread standing in as the
     * system never satisfies this.
     */
    private static boolean isAuthenticatedWithApprovedPasskey(Authentication authentication) {
        return authentication.getDetails() instanceof Map<?, ?> details && Boolean.TRUE.equals(details.get(TokenProvider.IS_AUTHENTICATED_WITH_PASSKEY))
                && Boolean.TRUE.equals(details.get(TokenProvider.IS_PASSKEY_SUPER_ADMIN_APPROVED));
    }
}
