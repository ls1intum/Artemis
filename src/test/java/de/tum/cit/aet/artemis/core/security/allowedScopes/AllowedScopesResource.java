package de.tum.cit.aet.artemis.core.security.allowedScopes;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastInstructor;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;

@Profile(PROFILE_CORE)
@Lazy
@RestController
@RequestMapping("api/core/test/")
public class AllowedScopesResource {

    @GetMapping("testScopedRead")
    @EnforceAtLeastStudent
    @AllowedScopes(AgentScope.COMPETENCIES_READ)
    public ResponseEntity<Void> testScopedRead() {
        return ResponseEntity.ok().build();
    }

    @GetMapping("testScopedInstructorWrite")
    @EnforceAtLeastInstructor
    @AllowedScopes(AgentScope.COMPETENCIES_WRITE)
    public ResponseEntity<Void> testScopedInstructorWrite() {
        return ResponseEntity.ok().build();
    }

    @GetMapping("testUnscoped")
    @EnforceAtLeastStudent
    public ResponseEntity<Void> testUnscoped() {
        return ResponseEntity.ok().build();
    }
}
