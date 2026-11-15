package de.tum.cit.aet.artemis.hyperion.dto;

import java.util.List;

import jakarta.validation.Valid;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO describing the questions one draw took from a course's quiz question pool.
 *
 * @param questions                     the drawn questions in draw order, fewer than requested when the pool held fewer eligible ones
 * @param requested                     how many questions the draw asked for
 * @param competencyIdsWithoutQuestions requested competencies for which the pool holds no eligible question
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(description = "The questions one draw took from the quiz question pool")
public record QuizQuestionPoolSelectionDTO(@Valid @Schema(description = "The drawn questions in draw order") List<QuizQuestionPoolItemDTO> questions,
        @Schema(description = "How many questions the draw asked for") int requested,
        @Schema(description = "Requested competencies for which the pool holds no eligible question") List<Long> competencyIdsWithoutQuestions) {
}
