package de.tum.cit.aet.artemis.iris.dto;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisActivityDTO;

/**
 * A snapshot of one in-flight lecture ingestion for the admin ingestion dashboard: which lecture unit is ingesting,
 * its run state, when it started, when it last reported progress, and the live per-step activity list (each with a
 * name, state, and duration).
 *
 * @param jobId           the Pyris job id
 * @param courseId        the course the lecture unit belongs to
 * @param lectureId       the lecture the unit belongs to
 * @param lectureUnitId   the lecture unit being ingested
 * @param lectureUnitName the display name of the lecture unit, or null when it could not be resolved
 * @param lectureName     the title of the lecture the unit belongs to, or null when it could not be resolved
 * @param runState        the current run state (RUNNING)
 * @param startedAt       the ISO-8601 run start time, or null when not reported
 * @param lastUpdatedAt   the ISO-8601 time of the last status callback, so the dashboard can detect a stalled run
 * @param activities      the live per-step activity snapshot, or null when the run has not reported steps yet
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ActiveIngestionDTO(String jobId, long courseId, long lectureId, long lectureUnitId, @Nullable String lectureUnitName, @Nullable String lectureName, String runState,
        @Nullable String startedAt, @Nullable String lastUpdatedAt, @Nullable List<PyrisActivityDTO> activities) {
}
