package de.tum.cit.aet.artemis.service.connectors.gitlab;

import de.tum.cit.aet.artemis.core.exception.VersionControlException;

// Gitlab support will be removed in 8.0.0. Please migrate to LocalVC using e.g. the PR https://github.com/ls1intum/Artemis/pull/8972
@Deprecated(since = "7.5.0", forRemoval = true)

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
