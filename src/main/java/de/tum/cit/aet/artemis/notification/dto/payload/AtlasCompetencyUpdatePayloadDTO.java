package de.tum.cit.aet.artemis.notification.dto.payload;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The payload of the Atlas competency update notification, sent after an automatic competency orchestration run.
 * <p>
 * Only scalars and strings, because each component is stored as one parameter row: the applied changes travel as one
 * markdown list rather than as a collection. The stored copy of {@code changesMarkdown} is truncated with every other
 * long parameter, which is harmless because only the e-mail renders it and the e-mail receives the full value.
 *
 * @param outcome         how the run ended: {@code COMPLETED}, {@code PARTIAL} or {@code FAILED}
 * @param exerciseCount   the number of changed learning objects (exercises and lecture units) the run processed
 * @param appliedCount    the number of changes the run applied
 * @param createdCount    the number of competencies the run created
 * @param editedCount     the number of competencies the run edited
 * @param deletedCount    the number of competencies the run deleted
 * @param assignedCount   the number of links the run created or reweighted
 * @param unassignedCount the number of links the run removed
 * @param changesMarkdown the applied changes as a markdown list, empty when the run applied none
 * @param omittedCount    the number of applied changes left out of {@code changesMarkdown} to bound the e-mail
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record AtlasCompetencyUpdatePayloadDTO(@Nullable String outcome, @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int exerciseCount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int appliedCount, @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int createdCount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int editedCount, @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int deletedCount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int assignedCount, @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int unassignedCount,
        @Nullable String changesMarkdown, @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int omittedCount) implements CourseNotificationPayloadDTO {
}
