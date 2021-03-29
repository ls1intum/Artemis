package de.tum.in.www1.artemis.exception;

public class ArtemisAuthenticationException extends RuntimeException {

    public ArtemisAuthenticationException(String message) {
        super(message);
    }

    public ArtemisAuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }
}
