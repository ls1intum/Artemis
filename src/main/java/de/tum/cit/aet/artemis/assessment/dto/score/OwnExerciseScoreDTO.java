package de.tum.cit.aet.artemis.assessment.dto.score;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Own rated score without loading the score, exercise or result entities. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record OwnExerciseScoreDTO(long exerciseId, double score) {
}
