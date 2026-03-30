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

    static <X> ResponseEntity<X> wrapOrNotFound(Optional<X> maybeResponse, HttpHeaders header) {
        return maybeResponse.map(response -> ResponseEntity.ok().headers(header).body(response)).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
