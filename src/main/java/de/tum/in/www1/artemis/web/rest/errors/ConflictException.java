package de.tum.in.www1.artemis.web.rest.errors;

import org.zalando.problem.AbstractThrowableProblem;
import org.zalando.problem.Status;

public class ConflictException extends AbstractThrowableProblem {
    public ConflictException(String defaultMessage, String detail) {
        super(ErrorConstants.DEFAULT_TYPE, defaultMessage, Status.CONFLICT, detail);
    }
}
