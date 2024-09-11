package de.tum.cit.aet.artemis.web.rest.open;

import static de.tum.cit.aet.artemis.config.Constants.PROFILE_CORE;

import java.time.Instant;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.security.annotations.EnforceNothing;

@Profile(PROFILE_CORE)
@RestController
@RequestMapping("api/public/")
public class PublicTimeResource {

    /**
     * {@code GET /time}:
     *
     * @return the current server time as Instant
     */
    @GetMapping("time")
    @EnforceNothing
    public ResponseEntity<Instant> time() {
        return ResponseEntity.ok(Instant.now());
    }
}
