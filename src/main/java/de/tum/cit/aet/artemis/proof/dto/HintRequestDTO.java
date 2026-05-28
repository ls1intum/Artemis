package de.tum.cit.aet.artemis.proof.dto;

import de.tum.cit.aet.artemis.proof.domain.MathNode;

/**
 * Request body for the hint endpoint. Carries the student's current proof state.
 *
 * @param currentExpression the tree the student is currently looking at
 */
public record HintRequestDTO(MathNode currentExpression) {
}
