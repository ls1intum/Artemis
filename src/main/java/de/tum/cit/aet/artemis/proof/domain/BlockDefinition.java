package de.tum.cit.aet.artemis.proof.domain;

import java.util.List;

/**
 * A block type available in the proof editor palette.
 * Implementations are Spring {@code @Component} beans collected by {@link de.tum.cit.aet.artemis.proof.service.BlockRegistry}.
 * New block types are added by implementing this interface — no schema changes are required.
 */
public interface BlockDefinition {

    /** Node type string used in {@link MathNode#getType()}. */
    String getType();

    /** Grouping category for display in the palette (e.g., {@code "arithmetic"}). */
    String getCategory();

    /** Human-readable display name. */
    String getLabel();

    /** LaTeX string representing this block in the palette. */
    String getPaletteLatex();

    /** Ordered list of named slot names that child nodes can be placed in. */
    List<String> getSlots();

    /** The rewrite rules owned by this block type. */
    List<RewriteRule> getRules();

    /**
     * Operator precedence for auto-parenthesization. Higher value binds tighter.
     * Terminals should return a high value (e.g., 100); unknown types default to 0.
     */
    default int getPrecedence() {
        return 0;
    }

    /** Associativity used to determine when the right child needs parentheses. */
    default Associativity getAssociativity() {
        return Associativity.NONE;
    }

    /**
     * Rendering layout category understood by the frontend.
     * All implementations must declare this explicitly so new node types
     * are never silently assigned the wrong rendering.
     */
    LayoutCategory getLayoutCategory();

    /**
     * Unicode symbol displayed in the interactive editor for {@code BINARY_INFIX} nodes.
     * Returns {@code null} for non-infix layout categories.
     */
    default String getDisplaySymbol() {
        return null;
    }

    /**
     * LaTeX symbol emitted in math output for {@code BINARY_INFIX} nodes (e.g., {@code "\\cdot"}).
     * Returns {@code null} for non-infix layout categories.
     */
    default String getLatexSymbol() {
        return null;
    }
}
