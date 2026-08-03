package de.tum.cit.aet.artemis.iris.dto;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisActivityDTO;

/**
 * A completed lecture ingestion kept for the admin dashboard's recent-history view: whether it finished or failed, how
 * long it took, the full per-step timeline with each step's duration, and - for failures - which step it failed at and
 * why.
 *
 * @param jobId           the Pyris job id
 * @param courseId        the course the lecture unit belongs to
 * @param lectureId       the lecture the unit belongs to
 * @param lectureUnitId   the lecture unit that was ingested
 * @param lectureUnitName the display name of the lecture unit, or null when it could not be resolved
 * @param lectureName     the title of the lecture the unit belongs to, or null when it could not be resolved
 * @param outcome         the terminal outcome, {@code FINISHED} or {@code FAILED}
 * @param startedAt       the ISO-8601 run start time, or null when it was not reported
 * @param finishedAt      the ISO-8601 time the run reached its terminal state
 * @param totalMillis     the total run duration in milliseconds, or null when the start time was unknown
 * @param activities      the full per-step timeline with each step's state and duration
 * @param failedStepName  for a failed run, the name of the step it failed at, or null
 * @param errorMessage    for a failed run, the reported error message, or null
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RecentIngestionDTO(String jobId, long courseId, long lectureId, long lectureUnitId, @Nullable String lectureUnitName, @Nullable String lectureName, String outcome,
        @Nullable String startedAt, String finishedAt, @Nullable Long totalMillis, @Nullable List<PyrisActivityDTO> activities, @Nullable String failedStepName,
        @Nullable String errorMessage) {
}
