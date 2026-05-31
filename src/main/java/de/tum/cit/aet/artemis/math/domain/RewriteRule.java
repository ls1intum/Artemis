package de.tum.cit.aet.artemis.math.domain;

import java.util.List;

/**
 * A single rewrite rule belonging to a {@link BlockDefinition}.
 * Rules are code-only — they are never stored in the database.
 * <p>
 * Wildcards in {@code pattern} (nodes with {@code type="wildcard"}) capture arbitrary subtrees during matching.
 * The same variable name in {@code template} is replaced by the captured subtree during instantiation.
 * <p>
 * {@code constraints} are side conditions checked after a successful match but before instantiation —
 * e.g. {@code NotEqualToConstant("c", num("0"))} to encode {@code c != 0} on the fraction cancellation rule.
 * <p>
 * {@code direction} controls whether the student may apply the rule in reverse (template → pattern).
 * {@link RuleDirection#FORWARD_ONLY} retires the old {@code isReduction} flag — reductions are
 * forward-only by definition.
 *
 * @param id           unique identifier used to reference the rule in {@link DerivationStep#getAppliedRuleId()}
 * @param name         human-readable display name
 * @param paletteLatex LaTeX string for the rule palette (e.g., {@code "a+b \\to b+a"})
 * @param pattern      the LHS tree (may contain wildcards)
 * @param template     the RHS tree (wildcards are substituted with captured subtrees)
 * @param direction    whether the rule is forward-only or may be applied in reverse
 * @param constraints  side conditions on wildcard bindings; all must hold for the rule to apply
 */
public record RewriteRule(String id, String name, String paletteLatex, MathNode pattern, MathNode template, RuleDirection direction, List<RuleConstraint> constraints) {

    public RewriteRule {
        constraints = constraints == null ? List.of() : List.copyOf(constraints);
        if (direction == null) {
            direction = RuleDirection.FORWARD_ONLY;
        }
    }

    /** Convenience constructor for rules with no side conditions. */
    public RewriteRule(String id, String name, String paletteLatex, MathNode pattern, MathNode template, RuleDirection direction) {
        this(id, name, paletteLatex, pattern, template, direction, List.of());
    }
}
