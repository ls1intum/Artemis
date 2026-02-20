package de.tum.cit.aet.artemis.core.service.connectors.campusonline;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when a CAMPUSOnline API call fails.
 * Maps to HTTP 502 Bad Gateway to indicate an upstream service failure.
 */
@ResponseStatus(HttpStatus.BAD_GATEWAY)
public class CampusOnlineApiException extends RuntimeException {

    /**
     * Creates a new exception with the specified error message.
     *
     * @param message a description of the API failure
     */
    public CampusOnlineApiException(String message) {
        super(message);
    }

    /**
     * Creates a new exception with the specified error message and cause.
     *
     * @param message a description of the API failure
     * @param cause   the underlying exception (e.g. RestClientException)
     */
    public CampusOnlineApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
