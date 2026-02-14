package de.tum.cit.aet.artemis.core.security.policy;

import java.util.Set;

import de.tum.cit.aet.artemis.core.security.Role;

/**
 * A single rule in an access policy, pairing a condition with an effect.
 * Optionally carries documentation metadata (roles and notes) for doc generation.
 *
 * @param <T> the type of resource being evaluated
 */
public final class PolicyRule<T> {

    private final PolicyCondition<T> condition;

    private final PolicyEffect effect;

    private final Set<Role> documentedRoles;

    private final String note;

    PolicyRule(PolicyCondition<T> condition, PolicyEffect effect) {
        this(condition, effect, Set.of(), null);
    }

    private PolicyRule(PolicyCondition<T> condition, PolicyEffect effect, Set<Role> documentedRoles, String note) {
        this.condition = condition;
        this.effect = effect;
        this.documentedRoles = Set.copyOf(documentedRoles);
        this.note = note;
    }

    /**
     * @return the condition that must be satisfied for this rule to apply
     */
    public PolicyCondition<T> condition() {
        return condition;
    }

    /**
     * @return the effect (ALLOW or DENY) when the condition is met
     */
    public PolicyEffect effect() {
        return effect;
    }

    /**
     * @return the set of roles this rule is documented for (for doc generation)
     */
    public Set<Role> documentedRoles() {
        return documentedRoles;
    }

    /**
     * @return an optional note describing the condition under which this rule applies (for doc generation)
     */
    public String note() {
        return note;
    }

    /**
     * Returns a new rule with the given documented roles set.
     *
     * @param roles the roles this rule should be documented for
     * @return a new PolicyRule with the documented roles
     */
    public PolicyRule<T> documentedFor(Role... roles) {
        return new PolicyRule<>(condition, effect, Set.of(roles), note);
    }

    /**
     * Returns a new rule with the given documentation note.
     *
     * @param note a note describing the condition (e.g. "if enrolled + started")
     * @return a new PolicyRule with the note
     */
    public PolicyRule<T> withNote(String note) {
        return new PolicyRule<>(condition, effect, documentedRoles, note);
    }
}
