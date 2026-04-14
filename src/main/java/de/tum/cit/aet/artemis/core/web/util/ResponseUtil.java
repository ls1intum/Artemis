package de.tum.cit.aet.artemis.core.web.util;

import java.util.Optional;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

/**
 * Inlined replacement for {@code tech.jhipster.web.util.ResponseUtil}.
 * <p>
 * Utility interface for wrapping Optional results into ResponseEntity instances,
 * throwing 404 when the value is absent.
 */
public interface ResponseUtil {

    static <X> ResponseEntity<X> wrapOrNotFound(Optional<X> maybeResponse) {
        return wrapOrNotFound(maybeResponse, null);
    }

    /**
     * Wraps an Optional result into a ResponseEntity, returning 404 if empty.
     *
     * @param <X>           the response body type
     * @param maybeResponse the optional result
     * @param header        optional HTTP headers to include (may be null)
     * @return a ResponseEntity with status 200 and the body, or throws 404
     */
    static <X> ResponseEntity<X> wrapOrNotFound(Optional<X> maybeResponse, HttpHeaders header) {
        return maybeResponse.map(response -> {
            var builder = ResponseEntity.ok();
            if (header != null) {
                builder.headers(header);
            }
            return builder.body(response);
        }).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
