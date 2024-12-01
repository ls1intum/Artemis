package de.tum.cit.aet.artemis.core.exception.localvc;

import de.tum.cit.aet.artemis.core.exception.VersionControlException;

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
