package de.tum.cit.aet.artemis.core.exception;

import java.io.Serial;

import org.springframework.security.access.AccessDeniedException;

/**
 * Exception thrown when passkey authentication requirements are not met (e.g. {@link de.tum.cit.aet.artemis.core.security.annotations.EnforceAdmin}.
 * Extends {@link AccessDeniedException} to integrate with Spring Security's exception handling.
 */
public class PasskeyAuthenticationException extends AccessDeniedException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final PasskeyAuthenticationFailureReason reason;

    public enum PasskeyAuthenticationFailureReason {

        /**
         * The user is not authenticated with a passkey
         */
        NOT_AUTHENTICATED_WITH_PASSKEY("error.passkeyAuth.notAuthenticatedWithPasskey"),

        /**
         * The passkey is not approved by a super admin
         */
        PASSKEY_NOT_SUPER_ADMIN_APPROVED("error.passkeyAuth.notSuperAdminApproved"),

        PASSKEY_LOGIN_REQUIRED_BUT_FEATURE_NOT_ENABLED("error.passkeyAuth.loginRequiredButFeatureNotEnabled");

        private final String errorKey;

        PasskeyAuthenticationFailureReason(String errorKey) {
            this.errorKey = errorKey;
        }

        public String getErrorKey() {
            return errorKey;
        }
    }

    public PasskeyAuthenticationException(PasskeyAuthenticationFailureReason reason) {
        super(reason.getErrorKey());
        this.reason = reason;
    }

    public PasskeyAuthenticationFailureReason getReason() {
        return reason;
    }

    public String getErrorKey() {
        return reason.getErrorKey();
    }
}
