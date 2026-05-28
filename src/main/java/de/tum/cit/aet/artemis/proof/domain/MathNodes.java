package de.tum.cit.aet.artemis.proof.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Static factory helpers and pure utility operations on {@link MathNode}.
 * Construction helpers ({@link #add}, {@link #mul}, …) are used both in rule definitions
 * and in tests; the utility operations ({@link #normalize}, {@link #assertWildcardFree})
 * are used at the persistence and wire boundaries.
 */
public final class MathNodes {

    private MathNodes() {
    }

    // ----------------------------------------------------------------------
    // Construction helpers
    // ----------------------------------------------------------------------

    public static MathNode wc(String varName) {
        return new MathNode("wildcard", varName, null);
    }

    public static MathNode num(String value) {
        return new MathNode("number", value, null);
    }

    public static MathNode var(String name) {
        return new MathNode("variable", name, null);
    }

    public static MathNode add(MathNode left, MathNode right) {
        return new MathNode("add", null, Map.of("left", List.of(left), "right", List.of(right)));
    }

    public static MathNode sub(MathNode left, MathNode right) {
        return new MathNode("sub", null, Map.of("left", List.of(left), "right", List.of(right)));
    }

    public static MathNode mul(MathNode left, MathNode right) {
        return new MathNode("mul", null, Map.of("left", List.of(left), "right", List.of(right)));
    }

    public static MathNode frac(MathNode numerator, MathNode denominator) {
        return new MathNode("fraction", null, Map.of("numerator", List.of(numerator), "denominator", List.of(denominator)));
    }

    public static MathNode paren(MathNode content) {
        return new MathNode("parentheses", null, Map.of("content", List.of(content)));
    }

    public static MathNode eq(MathNode left, MathNode right) {
        return new MathNode("equality", null, Map.of("left", List.of(left), "right", List.of(right)));
    }

    public static MathNode neg(MathNode inner) {
        return new MathNode("negation", null, Map.of("inner", List.of(inner)));
    }

    // ----------------------------------------------------------------------
    // Utility operations
    // ----------------------------------------------------------------------

    /**
     * Returns a structurally-equivalent tree with terminal values canonicalised:
     * numbers parsed via {@link BigDecimal} and stripped of trailing zeros (so {@code "0.0"}, {@code "00"}, {@code "-0"} all become {@code "0"});
     * variable names trimmed of surrounding whitespace.
     * <p>
     * Non-numeric strings are left unchanged (e.g. a variable named {@code "x"} stays {@code "x"}).
     * Slot ordering is preserved (canonical comparison still uses sorted keys).
     *
     * @param node tree to normalise (may be {@code null})
     * @return a fresh tree with canonical terminal values, or {@code null} if {@code node} was null
     */
    public static MathNode normalize(MathNode node) {
        if (node == null) {
            return null;
        }
        String type = node.getType();
        if ("number".equals(type)) {
            return new MathNode(type, normalizeNumberLiteral(node.getValue()), null);
        }
        if ("variable".equals(type) || "wildcard".equals(type)) {
            String v = node.getValue();
            return new MathNode(type, v == null ? null : v.trim(), null);
        }
        if (node.getSlots() == null || node.getSlots().isEmpty()) {
            return new MathNode(type, node.getValue(), null);
        }
        Map<String, List<MathNode>> newSlots = new TreeMap<>();
        for (Map.Entry<String, List<MathNode>> entry : node.getSlots().entrySet()) {
            List<MathNode> normalised = new ArrayList<>(entry.getValue().size());
            for (MathNode child : entry.getValue()) {
                normalised.add(normalize(child));
            }
            newSlots.put(entry.getKey(), normalised);
        }
        return new MathNode(type, node.getValue(), newSlots);
    }

    /**
     * Returns {@code true} iff the tree is an {@code equality} node whose two sides are structurally equal.
     * Used by the EQUATION-mode grader to decide when a proof is complete.
     *
     * @param node the tree to inspect (may be {@code null})
     * @return {@code true} when the tree is an equality with identical sides; {@code false} otherwise
     */
    public static boolean isTautology(MathNode node) {
        if (node == null || !"equality".equals(node.getType()) || node.getSlots() == null) {
            return false;
        }
        List<MathNode> left = node.getSlots().get("left");
        List<MathNode> right = node.getSlots().get("right");
        if (left == null || right == null || left.isEmpty() || right.isEmpty()) {
            return false;
        }
        return left.get(0).equals(right.get(0));
    }

    /**
     * Returns a canonical AC-normalised form: chains of {@code +} or {@code ·} are flattened, their operands sorted
     * by a stable canonical key, then rebuilt as a left-associative binary tree. The result preserves structural
     * type but collapses associativity-and-commutativity differences so that {@code (a + b) + c} and {@code c + (b + a)}
     * normalise to the same tree.
     * <p>
     * Used exclusively for equality comparisons in the grader when {@code ProofExercise.acNormalization} is set.
     * The persisted form of student submissions and exercise expressions is never replaced by the normalised form.
     *
     * @param node the tree to normalise (may be {@code null})
     * @return an AC-normalised structurally-equivalent tree, or {@code null} for {@code null}
     */
    public static MathNode normalizeAC(MathNode node) {
        if (node == null) {
            return null;
        }
        if (node.getSlots() == null || node.getSlots().isEmpty()) {
            return node;
        }
        String type = node.getType();
        if ("add".equals(type) || "mul".equals(type)) {
            List<MathNode> operands = new ArrayList<>();
            flattenChain(node, type, operands);
            operands.replaceAll(MathNodes::normalizeAC);
            operands.sort(Comparator.comparing(MathNodes::canonicalKey));
            return rebuildLeftAssoc(type, operands);
        }
        Map<String, List<MathNode>> newSlots = new TreeMap<>();
        for (Map.Entry<String, List<MathNode>> entry : node.getSlots().entrySet()) {
            List<MathNode> normalised = new ArrayList<>(entry.getValue().size());
            for (MathNode child : entry.getValue()) {
                normalised.add(normalizeAC(child));
            }
            newSlots.put(entry.getKey(), normalised);
        }
        return new MathNode(type, node.getValue(), newSlots);
    }

    /**
     * Equality with optional AC normalisation. When {@code ac} is false, this is plain {@link Objects#equals(Object, Object)};
     * when true, both trees are AC-normalised before comparison.
     *
     * @param a  first tree (may be {@code null})
     * @param b  second tree (may be {@code null})
     * @param ac whether to compare modulo associativity / commutativity of {@code +} and {@code ·}
     * @return whether the trees are equal under the chosen semantics
     */
    public static boolean equalsAC(MathNode a, MathNode b, boolean ac) {
        return ac ? Objects.equals(normalizeAC(a), normalizeAC(b)) : Objects.equals(a, b);
    }

    private static void flattenChain(MathNode node, String type, List<MathNode> out) {
        if (type.equals(node.getType()) && node.getSlots() != null) {
            // Visit slots in canonical (alphabetical) order so the input shape doesn't leak into the output ordering.
            for (Map.Entry<String, List<MathNode>> entry : new TreeMap<>(node.getSlots()).entrySet()) {
                for (MathNode child : entry.getValue()) {
                    flattenChain(child, type, out);
                }
            }
        }
        else {
            out.add(node);
        }
    }

    private static MathNode rebuildLeftAssoc(String type, List<MathNode> operands) {
        if (operands.isEmpty()) {
            return new MathNode(type, null, null);
        }
        MathNode acc = operands.get(0);
        for (int i = 1; i < operands.size(); i++) {
            acc = new MathNode(type, null, Map.of("left", List.of(acc), "right", List.of(operands.get(i))));
        }
        return acc;
    }

    private static String canonicalKey(MathNode node) {
        StringBuilder sb = new StringBuilder();
        appendCanonicalKey(node, sb);
        return sb.toString();
    }

    private static void appendCanonicalKey(MathNode node, StringBuilder sb) {
        if (node == null) {
            sb.append("∅");
            return;
        }
        sb.append('{').append(node.getType()).append(':');
        if (node.getValue() != null) {
            sb.append(node.getValue());
        }
        if (node.getSlots() != null) {
            for (Map.Entry<String, List<MathNode>> entry : new TreeMap<>(node.getSlots()).entrySet()) {
                sb.append('|').append(entry.getKey()).append('=');
                for (MathNode child : entry.getValue()) {
                    appendCanonicalKey(child, sb);
                }
            }
        }
        sb.append('}');
    }

    /**
     * Throws {@link IllegalArgumentException} if any node in the tree has type {@code "wildcard"}.
     * Wildcards are a metasyntactic construct that may appear only in rule definitions, never in
     * submitted or instructor-authored expressions. The grading engine treats wildcards as
     * free metavariables, so a wildcard in submission data would let a student bypass equality checks.
     *
     * @param node tree to validate (a {@code null} tree is accepted as wildcard-free)
     */
    public static void assertWildcardFree(MathNode node) {
        if (node == null) {
            return;
        }
        if ("wildcard".equals(node.getType())) {
            throw new IllegalArgumentException("Wildcard nodes are not allowed in submissions or exercise definitions");
        }
        if (node.getSlots() != null) {
            for (List<MathNode> children : node.getSlots().values()) {
                if (children == null) {
                    continue;
                }
                for (MathNode child : children) {
                    assertWildcardFree(child);
                }
            }
        }
    }

    private static String normalizeNumberLiteral(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        try {
            BigDecimal bd = new BigDecimal(value.trim()).stripTrailingZeros();
            // BigDecimal.stripTrailingZeros() on "0" leaves scale negative; setScale(0) makes toPlainString sensible.
            if (bd.signum() == 0) {
                return "0";
            }
            if (bd.scale() < 0) {
                bd = bd.setScale(0);
            }
            return bd.toPlainString();
        }
        catch (NumberFormatException e) {
            return value;
        }
    }
}
