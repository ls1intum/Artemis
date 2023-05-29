package de.tum.in.www1.artemis.exception.localvc;

import de.tum.in.www1.artemis.exception.VersionControlException;

/**
 * Exception thrown when an internal error occurs in the local version control system.
 * Corresponds to HTTP status code 500.
 */
public class LocalVCInternalException extends VersionControlException {

    public LocalVCInternalException(String message) {
        super(message);
    }

    public LocalVCInternalException(String message, Exception e) {
        super(message, e);
    }
}
