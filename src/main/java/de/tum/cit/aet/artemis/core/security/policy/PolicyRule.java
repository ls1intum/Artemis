package de.tum.cit.aet.artemis.core.security.policy;

/**
 * A single rule in an access policy, pairing a condition with an effect.
 *
 * @param <T>       the type of resource being evaluated
 * @param condition the condition that must be satisfied for this rule to apply
 * @param effect    the effect (ALLOW or DENY) when the condition is met
 */
public record PolicyRule<T>(PolicyCondition<T> condition, PolicyEffect effect) {
}
