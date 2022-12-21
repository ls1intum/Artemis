package de.tum.in.www1.artemis.security.jgitServlet;

import de.tum.in.www1.artemis.exception.LocalGitException;

public class LocalGitNotFoundException extends LocalGitException {
    public LocalGitNotFoundException(String message) {
        super(message);
    }

    public LocalGitNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
