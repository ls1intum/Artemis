package de.tum.cit.aet.artemis.core.exception;

import java.io.Serial;

import org.springframework.security.access.AccessDeniedException;

/**
 * Exception thrown when passkey authentication requirements are not met.
 * Extends AccessDeniedException to integrate with Spring Security's exception handling.
 */
public class PasskeyAuthenticationException extends AccessDeniedException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final PasskeyAuthenticationFailureReason reason;

    /**
     * Reasons why passkey authentication can fail
     */
    public enum PasskeyAuthenticationFailureReason {

        /**
         * The user is not authenticated with a passkey
         */
        NOT_AUTHENTICATED_WITH_PASSKEY("error.passkeyAuth.notAuthenticatedWithPasskey", "User is not authenticated with a passkey"),

        /**
         * The passkey is not approved by a super admin
         */
        PASSKEY_NOT_SUPER_ADMIN_APPROVED("error.passkeyAuth.notSuperAdminApproved", "Passkey is not approved by a super admin");

        private final String errorKey;

        private final String defaultMessage;

        PasskeyAuthenticationFailureReason(String errorKey, String defaultMessage) {
            this.errorKey = errorKey;
            this.defaultMessage = defaultMessage;
        }

        public String getErrorKey() {
            return errorKey;
        }

        public String getDefaultMessage() {
            return defaultMessage;
        }
    }

    public PasskeyAuthenticationException(PasskeyAuthenticationFailureReason reason) {
        super(reason.getDefaultMessage());
        this.reason = reason;
    }

    public PasskeyAuthenticationFailureReason getReason() {
        return reason;
    }

    public String getErrorKey() {
        return reason.getErrorKey();
    }
}
