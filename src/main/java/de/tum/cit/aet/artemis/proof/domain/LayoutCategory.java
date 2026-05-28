package de.tum.cit.aet.artemis.proof.domain;

/**
 * Rendering layout category for a {@link BlockDefinition} node type.
 * The frontend uses this to drive generic rendering without hardcoding node type names.
 */
public enum LayoutCategory {
    TERMINAL_NUMBER, TERMINAL_VARIABLE, BINARY_INFIX, FRACTION, PARENTHESES, UNARY_PREFIX
}
