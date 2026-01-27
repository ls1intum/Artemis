package de.tum.cit.aet.artemis.hyperion.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO for a learning goal item.
 *
 * @param skill         The skill associated with the learning goal
 * @param taxonomyLevel The taxonomy level of the learning goal (e.g. REMEMBER, UNDERSTAND)
 * @param confidence    The confidence score of the inference (0.0 to 1.0)
 * @param explanation   The explanation for the inference
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record LearningGoalItemDTO(String skill, String taxonomyLevel, Double confidence, String explanation) {
}
