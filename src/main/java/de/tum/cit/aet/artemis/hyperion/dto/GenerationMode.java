package de.tum.cit.aet.artemis.hyperion.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * The explicit intent of an agentic whole-exercise run, chosen by the client and never inferred from the exercise's contents.
 * <p>
 * A single endpoint and a single engine drive both modes; the mode only selects the seed and prompt framing (and, for {@link #ADAPT}, relaxes the tests-repository immutability
 * gate so an existing test suite can be revised). Keeping it explicit avoids a heuristic that would guess "adapt" from a present problem statement — a from-scratch
 * {@link #GENERATE} run may legitimately start against a drafted statement.
 */
@JsonFormat(shape = JsonFormat.Shape.STRING)
public enum GenerationMode {

    /** Author a complete exercise (solution, template, tests, problem statement) from the brief and/or the current draft statement. */
    GENERATE,

    /** Revise an existing, working exercise in place, typically to address instructor feedback (e.g. selected review-comment threads). */
    ADAPT
}
