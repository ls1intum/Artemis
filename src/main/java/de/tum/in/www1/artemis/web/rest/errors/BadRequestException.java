package de.tum.in.www1.artemis.web.rest.errors;

import java.io.Serial;

import org.zalando.problem.AbstractThrowableProblem;
import org.zalando.problem.Status;

/**
 * Simple exception with a message, that returns a Bad Request code.
 */
public class BadRequestException extends AbstractThrowableProblem {

    @Serial
    private static final long serialVersionUID = 1L;

    public BadRequestException(String message) {
        super(ErrorConstants.DEFAULT_TYPE, message, Status.BAD_REQUEST);
    }
}
