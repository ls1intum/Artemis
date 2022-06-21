package de.tum.in.www1.artemis.exception;

public class ArtemisMailException extends RuntimeException {

    public ArtemisMailException(String message) {
        super(message);
    }

    public ArtemisMailException(String message, Throwable cause) {
        super(message, cause);
    }
}
