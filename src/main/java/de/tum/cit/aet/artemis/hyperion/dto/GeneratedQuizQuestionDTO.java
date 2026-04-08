package de.tum.cit.aet.artemis.hyperion.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO describing one generated quiz question.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(description = "One generated quiz question")
public record GeneratedQuizQuestionDTO(@NotNull @Schema(description = "Question type") QuizQuestionGenerationType type,
        @NotBlank @Schema(description = "Question title") String title, @NotBlank @Schema(description = "Question text") String questionText,
        @NotEmpty @Schema(description = "Answer options") List<@Valid GeneratedQuizAnswerOptionDTO> options) {
}
