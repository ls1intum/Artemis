package de.tum.cit.aet.artemis.exercise.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * One category of one exercise, projected separately from the exercise details to avoid repeating the full exercise row
 * once per category.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExerciseCategoryDTO(long exerciseId, String category) {
}
