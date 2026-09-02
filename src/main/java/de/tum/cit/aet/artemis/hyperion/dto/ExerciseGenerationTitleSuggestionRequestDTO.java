package de.tum.cit.aet.artemis.hyperion.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Request for a draft title derived from the brief an instructor is about to generate an exercise from.
 *
 * @param prompt the instructor's brief, bounded by the same length as {@link ExerciseGenerationRequestDTO#prompt()} because it is the same text
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(description = "Request for a draft exercise title derived from an instructor's generation brief")
public record ExerciseGenerationTitleSuggestionRequestDTO(
        @NotBlank @Size(max = 8000) @Schema(description = "The instructor's brief for the exercise to be generated") String prompt) {
}
