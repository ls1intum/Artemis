package de.tum.cit.aet.artemis.hyperion.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO for a bulk quiz question refinement request.
 * Applies the same refinement prompt to all provided questions.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(description = "Request to refine multiple quiz questions at once using a single user prompt")
public record QuizQuestionBulkRefinementRequestDTO(@NotEmpty @Valid @Size(max = 50) @Schema(description = "The quiz questions to refine") List<GeneratedQuizQuestionDTO> questions,
        @NotBlank @Size(max = 2000) @Schema(description = "User instructions describing how all questions should be changed") String refinementPrompt) {
}
