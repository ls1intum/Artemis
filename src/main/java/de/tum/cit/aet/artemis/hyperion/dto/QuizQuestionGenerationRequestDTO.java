package de.tum.cit.aet.artemis.hyperion.dto;

import java.util.Set;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO for AI quiz question generation requests.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(description = "Request to generate quiz questions")
public record QuizQuestionGenerationRequestDTO(@NotBlank @Size(max = 500) @Schema(description = "Main topic for the generated quiz") String topic,
        @Size(max = 2000) @Schema(description = "Optional additional instructions") String optionalPrompt,
        @NotNull @Schema(description = "Target language for the generated quiz") QuizQuestionGenerationLanguage language,
        @NotEmpty @Schema(description = "Question types to include") Set<@NotNull QuizQuestionGenerationType> questionTypes,
        @NotNull @Min(1) @Max(10) @Schema(description = "Number of questions to generate") Integer numberOfQuestions,
        @NotNull @Min(0) @Max(100) @Schema(description = "Difficulty as a value from 0 to 100") Integer difficulty) {
}
