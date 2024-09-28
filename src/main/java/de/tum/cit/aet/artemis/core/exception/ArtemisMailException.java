package de.tum.cit.aet.artemis.core.exception;

public class ArtemisMailException extends RuntimeException {

    public ArtemisMailException(String message) {
        super(message);
    }

    public ArtemisMailException(String message, Throwable cause) {
        super(message, cause);
    }
}
