package de.tum.in.www1.artemis.service.connectors.gitlab;

public class GitLabUserDoesNotExistException extends GitLabException {

    public GitLabUserDoesNotExistException(String login) {
        super("User can not be found: " + login);
    }
}
