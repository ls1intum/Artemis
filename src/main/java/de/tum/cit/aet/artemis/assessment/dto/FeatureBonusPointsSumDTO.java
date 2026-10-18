package de.tum.cit.aet.artemis.assessment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The sum of the feature bonus points awarded to one participation or one student, as a query projects it.
 *
 * @param id     the id of the participation or student
 * @param points the sum of the awarded points
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record FeatureBonusPointsSumDTO(Long id, Double points) {
}
