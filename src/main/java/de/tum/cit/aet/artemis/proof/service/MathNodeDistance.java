package de.tum.cit.aet.artemis.proof.service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import de.tum.cit.aet.artemis.proof.domain.MathNode;

/**
 * Coarse tree-distance heuristic between two {@link MathNode} trees.
 * <p>
 * The chosen metric is the cardinality of the multiset symmetric difference of all subtrees:
 * for every distinct subtree {@code s}, sum {@code |count_a(s) - count_b(s)|}.
 * <p>
 * Properties:
 * <ul>
 * <li>{@code distance(t, t) == 0} for every tree.</li>
 * <li>Symmetric: {@code distance(a, b) == distance(b, a)}.</li>
 * <li>Bounded above by {@code size(a) + size(b)} (when no subtrees are shared).</li>
 * <li>Decreases as the student rewrites toward the target on rules that change tree shape
 * (e.g. {@code add_zero_left}). Stays flat on shape-preserving rules like {@code add_comm}.</li>
 * </ul>
 * <p>
 * This is intentionally a coarse first cut — easier to implement and reason about than Zhang–Shasha,
 * good enough as a partial-credit ratio. If it produces bad scores in practice, swap in a proper
 * tree-edit-distance algorithm without changing the grader API.
 */
public final class MathNodeDistance {

    private MathNodeDistance() {
    }

    /**
     * Returns the multiset-symmetric-difference distance between two trees.
     *
     * @param a first tree (may be {@code null})
     * @param b second tree (may be {@code null})
     * @return distance &ge; 0; zero iff {@code a.equals(b)}
     */
    public static int distance(MathNode a, MathNode b) {
        if (a == null && b == null) {
            return 0;
        }
        if (a == null) {
            return size(b);
        }
        if (b == null) {
            return size(a);
        }
        if (a.equals(b)) {
            return 0;
        }
        Map<MathNode, Integer> ca = new HashMap<>();
        Map<MathNode, Integer> cb = new HashMap<>();
        collectSubtrees(a, ca);
        collectSubtrees(b, cb);
        int total = 0;
        Set<MathNode> keys = new HashSet<>(ca.keySet());
        keys.addAll(cb.keySet());
        for (MathNode key : keys) {
            total += Math.abs(ca.getOrDefault(key, 0) - cb.getOrDefault(key, 0));
        }
        return total;
    }

    /**
     * Returns the number of nodes in the tree (1 per node, recursive).
     *
     * @param node the tree (may be {@code null})
     * @return node count, 0 for {@code null}
     */
    public static int size(MathNode node) {
        if (node == null) {
            return 0;
        }
        int count = 1;
        if (node.getSlots() != null) {
            for (List<MathNode> children : node.getSlots().values()) {
                if (children == null) {
                    continue;
                }
                for (MathNode child : children) {
                    count += size(child);
                }
            }
        }
        return count;
    }

    private static void collectSubtrees(MathNode node, Map<MathNode, Integer> counts) {
        if (node == null) {
            return;
        }
        counts.merge(node, 1, Integer::sum);
        if (node.getSlots() != null) {
            for (List<MathNode> children : node.getSlots().values()) {
                if (children == null) {
                    continue;
                }
                for (MathNode child : children) {
                    collectSubtrees(child, counts);
                }
            }
        }
    }
}
