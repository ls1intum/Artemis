package de.tum.cit.aet.artemis.hyperion.dto;

import jakarta.validation.constraints.NotBlank;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO describing one generated answer option.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(description = "One generated answer option")
public record GeneratedQuizAnswerOptionDTO(@NotBlank @Schema(description = "Answer option text") String text,
        @Schema(description = "Whether this option is correct") boolean correct) {
}
