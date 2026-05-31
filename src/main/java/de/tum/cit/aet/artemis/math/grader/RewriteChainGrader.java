package de.tum.cit.aet.artemis.math.grader;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.ToIntFunction;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.math.config.MathEnabled;
import de.tum.cit.aet.artemis.math.domain.DerivationStep;
import de.tum.cit.aet.artemis.math.domain.GoalMode;
import de.tum.cit.aet.artemis.math.domain.MathExercise;
import de.tum.cit.aet.artemis.math.domain.MathNode;
import de.tum.cit.aet.artemis.math.domain.MathNodes;
import de.tum.cit.aet.artemis.math.domain.MathSubmission;
import de.tum.cit.aet.artemis.math.domain.RewriteRule;
import de.tum.cit.aet.artemis.math.domain.RuleConstraint;
import de.tum.cit.aet.artemis.math.domain.RuleDirection;
import de.tum.cit.aet.artemis.math.domain.StepDirection;
import de.tum.cit.aet.artemis.math.service.BlockRegistry;
import de.tum.cit.aet.artemis.math.service.MathNodeDistance;
import de.tum.cit.aet.artemis.math.service.ReductionStrategy;

/**
 * Step-by-step grader: re-applies each {@link DerivationStep} against a rule from
 * {@link BlockRegistry} and accepts the math iff the chain terminates at {@code targetExpression}.
 * <p>
 * Path encoding: each integer in a step's {@code targetNodePath} is the index into the flat
 * children list of the current node, where children are ordered by sorted slot name then by
 * position within the slot. For example, an {@code add} node has slots {@code left} and {@code right};
 * the flat list is {@code [left[0], right[0]]} (alphabetical slot order).
 */
@Conditional(MathEnabled.class)
@Lazy
@Service
public class RewriteChainGrader implements MathGrader {

    private final BlockRegistry blockRegistry;

    public RewriteChainGrader(BlockRegistry blockRegistry) {
        this.blockRegistry = blockRegistry;
    }

    @Override
    public GraderType getType() {
        return GraderType.REWRITE_CHAIN;
    }

    /**
     * Applies a rewrite rule forward (pattern → template) to the subtree at the given path.
     *
     * @param tree the full expression tree
     * @param path index-path to the target node (empty = root)
     * @param rule the rule to apply
     * @return the new tree if the rule matched and constraints held, otherwise empty
     */
    public Optional<MathNode> applyRule(MathNode tree, List<Integer> path, RewriteRule rule) {
        return applyRule(tree, path, rule, StepDirection.FORWARD);
    }

    /**
     * Applies a rewrite rule in the given direction.
     * <p>
     * {@link StepDirection#FORWARD} matches {@code rule.pattern} and instantiates {@code rule.template}.
     * {@link StepDirection#REVERSE} matches {@code rule.template} and instantiates {@code rule.pattern} —
     * and is only valid for {@link RuleDirection#BIDIRECTIONAL} rules; reverse on a forward-only rule
     * always returns empty.
     *
     * @param tree      the full expression tree
     * @param path      index-path to the target node (empty = root)
     * @param rule      the rule to apply
     * @param direction the direction in which to apply the rule
     * @return the new tree if the rule matched and constraints held, otherwise empty
     */
    public Optional<MathNode> applyRule(MathNode tree, List<Integer> path, RewriteRule rule, StepDirection direction) {
        if (direction == StepDirection.REVERSE && rule.direction() != RuleDirection.BIDIRECTIONAL) {
            return Optional.empty();
        }
        MathNode patternSide = direction == StepDirection.REVERSE ? rule.template() : rule.pattern();
        MathNode templateSide = direction == StepDirection.REVERSE ? rule.pattern() : rule.template();
        MathNode target = nodeAtPath(tree, path);
        Map<String, MathNode> bindings = match(patternSide, target);
        if (bindings == null) {
            return Optional.empty();
        }
        for (RuleConstraint constraint : rule.constraints()) {
            if (!constraint.evaluate(bindings)) {
                return Optional.empty();
            }
        }
        MathNode result = instantiate(templateSide, bindings);
        return Optional.of(replaceAtPath(tree, path, result));
    }

    @Override
    public GradingResult grade(MathExercise exercise, MathSubmission submission) {
        return GradingResult.of(gradeSubmission(exercise, submission));
    }

    /**
     * Grades a submission and returns the raw score. Dispatches on {@link MathExercise#getGoalMode()}
     * — TRANSFORMATION mode compares against {@code targetExpression}, EQUATION mode reduces to a tautology.
     *
     * @param exercise   the exercise being graded
     * @param submission the student's submission
     * @return score in [0, 100]
     */
    public double gradeSubmission(MathExercise exercise, MathSubmission submission) {
        GoalMode mode = exercise.getGoalMode() == null ? GoalMode.TRANSFORMATION : exercise.getGoalMode();
        return mode == GoalMode.EQUATION ? gradeEquationMode(exercise, submission) : gradeTransformationMode(exercise, submission);
    }

