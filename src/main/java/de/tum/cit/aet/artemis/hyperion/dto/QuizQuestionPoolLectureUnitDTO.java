package de.tum.cit.aet.artemis.hyperion.dto;

import java.time.ZonedDateTime;

import jakarta.validation.constraints.NotNull;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO describing one lecture unit the quiz question pool can generate from, for one of the competencies it is linked to.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(description = "One lecture unit of a competency in the quiz question pool")
public record QuizQuestionPoolLectureUnitDTO(@NotNull @Schema(description = "Id of the competency the unit is linked to") Long competencyId,
        @Schema(description = "Title of the competency") @Nullable String competencyTitle, @NotNull @Schema(description = "Id of the lecture unit") Long lectureUnitId,
        @Schema(description = "Name of the lecture unit") @Nullable String lectureUnitName, @Schema(description = "Title of the unit's lecture") @Nullable String lectureTitle,
        @Schema(description = "Whether an instructor excluded the unit from generation") boolean excluded,
        @Schema(description = "Whether approved questions of the unit were generated from material that has changed since") boolean materialChanged,
        @Schema(description = "Questions the pool holds for the unit") int questionCount, @Schema(description = "Approved questions of the unit") int approvedCount,
        @Schema(description = "When the unit was last built, absent before the first build") @Nullable ZonedDateTime lastBuiltAt) {
}
