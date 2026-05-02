package de.tum.cit.aet.artemis.core.exception;

import java.nio.file.Path;

/**
 * Signals that creating a Git repository failed because the target repository already exists.
 */
public class RepositoryAlreadyExistsException extends GitException {

    public RepositoryAlreadyExistsException(Path repositoryPath, Throwable cause) {
        super("Repository already exists: " + repositoryPath, cause);
    }
}
