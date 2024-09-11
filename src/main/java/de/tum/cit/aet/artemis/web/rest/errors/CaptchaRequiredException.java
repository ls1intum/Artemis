package de.tum.cit.aet.artemis.web.rest.errors;

import java.io.Serial;

public class CaptchaRequiredException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public CaptchaRequiredException(String msg) {
        super(msg);
    }
}
