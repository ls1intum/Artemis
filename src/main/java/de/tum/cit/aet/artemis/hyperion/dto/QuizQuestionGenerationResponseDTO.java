package de.tum.cit.aet.artemis.hyperion.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO for AI quiz question generation responses.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(description = "Response containing generated quiz questions")
public record QuizQuestionGenerationResponseDTO(@NotEmpty @Schema(description = "Generated quiz questions") List<@Valid GeneratedQuizQuestionDTO> questions) {
}
