package de.tum.cit.aet.artemis.core.service.connectors.campusonline;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when a CAMPUSOnline API call fails.
 * Maps to HTTP 502 Bad Gateway to indicate an upstream service failure.
 */
@ResponseStatus(HttpStatus.BAD_GATEWAY)
public class CampusOnlineApiException extends RuntimeException {

    public CampusOnlineApiException(String message) {
        super(message);
    }

    public CampusOnlineApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
