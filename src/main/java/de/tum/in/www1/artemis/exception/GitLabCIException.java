package de.tum.in.www1.artemis.exception;

public class GitLabCIException extends ContinuousIntegrationException {

    public GitLabCIException(String message) {
        super(message);
    }

    public GitLabCIException(Throwable cause) {
        super(cause);
    }

    public GitLabCIException(String message, Throwable cause) {
        super(message, cause);
    }
}
