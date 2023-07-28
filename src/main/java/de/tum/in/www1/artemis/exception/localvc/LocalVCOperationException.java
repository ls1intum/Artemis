package de.tum.in.www1.artemis.exception.localvc;

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
}
