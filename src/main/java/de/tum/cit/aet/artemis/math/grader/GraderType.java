package de.tum.cit.aet.artemis.math.grader;

/**
 * Discriminator used to dispatch a {@link MathGrader} for a given exercise.
 * The current step-by-step rewrite engine is {@link #REWRITE_CHAIN}; backend graders
 * (Lean, Isabelle, egg e-graphs) are planned for milestone 3+.
 */
public enum GraderType {

    /** Step-by-step structural rewriting against a fixed rule library. The default. */
    REWRITE_CHAIN,

    /** Planned: a Lean kernel verifies the math. Out-of-scope until M3. */
    LEAN,

    /** Planned: Isabelle/HOL via Isabelle/jEdit or a custom server. Out-of-scope until M3. */
    ISABELLE,

    /** Planned: egg e-graph saturation in a Rust sidecar. Out-of-scope until M3. */
    EGG_EGRAPH
}
