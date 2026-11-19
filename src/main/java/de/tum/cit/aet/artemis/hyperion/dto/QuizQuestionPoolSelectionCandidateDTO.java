package de.tum.cit.aet.artemis.hyperion.dto;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.hyperion.domain.QuizQuestionDifficulty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO counting the questions a draw may take from one cell of a course's quiz question pool: judged questions that are
 * approved, or that await a decision after the filter accepted them without flagging them, generated from the lecture unit's current material.
 *
 * @param competencyId  the competency of the cell
 * @param lectureUnitId the lecture unit of the cell
 * @param questionType  the question type of the cell
 * @param difficulty    the difficulty of the cell
 * @param count         the questions of the cell a draw may take
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(description = "How many questions a draw may take from one cell of the quiz question pool")
public record QuizQuestionPoolSelectionCandidateDTO(@Schema(description = "Id of the competency") long competencyId,
        @Schema(description = "Id of the lecture unit") long lectureUnitId, @NotNull @Schema(description = "Question type") QuizQuestionGenerationType questionType,
        @NotNull @Schema(description = "Difficulty") QuizQuestionDifficulty difficulty, @Schema(description = "Questions a draw may take") long count) {
}
