package de.tum.cit.aet.artemis.proof.domain;

/**
 * Operator associativity used for precedence-based parenthesization in the proof editor.
 * LEFT-associative operators apply the stricter {@code <=} rule on their right child.
 */
public enum Associativity {
    LEFT, NONE
}
