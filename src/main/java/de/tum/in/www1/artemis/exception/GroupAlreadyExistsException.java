package de.tum.in.www1.artemis.exception;

public class GroupAlreadyExistsException extends RuntimeException {

    public GroupAlreadyExistsException() {
    }

    public GroupAlreadyExistsException(String message) {
        super(message);
    }

    public GroupAlreadyExistsException(Throwable cause) {
        super(cause);
    }

    public GroupAlreadyExistsException(String message, Throwable cause) {
        super(message, cause);
    }

}
