package de.tum.cit.aet.artemis.exception;

/**
 * Exception thrown when something goes wrong with the local CI system.
 * This is an unchecked exception and should only be used, if the error is not recoverable.
 */
public class LocalCIException extends ContinuousIntegrationException {

    public LocalCIException(String message) {
        super(message);
    }

    public LocalCIException(String message, Throwable cause) {
        super(message, cause);
    }
}
