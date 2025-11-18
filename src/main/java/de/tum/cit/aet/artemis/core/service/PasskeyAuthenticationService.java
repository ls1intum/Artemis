package de.tum.cit.aet.artemis.core.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.core.security.jwt.JWTFilter.extractValidJwt;

import java.util.Objects;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.exception.PasskeyAuthenticationException;
import de.tum.cit.aet.artemis.core.security.jwt.AuthenticationMethod;
import de.tum.cit.aet.artemis.core.security.jwt.JwtWithSource;
import de.tum.cit.aet.artemis.core.security.jwt.TokenProvider;

/**
 * Service for checking passkey authentication requirements.
 * Used primarily for security expressions in annotations like {@link de.tum.cit.aet.artemis.core.security.annotations.EnforceAdmin}.
 */
@Profile(PROFILE_CORE)
@Lazy
@Service("passkeyAuthenticationService")
public class PasskeyAuthenticationService {

    private static final Logger log = LoggerFactory.getLogger(PasskeyAuthenticationService.class);

    private final TokenProvider tokenProvider;

    private final boolean passkeyEnabled;

    private final boolean isPasskeyRequiredForAdministratorFeatures;

    public PasskeyAuthenticationService(TokenProvider tokenProvider, @Value("${" + Constants.PASSKEY_ENABLED_PROPERTY_NAME + ":false}") boolean passkeyEnabled,
            @Value("${" + Constants.PASSKEY_REQUIRE_FOR_ADMINISTRATOR_FEATURES_PROPERTY_NAME + ":false}") boolean isPasskeyRequiredForAdministratorFeatures) {
        this.tokenProvider = tokenProvider;
        this.passkeyEnabled = passkeyEnabled;
        this.isPasskeyRequiredForAdministratorFeatures = isPasskeyRequiredForAdministratorFeatures;
    }

    /**
     * @return see #isAuthenticatedWithPasskey(boolean)
     */
    public boolean isAuthenticatedWithPasskey() throws PasskeyAuthenticationException {
        return isAuthenticatedWithPasskey(false);
    }

    /**
     * @return see #isAuthenticatedWithPasskey(boolean)
     */
    public boolean isAuthenticatedWithSuperAdminApprovedPasskey() throws PasskeyAuthenticationException {
        return isAuthenticatedWithPasskey(true);
    }

    /**
     * Checks if the current request is authenticated with a passkey.
     * This method extracts the JWT from the current HTTP request and verifies
     * that it was created using passkey authentication.
     *
     * @param requireSuperAdminApproval if true, additionally checks that the passkey is super admin approved
     * @return true if the user is authenticated with a passkey (and super admin approved if required)
     * @throws PasskeyAuthenticationException if passkey authentication requirements are not met
     */
    public boolean isAuthenticatedWithPasskey(boolean requireSuperAdminApproval) throws PasskeyAuthenticationException {
        if (!isPasskeyRequiredForAdministratorFeatures) {
            log.debug("Passkey login is not required for administrator features");
            return true;
        }
        if (!passkeyEnabled) {
            log.warn("Cannot enforce passkey login when passkey feature is disabled");
            return false;
        }

        HttpServletRequest request = getCurrentHttpRequest();
        if (request == null) {
            log.debug("Passkey authentication check failed: no HTTP request in context");
            throw new PasskeyAuthenticationException(PasskeyAuthenticationException.PasskeyAuthenticationFailureReason.NOT_AUTHENTICATED_WITH_PASSKEY);
        }

        JwtWithSource jwtWithSource;
        try {
            jwtWithSource = extractValidJwt(request, tokenProvider);
        }
        catch (Exception exception) {
            log.debug("Passkey authentication check failed: error extracting JWT", exception);
            throw new PasskeyAuthenticationException(PasskeyAuthenticationException.PasskeyAuthenticationFailureReason.NOT_AUTHENTICATED_WITH_PASSKEY);
        }

        if (jwtWithSource == null) {
            log.debug("Passkey authentication check failed: no valid JWT found");
            throw new PasskeyAuthenticationException(PasskeyAuthenticationException.PasskeyAuthenticationFailureReason.NOT_AUTHENTICATED_WITH_PASSKEY);
        }

        AuthenticationMethod method = tokenProvider.getAuthenticationMethod(jwtWithSource.jwt());
        boolean isPasskey = Objects.equals(method, AuthenticationMethod.PASSKEY);

        if (!isPasskey) {
            log.debug("Passkey authentication check failed: authentication method is {} instead of PASSKEY", method);
            throw new PasskeyAuthenticationException(PasskeyAuthenticationException.PasskeyAuthenticationFailureReason.NOT_AUTHENTICATED_WITH_PASSKEY);
        }

        if (!requireSuperAdminApproval) {
            return true;
        }

        boolean isSuperAdminApproved = tokenProvider.isPasskeySuperAdminApproved(jwtWithSource.jwt());
        if (!isSuperAdminApproved) {
            log.debug("Passkey authentication check failed: passkey is not super admin approved");
            throw new PasskeyAuthenticationException(PasskeyAuthenticationException.PasskeyAuthenticationFailureReason.PASSKEY_NOT_SUPER_ADMIN_APPROVED);
        }

        return true;
    }

    /**
     * Gets the current HTTP request from the request context.
     *
     * @return the current HttpServletRequest, or null if not available
     */
    private HttpServletRequest getCurrentHttpRequest() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            return attributes != null ? attributes.getRequest() : null;
        }
        catch (Exception e) {
            log.warn("Failed to get current HTTP request", e);
            return null;
        }
    }
}
