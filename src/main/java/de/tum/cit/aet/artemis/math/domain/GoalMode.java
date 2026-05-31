package de.tum.cit.aet.artemis.math.domain;

/**
 * How the goal of a math exercise is encoded.
 * <p>
 * {@link #TRANSFORMATION} is the legacy mode: source and target are two separate trees and the
 * student transforms source into target step by step. {@link #EQUATION} encodes the goal as a single
 * tree — typically an {@code equality(LHS, RHS)} — and completion is reduction to a tautology
 * (a tree where both sides of the equality are structurally equal).
 */
public enum GoalMode {

    /** Legacy shape: source-to-target derivation. */
    TRANSFORMATION,

    /** Single goal tree (typically an equality); math is complete when {@link MathNodes#isTautology(MathNode)} holds. */
    EQUATION
}
