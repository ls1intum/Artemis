package de.tum.cit.aet.artemis.hyperion.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO for a quiz question refinement request.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(description = "Request to refine an existing quiz question using a user prompt")
public record QuizQuestionRefinementRequestDTO(@NotNull @Valid @Schema(description = "The existing quiz question to refine") GeneratedQuizQuestionDTO question,
        @NotBlank @Size(max = 2000) @Schema(description = "User instructions describing how the question should be changed") String refinementPrompt) {
}
