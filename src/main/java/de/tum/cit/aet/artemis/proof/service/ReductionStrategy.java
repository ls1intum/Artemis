package de.tum.cit.aet.artemis.proof.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import de.tum.cit.aet.artemis.proof.domain.MathNode;
import de.tum.cit.aet.artemis.proof.domain.RewriteRule;

/**
 * Repeatedly applies a list of rules to a tree until no rule fires or a step bound is hit. Used to power
 * hint generation and the editor's reachability check ("is the target obviously derivable from the source
 * by automated reasoning?").
 * <p>
 * Designed as a plain utility, not a Spring bean — callers (the grader, the editor's reachability endpoint)
 * pass in their own rule list and an {@link RuleApplier} lambda. This keeps the strategy fully decoupled
 * from the grader and avoids a circular dependency.
 * <p>
 * Termination is guaranteed when the supplied rule list contains only strictly-reducing rules (the engine's
 * {@code FORWARD_ONLY} subset, by convention); a {@code maxSteps} safety bound catches violations of that invariant.
 */
public final class ReductionStrategy {

    /** Default cap on the number of reductions per invocation. */
    public static final int DEFAULT_MAX_STEPS = 200;

    private final List<RewriteRule> rules;

    private final RuleApplier applier;

    public ReductionStrategy(List<RewriteRule> rules, RuleApplier applier) {
        this.rules = List.copyOf(rules);
        this.applier = applier;
    }

    /**
     * Reduces {@code start} to a fixpoint with the default step bound.
     *
     * @param start the tree to reduce
     * @return the reduced tree plus the sequence of applied steps in order
     */
    public ReductionResult reduceToFixpoint(MathNode start) {
        return reduceToFixpoint(start, DEFAULT_MAX_STEPS);
    }

    /**
     * Reduces {@code start} by repeatedly applying the configured rules until no rule fires or the step bound is hit.
     *
     * @param start    the tree to reduce
     * @param maxSteps maximum number of reductions to apply
     * @return the reduced tree plus the sequence of applied steps in order
     */
    public ReductionResult reduceToFixpoint(MathNode start, int maxSteps) {
        if (start == null) {
            return new ReductionResult(null, List.of());
        }
        MathNode current = start;
        List<AppliedStep> history = new ArrayList<>();
        for (int i = 0; i < maxSteps; i++) {
            Optional<AppliedStep> step = findNextReduction(current);
            if (step.isEmpty()) {
                break;
            }
            history.add(step.get());
            current = step.get().result();
        }
        return new ReductionResult(current, history);
    }

    private Optional<AppliedStep> findNextReduction(MathNode tree) {
        for (List<Integer> path : enumeratePaths(tree)) {
            for (RewriteRule rule : rules) {
                Optional<MathNode> result = applier.apply(tree, path, rule);
                if (result.isPresent() && !result.get().equals(tree)) {
                    return Optional.of(new AppliedStep(rule.id(), path, result.get()));
                }
            }
        }
        return Optional.empty();
    }

    /** Enumerates all index-paths in the tree in canonical DFS order (root first, then children left-to-right). */
    private static List<List<Integer>> enumeratePaths(MathNode tree) {
        List<List<Integer>> out = new ArrayList<>();
        collectPaths(tree, List.of(), out);
        return out;
    }

    private static void collectPaths(MathNode node, List<Integer> path, List<List<Integer>> out) {
        out.add(path);
        if (node.getSlots() == null) {
            return;
        }
        int idx = 0;
        for (Map.Entry<String, List<MathNode>> entry : new TreeMap<>(node.getSlots()).entrySet()) {
            for (MathNode child : entry.getValue()) {
                List<Integer> childPath = new ArrayList<>(path.size() + 1);
                childPath.addAll(path);
                childPath.add(idx);
                collectPaths(child, List.copyOf(childPath), out);
                idx++;
            }
        }
    }

    /** Functional interface bridging this strategy to whichever rule-application engine the caller owns. */
    @FunctionalInterface
    public interface RuleApplier {

        /**
         * Attempt to apply {@code rule} at {@code path} in {@code tree}.
         *
         * @param tree the tree to rewrite
         * @param path index-path to the subtree to rewrite at
         * @param rule the rule to apply
         * @return the rewritten tree, or empty if the rule does not apply
         */
        Optional<MathNode> apply(MathNode tree, List<Integer> path, RewriteRule rule);
    }

    /** Result of a reduction run: the final tree plus the ordered sequence of applied steps. */
    public record ReductionResult(MathNode tree, List<AppliedStep> history) {
    }

    /** A single step applied during reduction. */
    public record AppliedStep(String ruleId, List<Integer> path, MathNode result) {
    }
}
