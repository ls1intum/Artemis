package de.tum.cit.aet.artemis.core.exception;

import java.io.Serial;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * Simple exception with a message, that returns an Internal Server Error code.
 */
public class InternalServerErrorException extends ErrorResponseException {

    @Serial
    private static final long serialVersionUID = 1L;

    public InternalServerErrorException(String message) {
        super(HttpStatus.INTERNAL_SERVER_ERROR, asProblemDetail(message), null);
    }

    private static ProblemDetail asProblemDetail(String message) {
        ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        detail.setType(ErrorConstants.DEFAULT_TYPE);
        detail.setTitle(message);
        return detail;
    }
}
