package de.tum.in.www1.artemis.exception;

public class VCSAccountExistsException extends RuntimeException {

    public VCSAccountExistsException(String username) {
        super("The account with username" + username + " is already used in the VCS!");
    }
}
