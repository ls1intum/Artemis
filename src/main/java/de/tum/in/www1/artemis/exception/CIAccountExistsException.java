package de.tum.in.www1.artemis.exception;

public class CIAccountExistsException extends RuntimeException {

    public CIAccountExistsException(String username) {
        super("The account with username " + username + " is already used in the CIS!");
    }

}
