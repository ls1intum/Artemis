package de.tum.cit.aet.artemis.admin.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin endpoint that exposes the enabled-state and URL of integrated subsystems (iris, atlas, atlasml, athena).
 * Used by Claudia's e2e wrapper to validate that no real LLM endpoints are active before running Playwright tests.
 *
 * <p>
 * Auth: Bearer token via {@code artemis.config-check.token}. Returns 503 when the token is not configured
 * (disabled by default), 401 on wrong/missing token, 200 with subsystem state when auth succeeds.
 *
 * <p>
 * Path: {@code /api/admin/internal/config-check} — intentionally under the {@code /api/*\/internal/**}
 * pattern, which Spring Security permits without session auth. Token auth is enforced by this resource itself.
 */
@Profile(PROFILE_CORE)
@Lazy
@RestController
@RequestMapping("api/admin/internal")
public class AdminConfigCheckResource {

    private final Environment env;

    private final String expectedToken;

    public AdminConfigCheckResource(Environment env, @Value("${artemis.config-check.token:}") String expectedToken) {
        this.env = env;
        this.expectedToken = expectedToken;
    }

    /**
     * GET /api/admin/internal/config-check : Returns enabled-state and URL of subsystems (iris, atlas, atlasml, athena).
     *
     * @param auth the Authorization header (must be {@code Bearer <token>})
     * @return 200 with subsystem map, 401 on bad auth, 503 when endpoint is not configured
     */
    @GetMapping("/config-check")
    public ResponseEntity<Map<String, Object>> configCheck(@RequestHeader(name = "Authorization", required = false) String auth) {

        if (expectedToken == null || expectedToken.isBlank()) {
            // Endpoint not configured — disabled in production unless a token is set.
            return ResponseEntity.status(503).build();
        }

        if (auth == null || !auth.equals("Bearer " + expectedToken)) {
            return ResponseEntity.status(401).build();
        }

        // Hyperion intentionally omitted: no separate artemis.hyperion.url config exists (v1 scope).
        return ResponseEntity.ok(Map.of("iris", Map.of("enabled", env.getProperty("artemis.iris.enabled", Boolean.class, false), "url", env.getProperty("artemis.iris.url", "")),
                "atlas", Map.of("enabled", env.getProperty("artemis.atlas.enabled", Boolean.class, false)), "atlasml",
                Map.of("enabled", env.getProperty("artemis.atlas.atlasml.enabled", Boolean.class, false), "base_url", env.getProperty("artemis.atlas.atlasml.base-url", "")),
                "athena", Map.of("enabled", env.getProperty("artemis.athena.enabled", Boolean.class, false))));
    }
}
