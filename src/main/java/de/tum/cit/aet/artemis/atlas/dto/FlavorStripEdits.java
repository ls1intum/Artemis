package de.tum.cit.aet.artemis.atlas.dto;

import java.util.List;

/**
 * Edits returned by the flavor-stripping LLM. Each {@link Edit} is a literal
 * SEARCH/REPLACE pair applied to the raw input on the server: the first occurrence
 * of {@code search} is replaced with {@code replace}. Edits are applied in order.
 *
 * @param edits the ordered list of edits; an empty list means the input contained
 *                  no narrative scaffolding to remove
 */
public record FlavorStripEdits(List<Edit> edits) {

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
     * @param search    the exact verbatim substring of the raw input to locate
     * @param replace   the replacement text; empty string for a clean deletion, otherwise
     *                      a minimal grammatical joiner (e.g. capitalization fix)
     */
    public record Edit(String reasoning, String search, String replace) {
    }
}
