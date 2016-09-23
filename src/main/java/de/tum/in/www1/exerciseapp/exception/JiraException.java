package de.tum.in.www1.exerciseapp.exception;

/**
 * Created by Josias Montag on 22.09.16.
 */
public class JiraException extends RuntimeException {

    public JiraException() {
    }

    public JiraException(String message) {
        super(message);
    }

    public JiraException(Throwable cause) {
        super(cause);
    }

    public JiraException(String message, Throwable cause) {
        super(message, cause);
    }

}
