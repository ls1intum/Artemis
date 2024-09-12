package de.tum.cit.aet.artemis.core.exception;

import java.io.Serial;

public class CaptchaRequiredException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public CaptchaRequiredException(String msg) {
        super(msg);
    }
}
