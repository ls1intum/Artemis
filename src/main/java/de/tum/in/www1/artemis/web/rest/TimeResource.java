package de.tum.in.www1.artemis.web.rest;

import java.time.Instant;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TimeResource {

    /**
     * {@code GET /time}:
     * @return the current server time as Instant
     */
    @GetMapping(value = "/time", produces = MediaType.TEXT_PLAIN_VALUE)
    public String time() {
        return Instant.now().toString();
    }
}
