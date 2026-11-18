package de.tum.cit.aet.artemis.core.exception;

public class UsernameAlreadyUsedException extends RuntimeException {

    public UsernameAlreadyUsedException() {
        super("Login name already used!");
    }

}
