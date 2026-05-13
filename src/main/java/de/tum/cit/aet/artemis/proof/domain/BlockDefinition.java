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
}
