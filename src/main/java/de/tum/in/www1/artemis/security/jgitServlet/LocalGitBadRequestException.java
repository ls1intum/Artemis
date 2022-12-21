package de.tum.in.www1.artemis.security.jgitServlet;

import de.tum.in.www1.artemis.exception.LocalGitException;

public class LocalGitBadRequestException extends LocalGitException {
    public LocalGitBadRequestException(String message) {
        super(message);
    }

    public LocalGitBadRequestException(String message, Throwable cause) {
        super(message, cause);
    }
}
