package de.tum.in.www1.artemis.service.connectors.gitlab;

import de.tum.in.www1.artemis.exception.VersionControlException;

public class GitLabException extends VersionControlException {

    public GitLabException() {
    }

    public GitLabException(String message) {
        super(message);
    }

    public GitLabException(Throwable cause) {
        super(cause);
    }

    public GitLabException(String message, Throwable cause) {
        super(message, cause);
    }
}
