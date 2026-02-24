package de.tum.cit.aet.artemis.core.security.policy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.springframework.data.jpa.domain.Specification;

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
 * AccessPolicy<Course> policy = AccessPolicy.forResource(Course.class).named("course-visibility").section("Navigation").feature("Course Overview")
 *         .rule(when(memberOfGroup(Course::getInstructorGroupName)).thenAllow().documentedFor(INSTRUCTOR))
 *         .rule(when(memberOfGroup(Course::getStudentGroupName).and(hasStarted(Course::getStartDate))).thenAllow().documentedFor(STUDENT).withNote("if enrolled + started"))
 *         .denyByDefault();
 * }</pre>
 *
 * @param <T> the type of resource being protected
 */
public final class AccessPolicy<T> {

    private final Class<T> resourceType;

    private final String name;

    private final String section;

    private final List<String> features;

    private final List<PolicyRule<T>> rules;

    private final PolicyEffect defaultEffect;

    private AccessPolicy(Class<T> resourceType, String name, String section, List<String> features, List<PolicyRule<T>> rules, PolicyEffect defaultEffect) {
        this.resourceType = resourceType;
        this.name = name;
        this.section = section;
        this.features = List.copyOf(features);
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
     * @return the documentation section this policy belongs to (e.g. "Navigation"), or null if not set
     */
    public String getSection() {
        return section;
    }

    /**
     * @return the first feature label for documentation tables (e.g. "Course Overview"), or null if not set
     */
    public String getFeature() {
        return features.isEmpty() ? null : features.getFirst();
    }

    /**
     * @return all feature labels for documentation tables (one row per feature), or an empty list if not set
     */
    public List<String> getFeatures() {
        return features;
    }

    /**
     * @return an unmodifiable list of the rules in evaluation order
     */
    public List<PolicyRule<T>> getRules() {
        return rules;
    }

    /**
     * Converts this access policy to a JPA Specification that can be used for database queries.
     * <p>
     * This enables using the same access logic for both runtime authorization checks
     * and database filtering. The generated specification will match only resources that
     * would pass the {@link #evaluate(User, Object)} check for the given user context.
     * <p>
     * <strong>Important:</strong> All conditions in the policy must implement {@link SpecificationCondition}
     * for this conversion to work. Use conditions from {@link SpecificationConditions} instead of
     * {@link Conditions} when defining policies that need to be converted to specifications.
     *
     * @param userGroups the groups the current user belongs to
     * @param isAdmin    whether the current user is an admin
     * @return a Specification representing this policy's ALLOW rules
     * @throws IllegalStateException if any condition in the policy does not implement {@link SpecificationCondition}
     */
    public Specification<T> toSpecification(Set<String> userGroups, boolean isAdmin) {
        return (root, query, criteriaBuilder) -> {
            // Collect all ALLOW rule conditions
            List<SpecificationCondition<T>> allowConditions = new ArrayList<>();

            for (PolicyRule<T> rule : rules) {
                if (rule.effect() == PolicyEffect.ALLOW) {
                    PolicyCondition<T> condition = rule.condition();
                    if (!(condition instanceof SpecificationCondition<T> specCondition)) {
                        throw new IllegalStateException("Policy '" + name + "' contains a condition that does not implement SpecificationCondition. "
                                + "Use SpecificationConditions instead of Conditions to enable query generation.");
                    }
                    allowConditions.add(specCondition);
                }
            }

            // If no ALLOW rules and default is ALLOW, allow everything
            if (allowConditions.isEmpty() && defaultEffect == PolicyEffect.ALLOW) {
                return criteriaBuilder.conjunction(); // always true
            }

            // If no ALLOW rules and default is DENY, deny everything
            if (allowConditions.isEmpty() && defaultEffect == PolicyEffect.DENY) {
                return criteriaBuilder.disjunction(); // always false
            }

            // Combine all ALLOW conditions with OR
            // (any rule that allows access should result in the resource being included)
            return allowConditions.stream().map(cond -> cond.toPredicate(root, criteriaBuilder, userGroups, isAdmin)).reduce(criteriaBuilder::or)
                    .orElse(defaultEffect == PolicyEffect.ALLOW ? criteriaBuilder.conjunction() : criteriaBuilder.disjunction());
        };
    }

    /**
     * Builder for constructing an {@link AccessPolicy}.
     *
     * @param <T> the resource type
     */
    public static final class Builder<T> {

        private final Class<T> resourceType;

        private String name = "unnamed";

        private String section;

        private final List<String> features = new ArrayList<>();

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
         * Sets the documentation section heading this policy belongs to.
         *
         * @param section the section name (maps to ## heading in access-rights.mdx)
         * @return this builder
         */
        public Builder<T> section(String section) {
            this.section = section;
            return this;
        }

        /**
         * Adds a single feature label for the documentation table.
         * Can be called multiple times to add multiple features (one row per feature).
         *
         * @param feature the feature name (row label in the generated table)
         * @return this builder
         */
        public Builder<T> feature(String feature) {
            this.features.add(feature);
            return this;
        }

        /**
         * Adds multiple feature labels for the documentation table (one row per feature).
         * All features share the same access rules.
         *
         * @param features the feature names (row labels in the generated table)
         * @return this builder
         */
        public Builder<T> features(String... features) {
            this.features.addAll(Arrays.asList(features));
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
            return new AccessPolicy<>(resourceType, name, section, features, rules, PolicyEffect.DENY);
        }

        /**
         * Finalizes the policy with a default ALLOW effect when no rule matches.
         *
         * @return the built policy
         */
        public AccessPolicy<T> allowByDefault() {
            return new AccessPolicy<>(resourceType, name, section, features, rules, PolicyEffect.ALLOW);
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
