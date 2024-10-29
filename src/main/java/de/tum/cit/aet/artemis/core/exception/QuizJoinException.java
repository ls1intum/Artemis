package de.tum.cit.aet.artemis.core.exception;

public class QuizJoinException extends Throwable {

    private final String error;

    public QuizJoinException(String error, String message) {
        super(message);
        this.error = error;
    }

    public String getError() {
        return error;
    }
}
