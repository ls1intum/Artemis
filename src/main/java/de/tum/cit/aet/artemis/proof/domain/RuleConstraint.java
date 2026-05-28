package de.tum.cit.aet.artemis.proof.domain;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Side condition attached to a {@link RewriteRule} that must hold after pattern matching
 * succeeds but before the template is instantiated. Used to encode rule applicability
 * restrictions that the structural pattern alone cannot express — most notably
 * domain restrictions such as {@code c != 0} on the cancellation rule.
 *
 * <p>
 * Constraints flow from backend rule definitions to the frontend palette via the
 * block-registry endpoint. They never come back from the client; submission grading
 * always re-applies the rule server-side with constraint enforcement.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({ @JsonSubTypes.Type(value = NotEqualToConstant.class, name = "NOT_EQUAL_TO_CONSTANT") })
public sealed interface RuleConstraint permits NotEqualToConstant {

    /**
     * Returns {@code true} iff the constraint is satisfied for the given wildcard bindings.
     * An unbound wildcard name in the constraint is treated as a failure (defensive: rule-authoring bug).
     *
     * @param bindings wildcard captures produced by the rule's pattern match
     * @return {@code true} if the constraint holds, {@code false} if the rule should be rejected
     */
    boolean evaluate(Map<String, MathNode> bindings);
}
