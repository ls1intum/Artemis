package de.tum.cit.aet.artemis.core.exception;

public class FilePathParsingException extends IllegalArgumentException {

    public FilePathParsingException() {
    }

    public FilePathParsingException(String message) {
        super(message);
    }

    public FilePathParsingException(String message, Throwable cause) {
        super(message, cause);
    }
}
