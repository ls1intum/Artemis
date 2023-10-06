package de.tum.in.www1.artemis.exception;

import org.springframework.security.authentication.AuthenticationServiceException;

public class LtiEmailAlreadyInUseException extends AuthenticationServiceException {

    public LtiEmailAlreadyInUseException() {
        super("Email address is already in use by Artemis. Please login again to access Artemis content.");
    }
}
