package de.tum.cit.aet.artemis.core.exception.localvc;

/**
 * Exception thrown when the user is not authenticated or authorized to fetch or push to a local VC repository.
 * Corresponds to HTTP status code 401.
 */
public class LocalVCAuthException extends LocalVCOperationException {

    public LocalVCAuthException() {
        // empty constructor
    }

    public LocalVCAuthException(Throwable cause) {
        super(cause);
    }

    public LocalVCAuthException(String message) {
        super(message);
    }

}