    private double gradeTransformationMode(MathExercise exercise, MathSubmission submission) {
        MathNode source = exercise.getSourceExpression();
        MathNode target = exercise.getTargetExpression();
        if (source == null || target == null) {
            return 0.0;
        }
        boolean ac = exercise.isAcNormalization();
        List<DerivationStep> steps = submission.getSteps();
        if (steps == null || steps.isEmpty()) {
            return MathNodes.equalsAC(source, target, ac) ? 100.0 : 0.0;
        }
        ReplayResult r = replay(source, steps, ac);
        if (MathNodes.equalsAC(r.current(), target, ac)) {
            return 100.0;
        }
        if (!exercise.isPartialCreditEnabled()) {
            return 0.0;
        }
        return distanceBasedScore(comparableDistance(source, target, ac), comparableDistance(r.current(), target, ac));
    }

    private double gradeEquationMode(MathExercise exercise, MathSubmission submission) {
        MathNode goal = exercise.getGoalExpression();
        if (goal == null) {
            return 0.0;
        }
        boolean ac = exercise.isAcNormalization();
        List<DerivationStep> steps = submission.getSteps();
        if (steps == null || steps.isEmpty()) {
            return MathNodes.isTautology(canonicalise(goal, ac)) ? 100.0 : 0.0;
        }
        ReplayResult r = replay(goal, steps, ac);
        if (MathNodes.isTautology(canonicalise(r.current(), ac))) {
            return 100.0;
        }
        if (!exercise.isPartialCreditEnabled()) {
            return 0.0;
        }
        // In EQUATION mode, partial credit reflects how close the two sides of the current equation are to each other,
        // relative to how far apart they started.
        return distanceBasedScore(sideDistance(goal, ac), sideDistance(r.current(), ac));
    }

    /**
     * Replays the student's steps against {@code start}, returning the final tree and the count of valid steps before any break.
     * The chain also breaks if a step's result revisits a previously-seen state (no-regress invariant) — looping
     * applications of {@code add_comm} and the like cannot inflate the valid-step count or distance progress.
     * When {@code ac} is true, equality checks and visited-state membership are AC-aware so that students don't
     * need to apply commutativity / associativity rules explicitly to make trees match.
     */
    private ReplayResult replay(MathNode start, List<DerivationStep> steps, boolean ac) {
        MathNode current = start;
        int validSteps = 0;
        Set<MathNode> visited = new HashSet<>();
        visited.add(canonicalise(start, ac));
        for (DerivationStep step : steps) {
            Optional<RewriteRule> ruleOpt = blockRegistry.findRuleById(step.getAppliedRuleId());
            if (ruleOpt.isEmpty()) {
                break;
            }
            Optional<MathNode> newTree = applyRule(current, step.getTargetNodePath(), ruleOpt.get(), step.getDirection());
            if (newTree.isEmpty() || !MathNodes.equalsAC(newTree.get(), step.getResultExpression(), ac)) {
                break;
            }
            if (!visited.add(canonicalise(step.getResultExpression(), ac))) {
                break;
            }
            current = step.getResultExpression();
            validSteps++;
        }
        return new ReplayResult(current, validSteps);
    }

    /** Returns the AC-normalised form when {@code ac} is true, the input unchanged otherwise. */
    private static MathNode canonicalise(MathNode node, boolean ac) {
        return ac ? MathNodes.normalizeAC(node) : node;
    }

    /** Distance between two trees, AC-normalised first when {@code ac} is true. */
    private static int comparableDistance(MathNode a, MathNode b, boolean ac) {
        return MathNodeDistance.distance(canonicalise(a, ac), canonicalise(b, ac));
    }

    /**
     * Returns a partial-credit score in [0, 99] from a starting distance and the current distance to the goal.
     * Higher score means less remaining distance. Returns 0 if the student has made no progress or regressed.
     */
    private double distanceBasedScore(int initialDist, int currentDist) {
        if (initialDist <= 0) {
            return 0.0;
        }
        double ratio = 1.0 - (double) currentDist / initialDist;
        return Math.max(0.0, Math.min(99.0, ratio * 100.0));
    }

