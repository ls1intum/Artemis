package de.tum.in.www1.artemis.web.rest.errors;

import java.io.Serial;

import org.zalando.problem.AbstractThrowableProblem;
import org.zalando.problem.Status;

@Deprecated // Moved to user management microservice. To be removed
public class InvalidPasswordException extends AbstractThrowableProblem {

    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidPasswordException() {
        super(ErrorConstants.INVALID_PASSWORD_TYPE, "Incorrect password", Status.BAD_REQUEST);
    }
}
