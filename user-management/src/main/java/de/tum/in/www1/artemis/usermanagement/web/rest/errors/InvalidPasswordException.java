package de.tum.in.www1.artemis.usermanagement.web.rest.errors;

import de.tum.in.www1.artemis.web.rest.errors.ErrorConstants;
import org.zalando.problem.AbstractThrowableProblem;
import org.zalando.problem.Status;

import java.io.Serial;

public class InvalidPasswordException extends AbstractThrowableProblem {

    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidPasswordException() {
        super(ErrorConstants.INVALID_PASSWORD_TYPE, "Incorrect password", Status.BAD_REQUEST);
    }
}
