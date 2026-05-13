package de.tum.cit.aet.artemis.proof.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.proof.domain.DerivationStep;
import de.tum.cit.aet.artemis.proof.domain.MathNode;
import de.tum.cit.aet.artemis.proof.domain.ProofExercise;
import de.tum.cit.aet.artemis.proof.domain.ProofSubmission;
import de.tum.cit.aet.artemis.proof.domain.RewriteRule;

/**
 * Verifies proof derivation steps and grades submissions.
 * <p>
 * Path encoding: each integer in a step's {@code targetNodePath} is the index into the flat
 * children list of the current node, where children are ordered by sorted slot name then by
 * position within the slot. For example, an {@code add} node has slots {@code left} and {@code right};
 * the flat list is {@code [left[0], right[0]]} (alphabetical slot order).
 */
@Service
public class ProofGradingService {

    private final BlockRegistry blockRegistry;

    public ProofGradingService(BlockRegistry blockRegistry) {
        this.blockRegistry = blockRegistry;
    }

    /**
     * Applies a rewrite rule to the subtree at the given path in {@code tree}.
     *
     * @param tree the full expression tree
     * @param path index-path to the target node (empty = root)
     * @param rule the rule to apply
     * @return the new tree if the rule matched, otherwise empty
     */
    public Optional<MathNode> applyRule(MathNode tree, List<Integer> path, RewriteRule rule) {
        MathNode target = nodeAtPath(tree, path);
        Map<String, MathNode> bindings = match(rule.pattern(), target);
        if (bindings == null) {
            return Optional.empty();
        }
        MathNode result = instantiate(rule.template(), bindings);
        return Optional.of(replaceAtPath(tree, path, result));
    }

    /**
     * Grades a submission:
     * <ul>
     * <li>If the exercise has no source/target expression, falls back to checkbox comparison.</li>
     * <li>Otherwise, re-applies every recorded step and returns 100.0 iff all steps are valid
     * and the final expression equals the target.</li>
     * </ul>
     */
    public double gradeSubmission(ProofExercise exercise, ProofSubmission submission) {
        MathNode source = exercise.getSourceExpression();
        MathNode target = exercise.getTargetExpression();

        if (source == null || target == null) {
            return 0.0;
        }

        List<DerivationStep> steps = submission.getSteps();
        if (steps == null || steps.isEmpty()) {
            return source.equals(target) ? 100.0 : 0.0;
        }

        MathNode current = source;
        for (DerivationStep step : steps) {
            Optional<RewriteRule> ruleOpt = blockRegistry.findRuleById(step.getAppliedRuleId());
            if (ruleOpt.isEmpty()) {
                return 0.0;
            }
            Optional<MathNode> newTree = applyRule(current, step.getTargetNodePath(), ruleOpt.get());
            if (newTree.isEmpty() || !newTree.get().equals(step.getResultExpression())) {
                return 0.0;
            }
            current = step.getResultExpression();
        }
        return current.equals(target) ? 100.0 : 0.0;
    }

    // ---- Tree navigation ----

    private MathNode nodeAtPath(MathNode root, List<Integer> path) {
        MathNode current = root;
        for (int index : path) {
            List<MathNode> children = flatChildren(current);
            if (index >= children.size()) {
                throw new IllegalArgumentException("Path index " + index + " out of bounds (node has " + children.size() + " children)");
            }
            current = children.get(index);
        }
        return current;
    }

    /** Children in canonical order: slots sorted alphabetically, children within each slot in order. */
    private List<MathNode> flatChildren(MathNode node) {
        if (node.getSlots() == null || node.getSlots().isEmpty()) {
            return List.of();
        }
        return new TreeMap<>(node.getSlots()).values().stream().flatMap(List::stream).toList();
    }

    private MathNode replaceAtPath(MathNode root, List<Integer> path, MathNode replacement) {
        if (path.isEmpty()) {
            return replacement;
        }
        return replaceAtPathHelper(root, path, 0, replacement);
    }

