package de.tum.cit.aet.artemis.hyperion.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO describing the pool a build would plan with the course's stored settings.
 *
 * @param planned the questions the pool holds once a build with the stored settings is complete
 * @param missing the questions a build with the stored settings would add to the pool
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(description = "The pool a build would plan with the course's stored settings")
public record QuizQuestionPoolPlanDTO(
        @Schema(description = "Questions the pool holds once a build with the stored settings is complete", requiredMode = Schema.RequiredMode.REQUIRED) int planned,
        @Schema(description = "Questions a build with the stored settings would add to the pool", requiredMode = Schema.RequiredMode.REQUIRED) int missing) {
}
