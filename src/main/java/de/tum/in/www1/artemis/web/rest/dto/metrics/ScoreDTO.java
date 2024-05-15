package de.tum.in.www1.artemis.web.rest.dto.metrics;

/**
 * DTO for the score for an exercise.
 *
 * @param exerciseId the id of the exercise
 * @param score      the score of the exercise
 */
public record ScoreDTO(long exerciseId, Double score) {
}
