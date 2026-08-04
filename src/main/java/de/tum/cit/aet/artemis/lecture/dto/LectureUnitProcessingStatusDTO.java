package de.tum.cit.aet.artemis.lecture.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The authoritative processing status of a lecture unit, taken from the {@code LectureUnitProcessingState} that the
 * lecture-unit management page reads. It is exposed across the module boundary so the admin ingestion dashboard can
 * derive its in-flight view from the same source as the unit page instead of a separate webhook-fed one, which is what
 * lets the two never disagree.
 *
 * @param lectureUnitId     the lecture unit id
 * @param lectureUnitName   the unit display name, or null when it could not be resolved
 * @param lectureId         the parent lecture id
 * @param lectureName       the parent lecture title, or null when it could not be resolved
 * @param courseId          the course id
 * @param phase             the processing phase name ({@code IDLE}, {@code TRANSCRIBING}, {@code INGESTING},
 *                              {@code DONE}, {@code FAILED})
 * @param startedAt         the ISO-8601 time the current attempt started, or null when not started
 * @param lastUpdatedAt     the ISO-8601 time of the last state update (last Iris callback while processing), or null
 * @param retryCount        how many attempts have failed for this unit so far
 * @param ingestionJobToken the Pyris job token of the current attempt (matches the webhook job id), or null
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Authoritative processing state of a lecture unit for the ingestion dashboard")
public record LectureUnitProcessingStatusDTO(long lectureUnitId, @Nullable String lectureUnitName, long lectureId, @Nullable String lectureName, long courseId, String phase,
        @Nullable String startedAt, @Nullable String lastUpdatedAt, int retryCount, @Nullable String ingestionJobToken) {
}
