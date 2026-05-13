package de.tum.cit.aet.artemis.proof.domain;

/**
 * A single rewrite rule belonging to a {@link BlockDefinition}.
 * Rules are code-only — they are never stored in the database.
 * <p>
 * Wildcards in {@code pattern} (nodes with {@code type="wildcard"}) capture arbitrary subtrees during matching.
 * The same variable name in {@code template} is replaced by the captured subtree during instantiation.
 *
 * @param id           unique identifier used to reference the rule in {@link DerivationStep#getAppliedRuleId()}
 * @param name         human-readable display name
 * @param paletteLatex LaTeX string for the rule palette (e.g., {@code "a+b \\to b+a"})
 * @param pattern      the LHS tree (may contain wildcards)
 * @param template     the RHS tree (wildcards are substituted with captured subtrees)
 * @param isReduction  whether this rule always reduces expression size (used for hint filtering in MS2+)
 */
public record RewriteRule(String id, String name, String paletteLatex, MathNode pattern, MathNode template, boolean isReduction) {
}
