package de.tum.in.www1.artemis.exception;

/**
 * Created by muenchdo on 22/06/16.
 */
public class ContinousIntegrationException extends RuntimeException {

    public ContinousIntegrationException() {
    }

    public ContinousIntegrationException(String message) {
        super(message);
    }

    public ContinousIntegrationException(Throwable cause) {
        super(cause);
    }

    public ContinousIntegrationException(String message, Throwable cause) {
        super(message, cause);
    }

}
