package de.tum.cit.aet.artemis.hyperion.dto;

import jakarta.validation.constraints.NotNull;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO naming one competency that has questions in a course's quiz question pool.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(description = "A competency with questions in the quiz question pool")
public record QuizQuestionPoolCompetencyDTO(@NotNull @Schema(description = "Id of the competency") Long id,
        @Schema(description = "Title of the competency") @Nullable String title) {
}
