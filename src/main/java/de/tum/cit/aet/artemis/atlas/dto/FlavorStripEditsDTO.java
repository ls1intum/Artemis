package de.tum.cit.aet.artemis.atlas.dto;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Edits returned by the flavor-stripping LLM. Each {@link EditDTO} is a literal
 * SEARCH/REPLACE pair applied to the raw input on the server: the first occurrence
 * of {@code search} is replaced with {@code replace}. Edits are applied in order.
 * <p>
 * All components are populated from external LLM output and are therefore null-permissive;
 * the consuming service tolerates {@code null} (treated as "no edits" / a skipped edit).
 *
 * @param edits the ordered list of edits; {@code null} or an empty list means the input
 *                  contained no narrative scaffolding to remove
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record FlavorStripEditsDTO(@Nullable List<EditDTO> edits) {

    /**
     * A single SEARCH/REPLACE edit.
     * <p>
     * The {@code reasoning} field is declared first so the reasoning model commits to a written
     * justification before it commits to a SEARCH span. Putting the answer field before
     * reasoning fields causes chain-of-thought models to lock in an answer before finishing
     * their analysis, so field order matters.
     *
     * @param reasoning short natural-language justification for why this edit is pure narrative
     *                      scaffolding rather than pedagogical content — one sentence, e.g.
     *                      "This is backstory about Alice; the next sentence (kept) is the spec."
     * @param search    the exact verbatim substring of the raw input to locate; a {@code null} or
     *                      empty span causes the edit to be skipped
     * @param replace   the replacement text; {@code null} or empty string for a clean deletion,
     *                      otherwise a minimal grammatical joiner (e.g. capitalization fix)
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record EditDTO(@Nullable String reasoning, @Nullable String search, @Nullable String replace) {
    }
}