    @Override
    public List<HintSuggestion> suggestHints(MathExercise exercise, MathNode currentState) {
        if (currentState == null) {
            return List.of();
        }
        boolean ac = exercise.isAcNormalization();
        ToIntFunction<MathNode> metric = progressMetricFor(exercise, ac);
        if (metric == null) {
            return List.of();
        }
        List<Candidate> candidates = new ArrayList<>();
        Set<MathNode> seenResults = new HashSet<>();
        for (List<Integer> path : enumeratePaths(currentState)) {
            for (var block : blockRegistry.getAllBlocks()) {
                for (RewriteRule rule : blockRegistry.getNormalizedRulesFor(block)) {
                    addCandidateIfFresh(currentState, path, rule, StepDirection.FORWARD, ac, metric, candidates, seenResults);
                    if (rule.direction() == RuleDirection.BIDIRECTIONAL) {
                        addCandidateIfFresh(currentState, path, rule, StepDirection.REVERSE, ac, metric, candidates, seenResults);
                    }
                }
            }
        }
        candidates.sort(Comparator.comparingInt(Candidate::distance));
        return candidates.stream().limit(3).map(c -> new HintSuggestion(c.ruleId(), c.path(), c.result(), c.rationale())).toList();
    }

    @Override
    public Optional<ReachabilityReport> verifyReachability(MathExercise exercise) {
        boolean ac = exercise.isAcNormalization();
        GoalMode mode = exercise.getGoalMode() == null ? GoalMode.TRANSFORMATION : exercise.getGoalMode();
        ReductionStrategy strategy = forwardOnlyStrategy();
        if (mode == GoalMode.EQUATION) {
            MathNode goal = exercise.getGoalExpression();
            if (goal == null) {
                return Optional.empty();
            }
            ReductionStrategy.ReductionResult reduced = strategy.reduceToFixpoint(goal);
            int initialDist = sideDistance(goal, ac);
            int finalDist = sideDistance(reduced.tree(), ac);
            return Optional.of(new ReachabilityReport(finalDist == 0, initialDist, finalDist, reduced.tree()));
        }
        MathNode source = exercise.getSourceExpression();
        MathNode target = exercise.getTargetExpression();
        if (source == null || target == null) {
            return Optional.empty();
        }
        ReductionStrategy.ReductionResult reduced = strategy.reduceToFixpoint(source);
        int initialDist = comparableDistance(source, target, ac);
        int finalDist = comparableDistance(reduced.tree(), target, ac);
        return Optional.of(new ReachabilityReport(finalDist == 0, initialDist, finalDist, reduced.tree()));
    }

    /** Returns the progress metric to rank hint candidates by, or {@code null} if no goal is configured. */
    private ToIntFunction<MathNode> progressMetricFor(MathExercise exercise, boolean ac) {
        GoalMode mode = exercise.getGoalMode() == null ? GoalMode.TRANSFORMATION : exercise.getGoalMode();
        if (mode == GoalMode.EQUATION) {
            if (exercise.getGoalExpression() == null) {
                return null;
            }
            return tree -> sideDistance(tree, ac);
        }
        MathNode target = exercise.getTargetExpression();
        if (target == null) {
            return null;
        }
        return tree -> comparableDistance(tree, target, ac);
    }

    private void addCandidateIfFresh(MathNode tree, List<Integer> path, RewriteRule rule, StepDirection direction, boolean ac, ToIntFunction<MathNode> metric, List<Candidate> out,
            Set<MathNode> seen) {
        Optional<MathNode> result = applyRule(tree, path, rule, direction);
        if (result.isEmpty()) {
            return;
        }
        MathNode rewritten = result.get();
        if (MathNodes.equalsAC(rewritten, tree, ac)) {
            return; // no-op
        }
        MathNode key = ac ? MathNodes.normalizeAC(rewritten) : rewritten;
        if (!seen.add(key)) {
            return; // dedup
        }
        int dist = metric.applyAsInt(rewritten);
        String rationale = direction == StepDirection.REVERSE ? rule.name() + " (reverse)" : rule.name();
        out.add(new Candidate(rule.id(), path, rewritten, rationale, dist));
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

    /** Builds a ReductionStrategy with the registry's forward-only rules and this grader's rule applier. */
    private ReductionStrategy forwardOnlyStrategy() {
        List<RewriteRule> forwardOnly = new ArrayList<>();
        for (var block : blockRegistry.getAllBlocks()) {
            for (RewriteRule rule : blockRegistry.getNormalizedRulesFor(block)) {
                if (rule.direction() == RuleDirection.FORWARD_ONLY) {
                    forwardOnly.add(rule);
                }
            }
        }
        return new ReductionStrategy(forwardOnly, this::applyRule);
    }

    /** Distance between the two sides of an {@code equality} node, optionally AC-normalised first. */
    private int sideDistance(MathNode equality, boolean ac) {
        if (equality == null || !"equality".equals(equality.getType()) || equality.getSlots() == null) {
            return 0;
        }
        List<MathNode> left = equality.getSlots().get("left");
        List<MathNode> right = equality.getSlots().get("right");
        if (left == null || right == null || left.isEmpty() || right.isEmpty()) {
            return 0;
        }
        return comparableDistance(left.get(0), right.get(0), ac);
    }

    private record ReplayResult(MathNode current, int validSteps) {
    }

    private record Candidate(String ruleId, List<Integer> path, MathNode result, String rationale, int distance) {
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
