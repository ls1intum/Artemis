package de.tum.in.www1.artemis.exception;

public class VCSAccountExistsException extends RuntimeException {

    public VCSAccountExistsException() {
        super("The account is already used in the VCS!");
    }
}
