package de.tum.in.www1.exerciseapp.exception;

/**
 * Created by muenchdo on 22/06/16.
 */
public class GitException extends Exception {

    public GitException() {
    }

    public GitException(String message) {
        super(message);
    }

    public GitException(Throwable cause) {
        super(cause);
    }

    public GitException(String message, Throwable cause) {
        super(message, cause);
    }

}
