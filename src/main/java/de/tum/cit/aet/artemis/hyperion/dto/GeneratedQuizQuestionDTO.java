package de.tum.cit.aet.artemis.hyperion.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO describing one generated quiz question.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(description = "One generated quiz question")
public record GeneratedQuizQuestionDTO(@NotNull @Schema(description = "Question type") QuizQuestionGenerationType type,
        @NotBlank @Size(max = 500) @Schema(description = "Question title") String title, @NotBlank @Size(max = 10000) @Schema(description = "Question text") String questionText,
        @NotEmpty @Schema(description = "Answer options") List<@Valid GeneratedQuizAnswerOptionDTO> options,
        @Size(max = 2000) @Schema(description = "Optional hint for the overall question") String hint,
        @Size(max = 2000) @Schema(description = "Optional explanation for the overall question") String explanation) {
}
