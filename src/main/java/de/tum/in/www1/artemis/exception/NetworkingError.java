package de.tum.in.www1.artemis.exception;

public class NetworkingError extends Exception {

    public NetworkingError(String message) {
        super(message);
    }

    public NetworkingError(String message, Throwable cause) {
        super(message, cause);
    }
}
