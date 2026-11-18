package de.tum.cit.aet.artemis.core.aspects.util;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.security.annotations.Internal;

@Profile(PROFILE_CORE)
@Lazy
@RestController
@RequestMapping("api/core/internal/")
public class InternalResource {

    @GetMapping("test")
    @Internal
    public ResponseEntity<Void> internalTest() {
        return ResponseEntity.ok().build();
    }
}
