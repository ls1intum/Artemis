package de.tum.cit.aet.artemis.math.grader;

import de.tum.cit.aet.artemis.math.domain.MathNode;

/**
 * Result of running the reduction strategy from an exercise's source expression to check whether the target is
 * automatically reachable by forward-only rules. Used by the editor to flag exercises where the student would
 * need a non-trivial application of bidirectional rules (commutativity, distributivity, …) that the reducer
 * cannot find on its own.
 *
 * @param reachable         whether the reducer ended up at the target (distance 0)
 * @param initialDistance   distance between source and target before reduction
 * @param finalDistance     distance between the reduced expression and target
 * @param reducedExpression the tree the reducer settled at (a fixpoint of all forward-only rules)
 */
public record ReachabilityReport(boolean reachable, int initialDistance, int finalDistance, MathNode reducedExpression) {
}
