package de.tum.cit.aet.artemis.hyperion.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A draft title the instructor may accept or overwrite before generation starts.
 *
 * @param title a title Artemis accepts as-is: valid characters, at least three of them, and not yet used by a programming exercise in the course
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(description = "A draft exercise title that is valid and unique in the course")
public record ExerciseGenerationTitleSuggestionResponseDTO(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String title) {
}
