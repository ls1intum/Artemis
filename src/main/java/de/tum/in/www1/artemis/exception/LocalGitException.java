package de.tum.in.www1.artemis.exception;

public class LocalGitException extends VersionControlException {

    public LocalGitException() {
    }

    public LocalGitException(String message) {
        super(message);
    }

    public LocalGitException(Throwable cause) {
        super(cause);
    }

    public LocalGitException(String message, Throwable cause) {
        super(message, cause);
    }
}
