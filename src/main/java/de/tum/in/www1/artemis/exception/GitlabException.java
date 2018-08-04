package de.tum.in.www1.artemis.exception;

public class GitlabException extends RuntimeException {

    public GitlabException() {
    }

    public GitlabException(String message) {
        super(message);
    }

    public GitlabException(Throwable cause) {
        super(cause);
    }

    public GitlabException(String message, Throwable cause) {
        super(message, cause);
    }

}
