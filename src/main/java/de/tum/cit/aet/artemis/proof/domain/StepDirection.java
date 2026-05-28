package de.tum.cit.aet.artemis.proof.domain;

/**
 * Direction in which a {@link DerivationStep} replays a {@link RewriteRule}.
 * <p>
 * {@link #FORWARD} is the default and matches pattern → template. {@link #REVERSE} matches
 * template → pattern and is only valid for rules whose {@link RewriteRule#direction()} is
 * {@link RuleDirection#BIDIRECTIONAL}; otherwise the grader rejects the step.
 */
public enum StepDirection {
    FORWARD, REVERSE
}
