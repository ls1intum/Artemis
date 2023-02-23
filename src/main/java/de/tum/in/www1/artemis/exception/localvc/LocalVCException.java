package de.tum.in.www1.artemis.exception.localvc;

import de.tum.in.www1.artemis.exception.VersionControlException;

/**
 * Generic exception for all local version control purposes.
 */
public class LocalVCException extends VersionControlException {

    public LocalVCException() {
    }

    public LocalVCException(Throwable cause) {
        super(cause);
    }

    public LocalVCException(String message) {
        super(message);
    }

    public LocalVCException(String message, Throwable cause) {
        super(message, cause);
    }
}
