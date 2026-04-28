package de.tum.cit.aet.artemis.hyperion.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO for a quiz question refinement response.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(description = "Response containing the refined quiz question and a summary of the changes made")
public record QuizQuestionRefinementResponseDTO(@NotNull @Valid @Schema(description = "The refined quiz question") GeneratedQuizQuestionDTO question,
        @NotBlank @Schema(description = "Brief explanation of what was changed during refinement") String reasoning) {
}
