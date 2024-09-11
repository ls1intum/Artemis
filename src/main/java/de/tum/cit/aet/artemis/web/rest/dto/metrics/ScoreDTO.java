package de.tum.cit.aet.artemis.web.rest.dto.metrics;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO for the score for an exercise.
 *
 * @param exerciseId the id of the exercise
 * @param score      the score of the exercise
 */
@JsonInclude
public record ScoreDTO(long exerciseId, Double score) {
}
