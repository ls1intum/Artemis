package de.tum.cit.aet.artemis.core.exception;

/*
 * TODO our exception handling is awful in some places. An example with this exception: I just created this wrapper for now so that we don't have 'throws Exception' after some of
 * our methods in the VersionControlService interface. If we extend from a RuntimeException, this should not be declared in a "throws" statement in the first place since they are
 * by definition
 * unchecked exceptions. But, by declaring "throws Exception" in the interface, we now have lots of methods calling this interface and just passing the generic exceptions by
 * declaring "throws Exception" themselves (Although in the end, we just throw an unchecked RuntimeException).
 */

import de.tum.cit.aet.artemis.programming.service.vcs.VersionControlService;

/**
 * Generic exception thrown if there are any errors while communicating with the {@link VersionControlService VersionControlService}
 */
public class VersionControlException extends RuntimeException {

    public VersionControlException() {
    }

    public VersionControlException(String message) {
        super(message);
    }

    public VersionControlException(Throwable cause) {
        super(cause);
    }

    public VersionControlException(String message, Throwable cause) {
        super(message, cause);
    }
}
