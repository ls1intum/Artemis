package de.tum.cit.aet.artemis.core.security.allowedTools;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;

@Profile(PROFILE_CORE)
@RestController
@RequestMapping("api/core/test/")
public class AllowedToolsResource {

    @GetMapping("testAllowedToolTokenScorpio")
    @EnforceAtLeastStudent
    @AllowedTools(ToolTokenType.SCORPIO)
    public ResponseEntity<Void> testAllowedToolTokenScorpio() {
        return ResponseEntity.ok().build();
    }

    @GetMapping("testNoAllowedToolToken")
    @EnforceAtLeastStudent
    public ResponseEntity<Void> testNoAllowedToolToken() {
        return ResponseEntity.ok().build();
    }
}
