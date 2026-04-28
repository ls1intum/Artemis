package de.tum.cit.aet.artemis.core.security.passkey;

import java.io.Serial;

import org.springframework.security.core.AuthenticationException;

/**
 * This exception is thrown when a user tries to authenticate with a passkey that is not registered in Artemis.
 */
public class NoPasskeyFoundException extends AuthenticationException {

    @Serial
    private static final long serialVersionUID = 1L;

    public NoPasskeyFoundException(String message) {
        super(message);
    }
}
