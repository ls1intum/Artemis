package de.tum.cit.aet.artemis.assessment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The level position a rating selects, used to recalculate rubric scores without loading the ratings as entities.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PresentationRubricRatingLevelDTO(long instanceId, long criterionId, int levelPosition) {
}
