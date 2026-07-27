package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic;

import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

import de.tum.cit.aet.artemis.hyperion.service.HyperionSecretMaterialPolicy;

/**
 * The guardrails every reviewer pass applies to untrusted text in both directions: the caps that bound what a degenerate model response can do, the truncation used when reviewer
 * prose is surfaced, the secret-material check applied before any artifact reaches a provider, and the tolerant JSON extraction applied to every reviewer response.
 * <p>
 * They live in one place because each pass ({@link ConceptSelectionCritic}, {@link SpecificationReviewCritic}, {@link ContractWitnessAuthor}, {@link CriticVerdictParser},
 * {@link SpecFidelityCriticService}) must apply exactly the same bounds; duplicating them is how two passes silently drift apart.
 */
final class ReviewGuardrails {

    /** Defensive cap on how many model-reported uncovered requirements are surfaced, so a degenerate response can never flood the retry prompt or the review panel. */
    static final int MAX_REVIEW_FINDINGS = 12;

    /** A requirement string longer than this is almost certainly the model rambling rather than naming a concrete requirement; it is truncated before surfacing. */
    static final int MAX_REQUIREMENT_CHARS = 240;

    /** Complete artifact evidence beyond this size cannot be reviewed reliably in a bounded call. */
    static final int MAX_ARTIFACT_EVIDENCE_CHARS = 100_000;

    private static final HyperionSecretMaterialPolicy SECRET_MATERIAL_POLICY = new HyperionSecretMaterialPolicy();

    /** Matches a JSON object wrapped in a markdown code block (```json ... ``` or ``` ... ```), so a fenced model response is parsed. */
    private static final Pattern JSON_CODE_BLOCK_PATTERN = Pattern.compile("```(?:json)?\\s*(\\{.*?})\\s*```", Pattern.DOTALL);

    private ReviewGuardrails() {
    }

    /** Fails closed when text about to be sent to the provider carries secret material, so a leaked credential never leaves the server in a review prompt. */
    static void requireReviewTextSafe(String logicalPath, @Nullable String content) {
        SECRET_MATERIAL_POLICY.requireSafe(logicalPath, content == null ? new byte[0] : content.getBytes(StandardCharsets.UTF_8),
                HyperionSecretMaterialPolicy.Origin.GENERATED_CANDIDATE);
    }

    /**
     * Extracts the JSON object from a raw model response, tolerating a markdown code fence or leading/trailing prose. Mirrors the sibling Hyperion services' extraction so a chatty
     * local model's response still parses: (1) a fenced block, (2) the span from the first {@code {} to the last {@code }}, (3) the raw text.
     */
    static String extractJsonPayload(String responseText) {
        String trimmed = responseText.trim();
        Matcher codeBlockMatcher = JSON_CODE_BLOCK_PATTERN.matcher(trimmed);
        if (codeBlockMatcher.find()) {
            return codeBlockMatcher.group(1).trim();
        }
        int firstBrace = trimmed.indexOf('{');
        int lastBrace = trimmed.lastIndexOf('}');
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            return trimmed.substring(firstBrace, lastBrace + 1);
        }
        return trimmed;
    }

    static String truncate(String value) {
        return value.length() <= MAX_REQUIREMENT_CHARS ? value : value.substring(0, MAX_REQUIREMENT_CHARS) + "…";
    }

    /** Learning-fit explanations need enough room to retain the reviewer's causal diagnosis; the generic finding excerpts remain deliberately shorter. */
    static String truncateLearningEvidence(String value) {
        int limit = MAX_REQUIREMENT_CHARS * 2;
        return value.length() <= limit ? value : value.substring(0, limit) + "…";
    }
}
