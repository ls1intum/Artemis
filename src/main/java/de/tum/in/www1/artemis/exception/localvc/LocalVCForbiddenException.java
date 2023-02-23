package de.tum.in.www1.artemis.exception.localvc;

/**
 * Exception thrown when the user is authorized but not allowed to fetch or push to a local VC repository.
 * Corresponds to HTTP status code 403.
 */
public class LocalVCForbiddenException extends LocalVCException {

    public LocalVCForbiddenException() {
        super();
    }

    public LocalVCForbiddenException(Throwable cause) {
        super(cause);
    }
}
