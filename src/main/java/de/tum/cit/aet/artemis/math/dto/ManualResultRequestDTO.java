package de.tum.cit.aet.artemis.math.dto;

/**
 * Request body for {@code PUT /api/math/math-submissions/{submissionId}/manual-result}.
 *
 * @param score the tutor-supplied manual score in {@code [0, 100]}
 */
public record ManualResultRequestDTO(double score) {
}
