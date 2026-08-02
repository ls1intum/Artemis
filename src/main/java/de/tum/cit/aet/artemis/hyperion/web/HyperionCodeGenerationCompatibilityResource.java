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
 * Retirement tombstone for the released per-repository operation {@code POST api/hyperion/programming-exercises/{exerciseId}/generate-code}, whose engine was replaced by agentic
 * whole-exercise generation. Removing a released path silently is not permitted (see {@code documentation/docs/developer/guidelines/rest-api.mdx}), so the route stays mapped and
 * editor-authorized and answers a deterministic {@code 410 Gone} instead of an ambiguous {@code 404}.
 * <p>
 * The successor {@code POST .../generate-exercise} ({@link HyperionExerciseGenerationResource}) is not a drop-in: it multiplexes {@code GENERATE}/{@code ADAPT} explicitly, answers
 * {@code 202}, rewrites the problem statement plus all repositories rather than one, and has a different status/cancel/revert/event contract. Forwarding a legacy request into it
 * would silently broaden the mutation scope and still return a shape the caller cannot parse, so there is no safe transparent adapter.
 * <p>
 * Consequently this controller depends on <b>no</b> generation service, job, prompt, or DTO, and takes the body as an opaque {@link JsonNode} so that both the full legacy payload
 * and the bare {@code checkOnly} poll are accepted without resurrecting the old contract. It is {@link Hidden} from the generated OpenAPI spec so new integrations never see a
 * callable dead API; only already-deployed callers are expected to reach it. Delete the class once {@link #SUNSET_DATE} has passed and no legacy caller remains.
 */
@Conditional(HyperionEnabled.class)
@Lazy
@RestController
@RequestMapping("api/hyperion/")
public class HyperionCodeGenerationCompatibilityResource {

    /** Stable, programmatically-checkable error key surfaced both as a {@link ProblemDetail} property and embedded in the problem {@code type} URI. */
    static final String ERROR_KEY = "hyperionCodeGenerationRetired";

    /**
     * RFC 9745 Deprecation field-value: an HTTP Structured Field date, i.e. {@code "@"} followed by the Unix epoch second — here 2026-07-19T00:00:00Z, the release in which callers
     * can first observe the retirement. Deliberately not {@link LegacyApiPathDeprecationInterceptor#DEPRECATION_DATE}: that instant was announced for other paths, and this
     * operation was still fully functional then.
     */
    static final String DEPRECATION_DATE = "@1784419200";

    /** Sunset date (RFC 8594, IMF-fixdate). Shares the house-wide migration window of {@link LegacyApiPathDeprecationInterceptor} rather than inventing a second deadline. */
    static final String SUNSET_DATE = LegacyApiPathDeprecationInterceptor.SUNSET_DATE;

    private static final String SUCCESSOR_PATH_TEMPLATE = "/api/hyperion/programming-exercises/%d/generate-exercise";

    /**
     * POST programming-exercises/{exerciseId}/generate-code : retired. Always answers {@code 410 Gone}; never starts, inspects, or forwards to any generation job.
     *
     * @param exerciseId        the programming exercise id, kept so the original endpoint's authorization boundary and successor link still apply
     * @param ignoredLegacyBody any historical request body, accepted but never inspected
     * @return {@code 410 Gone} as {@code application/problem+json}, with {@code Deprecation}, {@code Sunset}, and successor {@code Link} headers
     */
    @Hidden
    @PostMapping("programming-exercises/{exerciseId}/generate-code")
    @EnforceAtLeastEditorInExercise
    public ResponseEntity<ProblemDetail> generateCodeRetired(@PathVariable long exerciseId, @RequestBody(required = false) JsonNode ignoredLegacyBody) {
        String successorPath = SUCCESSOR_PATH_TEMPLATE.formatted(exerciseId);

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
