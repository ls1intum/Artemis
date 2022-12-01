package de.tum.in.www1.artemis.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception that is thrown when the API version range of an annotation is not valid in a specific context.
 */
@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
public class ApiVersionRangeNotValidException extends RuntimeException {

    @Override
    public String toString() {
        return super.toString() + "\n The version range must contain one or two items. None or more were found.";
    }
}
