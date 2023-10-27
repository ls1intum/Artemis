package de.tum.in.www1.artemis.service.connectors.lti.oauth;

import java.io.Serial;

/**
 * An exception thrown by the OAuth library.
 */
public class OAuthException extends Exception {

    public OAuthException(String message) {
        super(message);
    }

    public OAuthException(Throwable cause) {
        super(cause);
    }

    @Serial
    private static final long serialVersionUID = 1L;
}
