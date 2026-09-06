package de.tum.cit.aet.artemis.core.security.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.security.access.prepost.PreAuthorize;

/**
 * This annotation is used to enforce that the user is an admin authenticated with a super admin approved passkey.
 * It should only be used with endpoints starting with {@code /api/<module-name>/admin/}
 * <p>
 * This annotation requires:
 * <ul>
 * <li>The user must have the {@link de.tum.cit.aet.artemis.core.security.Role#SUPER_ADMIN} role</li>
 * <li>The current account state and super-administrator authority must be valid</li>
 * <li>The user must be authenticated with a passkey (WebAuthn)</li>
 * <li>The passkey must be super admin approved</li>
 * </ul>
 * <p>
 * If passkey authentication is disabled in the configuration, only the passkey requirements are skipped. The
 * SUPER_ADMIN role and current-account validation remain enforced.
 */
@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("hasRole('SUPER_ADMIN') and @userRepository.isSuperAdmin(authentication.name) and @passkeyAuthenticationService.isAuthenticatedWithSuperAdminApprovedPasskey()")
public @interface EnforceSuperAdmin {

}
