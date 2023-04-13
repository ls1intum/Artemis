package de.tum.in.www1.artemis.exception.localvc;

/**
 * Exception thrown when an internal error occurs in the local version control system.
 * Corresponds to HTTP status code 500.
 */
public class LocalVCInternalException extends LocalVCException {

    public LocalVCInternalException(String message, Throwable e) {
        super(message, e);
    }
}
