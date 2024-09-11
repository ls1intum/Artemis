package de.tum.cit.aet.artemis.exception;

public class ArtemisMailException extends RuntimeException {

    public ArtemisMailException(String message) {
        super(message);
    }

    public ArtemisMailException(String message, Throwable cause) {
        super(message, cause);
    }
}
