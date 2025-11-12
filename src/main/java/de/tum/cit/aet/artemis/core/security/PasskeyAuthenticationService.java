package de.tum.cit.aet.artemis.core.security;

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
import de.tum.cit.aet.artemis.core.security.jwt.AuthenticationMethod;
import de.tum.cit.aet.artemis.core.security.jwt.JwtWithSource;
import de.tum.cit.aet.artemis.core.security.jwt.TokenProvider;

/**
 * Service for checking passkey authentication requirements.
 * Used primarily for security expressions in annotations like @EnforceAdmin.
 */
@Profile(PROFILE_CORE)
@Lazy
@Service("passkeyAuthenticationService")
public class PasskeyAuthenticationService {

    private static final Logger log = LoggerFactory.getLogger(PasskeyAuthenticationService.class);

    private final TokenProvider tokenProvider;

    @Value("${" + Constants.PASSKEY_ENABLED_PROPERTY_NAME + ":false}")
    private boolean passkeyEnabled;

    public PasskeyAuthenticationService(TokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    /**
     * @see #isAuthenticatedWithPasskey(boolean)
     */
    public boolean isAuthenticatedWithPasskey() {
        return isAuthenticatedWithPasskey(false);
    }

    /**
     * Checks if the current request is authenticated with a passkey.
     * This method extracts the JWT from the current HTTP request and verifies
     * that it was created using passkey authentication.
     *
     * @param requireSuperAdminApproval if true, additionally checks that the passkey is super admin approved
     * @return true if the user is authenticated with a passkey (and super admin approved if required), false otherwise
     */
    public boolean isAuthenticatedWithPasskey(boolean requireSuperAdminApproval) {
        if (!passkeyEnabled) {
            log.debug("Cannot enforce passkey login when passkey feature is disabled, skipping passkey check");
            return true;
        }

        HttpServletRequest request = getCurrentHttpRequest();
        if (request == null) {
            log.debug("Passkey authentication check failed: no HTTP request in context");
            return false;
        }

        JwtWithSource jwtWithSource = extractValidJwt(request, tokenProvider);
        if (jwtWithSource == null) {
            log.debug("Passkey authentication check failed: no valid JWT found");
            return false;
        }

        AuthenticationMethod method = tokenProvider.getAuthenticationMethod(jwtWithSource.jwt());
        boolean isPasskey = Objects.equals(method, AuthenticationMethod.PASSKEY);

        if (!isPasskey) {
            log.debug("Passkey authentication check failed: authentication method is {} instead of PASSKEY", method);
            return false;
        }

        if (requireSuperAdminApproval) {
            boolean isSuperAdminApproved = tokenProvider.isPasskeySuperAdminApproved(jwtWithSource.jwt());
            if (!isSuperAdminApproved) {
                log.debug("Passkey authentication check failed: passkey is not super admin approved");
                return false;
            }
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
