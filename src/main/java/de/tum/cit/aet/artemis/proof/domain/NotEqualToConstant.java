package de.tum.cit.aet.artemis.proof.domain;

import java.util.Map;

/**
 * Constraint that fails if the wildcard {@code wildcardName} captured a subtree structurally
 * equal to {@code value}. Used to encode side conditions like {@code c != 0} on the fraction
 * cancellation rule, which is mathematically unsound when {@code c = 0}.
 *
 * @param wildcardName name of the wildcard variable (must appear in the rule's pattern)
 * @param value        the constant the binding must not equal
 */
public record NotEqualToConstant(String wildcardName, MathNode value) implements RuleConstraint {

    @Override
    public boolean evaluate(Map<String, MathNode> bindings) {
        MathNode bound = bindings.get(wildcardName);
        return bound != null && !bound.equals(value);
    }
}
