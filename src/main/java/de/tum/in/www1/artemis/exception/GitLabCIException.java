package de.tum.in.www1.artemis.exception;

// Gitlab support will be removed in 8.0.0. Please migrate to LocalVC using e.g. the PR https://github.com/ls1intum/Artemis/pull/8972
@Deprecated(since = "7.5.0", forRemoval = true)

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
