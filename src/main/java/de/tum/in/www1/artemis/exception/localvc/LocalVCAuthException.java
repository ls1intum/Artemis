package de.tum.in.www1.artemis.exception.localvc;

/**
 * Exception thrown when the user is not authenticated or authorized to fetch or push to a local VC repository.
 * Corresponds to HTTP status code 401.
 */
public class LocalVCAuthException extends LocalVCException {

    public LocalVCAuthException() {
    }

    public LocalVCAuthException(Throwable cause) {
        super(cause);
    }
}
