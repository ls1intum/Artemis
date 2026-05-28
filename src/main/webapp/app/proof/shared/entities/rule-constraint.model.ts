import { MathNode, mathNodesEqual } from './math-node.model';

/**
 * Discriminated union of side conditions attached to a rewrite rule.
 * The {@code type} discriminator mirrors the backend Jackson @JsonTypeInfo names.
 */
export type RuleConstraint = NotEqualToConstantConstraint;

export interface NotEqualToConstantConstraint {
    type: 'NOT_EQUAL_TO_CONSTANT';
    wildcardName: string;
    value: MathNode;
}

/**
 * Returns true iff the constraint is satisfied for the given wildcard bindings.
 * Mirrors `RuleConstraint.evaluate` on the backend; an unbound wildcard name fails the check.
 */
export function evaluateConstraint(constraint: RuleConstraint, bindings: Map<string, MathNode>): boolean {
    switch (constraint.type) {
        case 'NOT_EQUAL_TO_CONSTANT': {
            const bound = bindings.get(constraint.wildcardName);
            return bound !== undefined && !mathNodesEqual(bound, constraint.value);
        }
    }
}
