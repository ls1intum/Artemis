package de.tum.cit.aet.artemis.programming.service.gitlab;

// Gitlab support will be removed in 8.0.0. Please migrate to LocalVC using e.g. the PR https://github.com/ls1intum/Artemis/pull/8972
@Deprecated(since = "7.5.0", forRemoval = true)

public class GitLabUserDoesNotExistException extends GitLabException {

    public GitLabUserDoesNotExistException(String login) {
        super("User can not be found: " + login);
    }
}
