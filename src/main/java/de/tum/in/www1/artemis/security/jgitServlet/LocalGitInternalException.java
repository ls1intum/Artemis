package de.tum.in.www1.artemis.security.jgitServlet;

import de.tum.in.www1.artemis.exception.LocalGitException;

public class LocalGitInternalException extends LocalGitException {

    public LocalGitInternalException(String message) {
        super(message);
    }
}
