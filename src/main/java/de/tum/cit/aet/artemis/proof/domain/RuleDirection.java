package de.tum.cit.aet.artemis.proof.domain;

/**
 * Direction in which a {@link RewriteRule} may be applied.
 * <p>
 * Mathematics is symmetric — an equation {@code a = b} licenses rewriting in either direction —
 * but some rules are pedagogically one-way: applying {@code a + 0 → a} reversely would let
 * students introduce arbitrary zero-additions. {@link #FORWARD_ONLY} encodes that asymmetry.
 */
public enum RuleDirection {

    /** The rule applies only in the {@code pattern → template} direction. Use for reductions and similar. */
    FORWARD_ONLY,

    /** The rule may be applied either as {@code pattern → template} or {@code template → pattern}. */
    BIDIRECTIONAL
}
