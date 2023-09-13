package de.tum.in.www1.artemis.exception;

/**
 * This exception is thrown when a networking error occurs while communicating with external services.
 */
public class NetworkingException extends Exception {

    public NetworkingException(String message) {
        super(message);
    }

    public NetworkingException(String message, Throwable cause) {
        super(message, cause);
    }
}
