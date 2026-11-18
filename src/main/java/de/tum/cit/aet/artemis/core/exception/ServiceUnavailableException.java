package de.tum.cit.aet.artemis.core.exception;

import java.io.Serial;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Generic unchecked exception for service unavailable (i.e. 503) errors.
 */
@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
public class ServiceUnavailableException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ServiceUnavailableException(String message) {
        super(message);
    }
}
