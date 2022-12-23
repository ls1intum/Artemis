package de.tum.in.www1.artemis.exception;

public class LocalVCException extends VersionControlException {

    public LocalVCException() {
    }

    public LocalVCException(String message) {
        super(message);
    }

    public LocalVCException(String message, Throwable cause) {
        super(message, cause);
    }
}
