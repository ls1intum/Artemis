package de.tum.in.www1.artemis.exception;

/**
 * Exception thrown when something goes wrong with the local CI system.
 */
public class LocalCIException extends ContinuousIntegrationException {

    public LocalCIException(String message) {
        super(message);
    }

    public LocalCIException(String message, Throwable cause) {
        super(message, cause);
    }
}
