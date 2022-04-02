package de.tum.in.www1.artemis.web.rest.errors;

import java.io.Serial;

import org.zalando.problem.AbstractThrowableProblem;
import org.zalando.problem.Status;

public class PasswordViolatesRequirementsException extends AbstractThrowableProblem {

    @Serial
    private static final long serialVersionUID = 1L;

    public PasswordViolatesRequirementsException() {
        super(ErrorConstants.INVALID_PASSWORD_TYPE, "Incorrect password", Status.BAD_REQUEST);
    }
}
