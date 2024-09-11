package de.tum.cit.aet.artemis.web.rest.errors;

import java.io.Serial;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Generic unchecked exception for access forbidden (i.e. 403) errors.
 */
@ResponseStatus(HttpStatus.FORBIDDEN)
public class AccessForbiddenException extends RuntimeException {

    public static final String NOT_ALLOWED = "You are not allowed to access this resource";

    @Serial
    private static final long serialVersionUID = 1L;

    public AccessForbiddenException() {
        super(NOT_ALLOWED);
    }

    public AccessForbiddenException(String message) {
        super(message);
    }

    public AccessForbiddenException(Throwable cause) {
        super(NOT_ALLOWED, cause);
    }

    public AccessForbiddenException(String entityType, long entityId) {
        super("You are not allowed to access the " + entityType + " with id " + entityId);
    }
}
