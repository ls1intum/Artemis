package de.tum.cit.aet.artemis.hyperion.web;

import java.net.URI;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;

import de.tum.cit.aet.artemis.core.config.LegacyApiPathDeprecationInterceptor;
import de.tum.cit.aet.artemis.core.exception.ErrorConstants;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInExercise.EnforceAtLeastEditorInExercise;
import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;
import io.swagger.v3.oas.annotations.Hidden;

/**
 * Honest retirement tombstone for the released {@code POST api/hyperion/programming-exercises/{exerciseId}/generate-code} operation.
 * <p>
 * That per-repository code-generation endpoint shipped in Artemis 8.7.0 (PR #11405) and was still present through 9.7. It was deleted (not deprecated) by the commit that
 * introduced the agentic whole-exercise generation engine, which silently turned every released call into an unannounced {@code 404}. The repository's REST guidelines forbid
 * silently removing a released path (see {@code documentation/docs/developer/guidelines/rest-api.mdx} and the {@code LegacyApiPathDeprecationInterceptor} /
 * {@code *LegacyRestPaths} precedent used across other modules), so a bare {@code 404} is not an acceptable outcome here.
 * <p>
 * The new {@code POST .../generate-exercise} operation (see {@link HyperionExerciseGenerationResource}) is not a compatible replacement: it multiplexes
 * {@code GENERATE}/{@code ADAPT} explicitly, returns {@code 202} instead of {@code 200}, mutates the problem statement plus all repositories instead of one repository, and has a
 * different status/cancel/revert/event contract. Silently forwarding an old request into it would broaden the mutation scope without the caller's consent and still strand the
 * caller on an incompatible response shape. There is therefore no safe transparent adapter (see the audit at the top of this class's originating change for the full argument).
 * <p>
 * This controller is the deliberate middle ground: it keeps the exact old route mapped and editor-authorized (so callers get a deterministic, actionable {@code 410 Gone} instead
 * of an ambiguous {@code 404}), but has <b>no dependency on any generation service, job, prompt, or DTO</b> — the deleted engine stays deleted. The request body is accepted as an
 * opaque {@link JsonNode} (never deserialized into a restored {@code CodeGenerationRequestDTO}) so that both a full legacy body and the bare {@code checkOnly} polling shape are
 * accepted without resurrecting the old contract. The response follows RFC 9457 ({@code application/problem+json}) and carries {@code Deprecation}/{@code Sunset}/{@code Link}
 * headers so a caller can discover the successor programmatically. It is {@link Hidden} from the generated OpenAPI spec/clients so it never perpetuates a callable dead API for
 * new integrations; only already-deployed legacy callers are expected to hit it.
 * <p>
 * Remove this tombstone once the migration sunset below has passed and maintainers have confirmed no legacy caller still depends on it.
 */
@Conditional(HyperionEnabled.class)
@Lazy
@RestController
@RequestMapping("api/hyperion/")
public class HyperionCodeGenerationCompatibilityResource {

    /** Stable, programmatically-checkable error key surfaced both as a {@link ProblemDetail} property and embedded in the problem {@code type} URI. */
    static final String ERROR_KEY = "hyperionCodeGenerationRetired";

    /**
     * RFC 9745 (Deprecation HTTP header) field-value: an HTTP Structured Field date, i.e. {@code "@"} followed by the Unix epoch second.
     * <p>
     * This must NOT reuse {@link LegacyApiPathDeprecationInterceptor#DEPRECATION_DATE} (2026-05-28): that date was never announced for this operation, and the endpoint was still
     * fully functional then. The endpoint was actually deleted on 2026-07-02 (commit {@code b44846dd3d}), but that removal was never itself released to deployed clients — it only
     * existed on this development branch. The honest deprecation instant is therefore the date this tombstone (the first release in which callers can observe the retirement)
     * ships: 2026-07-19T00:00:00Z = 1784419200.
     */
    static final String DEPRECATION_DATE = "@1784419200";

    /**
     * Sunset date (RFC 8594, IMF-fixdate). Reuses the same shared migration-window sunset the rest of the codebase's legacy paths use ({@link
     * LegacyApiPathDeprecationInterceptor#SUNSET_DATE}) rather than inventing a second deadline, since this is the same house-wide "give deployed clients until here" convention.
     */
    static final String SUNSET_DATE = LegacyApiPathDeprecationInterceptor.SUNSET_DATE;

    private static final String SUCCESSOR_PATH_TEMPLATE = "/api/hyperion/programming-exercises/%d/generate-exercise";

    /**
     * POST programming-exercises/{exerciseId}/generate-code : retired. Always answers {@code 410 Gone}; never starts, inspects, or forwards to any generation job.
     *
     * @param exerciseId        the programming exercise id (kept so the same authorization boundary and successor link as the original endpoint apply)
     * @param ignoredLegacyBody any historical request body (a full legacy {@code CodeGenerationRequestDTO}-shaped payload or a bare {@code {"checkOnly": true}} poll), accepted but
     *                              never inspected or deserialized into a restored DTO
     * @return {@code 410 Gone} as {@code application/problem+json}, with {@code Deprecation}, {@code Sunset}, and successor {@code Link} headers
     */
    @Hidden
    @PostMapping("programming-exercises/{exerciseId}/generate-code")
    @EnforceAtLeastEditorInExercise
    public ResponseEntity<ProblemDetail> generateCodeRetired(@PathVariable long exerciseId, @RequestBody(required = false) JsonNode ignoredLegacyBody) {
        String successorPath = String.format(SUCCESSOR_PATH_TEMPLATE, exerciseId);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.GONE, "Per-repository code generation ('generate-code') was retired. Callers must adopt POST "
                + successorPath + " (agentic whole-exercise generation) and its status/event contract.");
        problem.setTitle("Hyperion Code Generation Retired");
        problem.setType(URI.create(ErrorConstants.PROBLEM_BASE_URL + "/" + ERROR_KEY));
        problem.setProperty("errorKey", ERROR_KEY);

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CONTENT_TYPE, "application/problem+json");
        headers.set("Deprecation", DEPRECATION_DATE);
        headers.set("Sunset", SUNSET_DATE);
        headers.set(HttpHeaders.LINK, "<" + successorPath + ">; rel=\"successor-version\"");

        return ResponseEntity.status(HttpStatus.GONE).headers(headers).body(problem);
    }
}
