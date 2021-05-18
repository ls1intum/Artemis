package de.tum.in.www1.artemis.exception;

public class CIAccountExistsException extends RuntimeException {

    public CIAccountExistsException() {
        super("The account is already used in the CIS!");
    }

}
