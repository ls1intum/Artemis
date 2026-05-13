package de.tum.cit.aet.artemis.proof.domain;

import java.util.List;
import java.util.Map;

/**
 * Static factory helpers for constructing {@link MathNode} instances used in rewrite rule definitions.
 */
public final class MathNodes {

    private MathNodes() {
    }

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
}
