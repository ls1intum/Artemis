package de.tum.cit.aet.artemis.core.exception;

public class EmailFailedException extends Exception {

    public EmailFailedException(String message, Exception exception) {
        super(message, exception);
    }
}
