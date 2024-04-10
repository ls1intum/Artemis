package de.tum.in.www1.artemis.web.rest.open;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import java.time.Instant;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.in.www1.artemis.security.annotations.EnforceNothing;
import de.tum.in.www1.artemis.versioning.IgnoreGlobalMapping;

@Profile(PROFILE_CORE)
@RestController
@RequestMapping("public/")
public class PublicTimeResource {

    /**
     * {@code GET /time}:
     *
     * @return the current server time as Instant
     */
    @IgnoreGlobalMapping
    @GetMapping("time")
    @EnforceNothing
    public ResponseEntity<Instant> time() {
        return ResponseEntity.ok(Instant.now());
    }
}
