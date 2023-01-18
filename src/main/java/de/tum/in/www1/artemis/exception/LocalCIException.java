package de.tum.in.www1.artemis.exception;

public class LocalCIException extends ContinuousIntegrationException {

    public LocalCIException() {
    }

    public LocalCIException(String message) {
        super(message);
    }

    public LocalCIException(Throwable cause) {
        super(cause);
    }

    public LocalCIException(String message, Throwable cause) {
        super(message, cause);
    }

}
