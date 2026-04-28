package de.tum.cit.aet.artemis.core.exception;

import java.io.Serial;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

public class PasswordViolatesRequirementsException extends ErrorResponseException {

    @Serial
    private static final long serialVersionUID = 1L;

    public PasswordViolatesRequirementsException() {
        super(HttpStatus.BAD_REQUEST, asProblemDetail(), null);
    }

    private static ProblemDetail asProblemDetail() {
        ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        detail.setType(ErrorConstants.INVALID_PASSWORD_TYPE);
        detail.setTitle("error.incorrectPassword");
        return detail;
    }
}
