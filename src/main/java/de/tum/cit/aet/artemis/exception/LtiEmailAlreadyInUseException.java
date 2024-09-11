package de.tum.cit.aet.artemis.exception;

import org.springframework.security.authentication.InternalAuthenticationServiceException;

/**
 * Exception thrown when an email provided during LTI authentication is already in use within Artemis.
 * This is an unchecked exception and indicates that the user should re-login to access Artemis content.
 */
public class LtiEmailAlreadyInUseException extends InternalAuthenticationServiceException {

    /**
     * Constructs a new LtiEmailAlreadyInUseException with a default message.
     */
    public LtiEmailAlreadyInUseException() {
        super("Email address is already in use by Artemis. Please login again to access Artemis content.");
    }
}
