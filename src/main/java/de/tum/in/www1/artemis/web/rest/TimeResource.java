package de.tum.in.www1.artemis.web.rest;

import java.time.Instant;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TimeResource {

    /**
     * {@code GET /time}:
     * @return the current server time as Instant
     */
    @GetMapping("/time")
    public Instant time() {
        return Instant.now();
    }
}
