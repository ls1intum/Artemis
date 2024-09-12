package de.tum.cit.aet.artemis.core.exception.localvc;

/**
 * Generic exception for all local version control purposes.
 */
public class LocalVCOperationException extends Exception {

    public LocalVCOperationException() {
        // empty constructor
    }

    public LocalVCOperationException(Throwable cause) {
        super(cause);
    }

    public LocalVCOperationException(String message) {
        super(message);
    }
}
