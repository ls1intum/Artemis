package de.tum.in.www1.artemis.exception;

/**
 * This exception is thrown when a networking error occurs while communicating with external services.
 */
public class NetworkingError extends Exception {

    public NetworkingError(String message) {
        super(message);
    }

    public NetworkingError(String message, Throwable cause) {
        super(message, cause);
    }
}
