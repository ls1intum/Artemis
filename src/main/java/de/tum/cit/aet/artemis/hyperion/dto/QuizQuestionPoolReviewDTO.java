package de.tum.cit.aet.artemis.hyperion.dto;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.hyperion.domain.QuizQuestionReviewState;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO carrying the instructor's review decision for one pool question.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(description = "The review decision for one quiz question pool item")
public record QuizQuestionPoolReviewDTO(@NotNull @Schema(description = "The review decision") QuizQuestionReviewState reviewState) {
}
