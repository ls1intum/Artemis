package de.tum.cit.aet.artemis.core.exception;

public class ContinuousIntegrationException extends RuntimeException {

    public ContinuousIntegrationException() {
        // intentionally empty
    }

    public ContinuousIntegrationException(String message) {
        super(message);
    }

    public ContinuousIntegrationException(Throwable cause) {
        super(cause);
    }

    public ContinuousIntegrationException(String message, Throwable cause) {
        super(message, cause);
    }

}
