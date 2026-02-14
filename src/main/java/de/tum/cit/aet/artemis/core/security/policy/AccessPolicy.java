package de.tum.cit.aet.artemis.core.security.policy;

import java.util.ArrayList;
import java.util.List;

import de.tum.cit.aet.artemis.core.domain.User;

/**
 * A declarative access policy that evaluates a sequence of rules against a user and resource.
 * Rules are evaluated in order; the first matching rule determines the outcome.
 * If no rule matches, the default effect applies.
 *
 * <p>
 * Example usage:
 *
 * <pre>{@code
 *
 * AccessPolicy<Course> policy = AccessPolicy.forResource(Course.class).named("course-visibility").rule(when(memberOfGroup(Course::getInstructorGroupName)).thenAllow())
 *         .rule(when(memberOfGroup(Course::getStudentGroupName).and(hasStarted(Course::getStartDate))).thenAllow()).denyByDefault();
 * }</pre>
 *
 * @param <T> the type of resource being protected
 */
public final class AccessPolicy<T> {

    private final Class<T> resourceType;

    private final String name;

    private final List<PolicyRule<T>> rules;

    private final PolicyEffect defaultEffect;

    private AccessPolicy(Class<T> resourceType, String name, List<PolicyRule<T>> rules, PolicyEffect defaultEffect) {
        this.resourceType = resourceType;
        this.name = name;
        this.rules = List.copyOf(rules);
        this.defaultEffect = defaultEffect;
    }

    /**
     * Entry point for building a policy for a given resource type.
     *
     * @param <T>          the resource type
     * @param resourceType the class of the resource
     * @return a new builder
     */
    public static <T> Builder<T> forResource(Class<T> resourceType) {
        return new Builder<>(resourceType);
    }

    /**
     * Starts building a rule from a condition. Use with static import:
     * {@code when(memberOfGroup(...)).thenAllow()}
     *
     * @param <T>       the resource type
     * @param condition the condition for the rule
     * @return a rule builder
     */
    public static <T> RuleBuilder<T> when(PolicyCondition<T> condition) {
        return new RuleBuilder<>(condition);
    }

    /**
     * Evaluates this policy for the given user and resource.
     *
     * @param user     the user to evaluate
     * @param resource the resource to evaluate against
     * @return the resulting effect (ALLOW or DENY)
     */
    public PolicyEffect evaluate(User user, T resource) {
        for (PolicyRule<T> rule : rules) {
            if (rule.condition().test(user, resource)) {
                return rule.effect();
            }
        }
        return defaultEffect;
    }

    /**
     * @return the policy name (useful for logging and auditing)
     */
    public String getName() {
        return name;
    }

    /**
     * @return the resource type this policy protects
     */
    public Class<T> getResourceType() {
        return resourceType;
    }

    /**
     * Builder for constructing an {@link AccessPolicy}.
     *
     * @param <T> the resource type
     */
    public static final class Builder<T> {

        private final Class<T> resourceType;

        private String name = "unnamed";

        private final List<PolicyRule<T>> rules = new ArrayList<>();

        private Builder(Class<T> resourceType) {
            this.resourceType = resourceType;
        }

        /**
         * Sets a descriptive name for the policy.
         *
         * @param name the policy name
         * @return this builder
         */
        public Builder<T> named(String name) {
            this.name = name;
            return this;
        }

        /**
         * Adds a rule to the policy. Rules are evaluated in the order they are added.
         *
         * @param rule the rule to add
         * @return this builder
         */
        public Builder<T> rule(PolicyRule<T> rule) {
            this.rules.add(rule);
            return this;
        }

        /**
         * Finalizes the policy with a default DENY effect when no rule matches.
         *
         * @return the built policy
         */
        public AccessPolicy<T> denyByDefault() {
            return new AccessPolicy<>(resourceType, name, rules, PolicyEffect.DENY);
        }

        /**
         * Finalizes the policy with a default ALLOW effect when no rule matches.
         *
         * @return the built policy
         */
        public AccessPolicy<T> allowByDefault() {
            return new AccessPolicy<>(resourceType, name, rules, PolicyEffect.ALLOW);
        }
    }

    /**
     * Builder for constructing a {@link PolicyRule} from a condition.
     *
     * @param <T> the resource type
     */
    public static final class RuleBuilder<T> {

        private final PolicyCondition<T> condition;

        RuleBuilder(PolicyCondition<T> condition) {
            this.condition = condition;
        }

        /**
         * Creates a rule that allows access when the condition is met.
         *
         * @return the policy rule
         */
        public PolicyRule<T> thenAllow() {
            return new PolicyRule<>(condition, PolicyEffect.ALLOW);
        }

        /**
         * Creates a rule that denies access when the condition is met.
         *
         * @return the policy rule
         */
        public PolicyRule<T> thenDeny() {
            return new PolicyRule<>(condition, PolicyEffect.DENY);
        }
    }
}