    private MathNode replaceAtPathHelper(MathNode current, List<Integer> path, int depth, MathNode replacement) {
        if (depth == path.size()) {
            return replacement;
        }
        int targetIndex = path.get(depth);
        if (current.getSlots() == null || current.getSlots().isEmpty()) {
            throw new IllegalArgumentException("Cannot navigate into terminal node at depth " + depth);
        }

        Map<String, List<MathNode>> newSlots = new TreeMap<>();
        int globalIndex = 0;
        for (Map.Entry<String, List<MathNode>> entry : new TreeMap<>(current.getSlots()).entrySet()) {
            List<MathNode> children = entry.getValue();
            List<MathNode> newChildren = new ArrayList<>(children.size());
            for (MathNode child : children) {
                if (globalIndex == targetIndex) {
                    newChildren.add(replaceAtPathHelper(child, path, depth + 1, replacement));
                }
                else {
                    newChildren.add(child);
                }
                globalIndex++;
            }
            newSlots.put(entry.getKey(), newChildren);
        }
        return new MathNode(current.getType(), current.getValue(), newSlots);
    }

    // ---- Pattern matching ----

    /**
     * Returns a binding map {varName → capturedNode} if {@code pattern} matches {@code node}, or {@code null} if it doesn't.
     * A wildcard variable that appears more than once must capture the same subtree.
     */
    private Map<String, MathNode> match(MathNode pattern, MathNode node) {
        if ("wildcard".equals(pattern.getType())) {
            return new HashMap<>(Map.of(pattern.getValue(), node));
        }
        if (!Objects.equals(pattern.getType(), node.getType())) {
            return null;
        }
        // Terminal node: values must match
        boolean patternIsTerminal = pattern.getSlots() == null || pattern.getSlots().isEmpty();
        boolean nodeIsTerminal = node.getSlots() == null || node.getSlots().isEmpty();
        if (patternIsTerminal && nodeIsTerminal) {
            return Objects.equals(pattern.getValue(), node.getValue()) ? new HashMap<>() : null;
        }
        if (patternIsTerminal != nodeIsTerminal) {
            return null;
        }

        // Non-terminal: match all slots
        Map<String, List<MathNode>> patternSlots = pattern.getSlots();
        Map<String, List<MathNode>> nodeSlots = node.getSlots();
        if (!patternSlots.keySet().equals(nodeSlots.keySet())) {
            return null;
        }

        Map<String, MathNode> bindings = new HashMap<>();
        for (Map.Entry<String, List<MathNode>> entry : patternSlots.entrySet()) {
            List<MathNode> pChildren = entry.getValue();
            List<MathNode> nChildren = nodeSlots.get(entry.getKey());
            if (nChildren == null || pChildren.size() != nChildren.size()) {
                return null;
            }
            for (int i = 0; i < pChildren.size(); i++) {
                Map<String, MathNode> subBindings = match(pChildren.get(i), nChildren.get(i));
                if (subBindings == null) {
                    return null;
                }
                for (Map.Entry<String, MathNode> binding : subBindings.entrySet()) {
                    if (bindings.containsKey(binding.getKey()) && !bindings.get(binding.getKey()).equals(binding.getValue())) {
                        return null; // Inconsistent wildcard binding
                    }
                    bindings.put(binding.getKey(), binding.getValue());
                }
            }
        }
        return bindings;
    }

    /** Substitutes wildcard nodes in {@code template} with their captured subtrees from {@code bindings}. */
    private MathNode instantiate(MathNode template, Map<String, MathNode> bindings) {
        if ("wildcard".equals(template.getType())) {
            MathNode bound = bindings.get(template.getValue());
            return bound != null ? bound : template;
        }
        if (template.getSlots() == null || template.getSlots().isEmpty()) {
            return new MathNode(template.getType(), template.getValue(), null);
        }
        Map<String, List<MathNode>> newSlots = new TreeMap<>();
        for (Map.Entry<String, List<MathNode>> entry : template.getSlots().entrySet()) {
            List<MathNode> instantiated = entry.getValue().stream().map(child -> instantiate(child, bindings)).toList();
            newSlots.put(entry.getKey(), instantiated);
        }
        return new MathNode(template.getType(), template.getValue(), newSlots);
    }
}
