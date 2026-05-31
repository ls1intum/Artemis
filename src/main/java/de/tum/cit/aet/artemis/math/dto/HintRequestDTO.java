package de.tum.cit.aet.artemis.math.dto;

import de.tum.cit.aet.artemis.math.domain.MathNode;

/**
 * Request body for the hint endpoint. Carries the student's current math state.
 *
 * @param currentExpression the tree the student is currently looking at
 */
public record HintRequestDTO(MathNode currentExpression) {
}
