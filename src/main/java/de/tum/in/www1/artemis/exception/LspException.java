package de.tum.in.www1.artemis.exception;

public class LspException extends RuntimeException {

    public LspException() {
    }

    public LspException(String message) {
        super(message);
    }

    public LspException(Throwable cause) {
        super(cause);
    }

    public LspException(String message, Throwable cause) {
        super(message, cause);
    }
}
