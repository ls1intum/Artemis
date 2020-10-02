package de.tum.in.www1.artemis.exception;

public class ContinousIntegrationException extends RuntimeException {

    public ContinousIntegrationException() {
        // intentionally empty
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
