package de.tum.cit.aet.artemis.atlas.service.util;

import org.jspecify.annotations.Nullable;

/**
 * Shared neutralization of instructor-authored text before it is interpolated into an Atlas LLM prompt.
 * <p>
 * Used by both the competency orchestrator (multi-line body of the execute prompt) and the AtlasML
 * similarity shortlist (single-line ranked list items). The two callers differ only in whether newlines
 * and tabs are preserved; everything else — zero-width neutralization, fence-delimiter neutralization,
 * and surrogate-safe hard truncation — is identical, so it lives here once.
 */
public final class AtlasPromptSanitizer {

    private static final String TRUNCATION_MARKER = " …[truncated]";

    /** Fence delimiters used by {@code orchestrator_execute_prompt.st}; literal occurrences in user data are neutralized. */
    private static final String USER_DATA_BEGIN = "<<<USER_DATA>>>";

    private static final String USER_DATA_END = "<<<END_USER_DATA>>>";

    private AtlasPromptSanitizer() {
    }

    /**
     * Neutralizes {@code raw} for prompt interpolation: replaces zero-width characters with spaces, strips or
     * collapses control characters depending on {@code singleLine}, neutralizes the user-data fence delimiters,
     * and hard-truncates at {@code maxChars} without ever splitting a UTF-16 surrogate pair.
     *
     * @param raw              the untrusted instructor text (may be {@code null})
     * @param maxChars         the hard length cap after which the truncation marker is appended
     * @param singleLine       {@code true} to force everything onto one line (all control chars incl. {@code \n}/{@code \t}
     *                             become spaces, runs of whitespace collapse); {@code false} to preserve {@code \n}/{@code \t}
     *                             and only collapse 3+ consecutive newlines
     * @param emptyPlaceholder the value returned when {@code raw} is null, blank, or reduces to empty after normalization
     * @return the sanitized, length-bounded string
     */
    public static String sanitizeForPrompt(@Nullable String raw, int maxChars, boolean singleLine, String emptyPlaceholder) {
        if (raw == null || raw.isBlank()) {
            return emptyPlaceholder;
        }
        String normalized = raw.replace('\u00A0', ' ').replace('\u200B', ' ').replace('\u200C', ' ').replace('\u200D', ' ').replace('\uFEFF', ' ');
        if (singleLine) {
            normalized = normalized.replaceAll("\\p{Cntrl}", " ").replaceAll("\\s{2,}", " ").strip();
        }
        else {
            normalized = normalized.replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", "").replaceAll("\\n{3,}", "\n\n").strip();
        }
        if (normalized.isEmpty()) {
            return emptyPlaceholder;
        }
        normalized = normalized.replace(USER_DATA_BEGIN, "<<<USER_DATA_LITERAL>>>").replace(USER_DATA_END, "<<<END_USER_DATA_LITERAL>>>");
        if (normalized.length() > maxChars) {
            int cut = Math.max(0, maxChars - TRUNCATION_MARKER.length());
            // Never truncate mid surrogate pair: dropping a lone high surrogate would emit an unpaired code unit.
            if (cut > 0 && Character.isHighSurrogate(normalized.charAt(cut - 1))) {
                cut--;
            }
            normalized = normalized.substring(0, cut) + TRUNCATION_MARKER;
        }
        return normalized;
    }
}
