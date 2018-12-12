package de.tum.in.www1.artemis.web.rest.errors;

public class CaptchaRequiredException extends RuntimeException {

    public CaptchaRequiredException(String msg) {
        super(msg);
    }

    public CaptchaRequiredException(String msg, Throwable t) {
        super(msg, t);
    }
}
