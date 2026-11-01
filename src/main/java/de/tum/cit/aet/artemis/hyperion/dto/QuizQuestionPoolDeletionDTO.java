package de.tum.cit.aet.artemis.hyperion.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO reporting how many quiz question pool items a bulk deletion removed.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(description = "The result of deleting quiz question pool items")
public record QuizQuestionPoolDeletionDTO(@Schema(description = "How many items were deleted", requiredMode = Schema.RequiredMode.REQUIRED) long deleted) {
}
