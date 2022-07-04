package de.tum.in.www1.artemis.exception;

public class QuizJoinException extends Throwable {

    private String error;

    public QuizJoinException(String error, String message) {
        super(message);
        this.error = error;
    }

    public String getError() {
        return error;
    }
}
