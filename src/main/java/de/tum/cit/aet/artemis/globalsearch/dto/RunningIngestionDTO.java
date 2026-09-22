package de.tum.cit.aet.artemis.globalsearch.dto;

import java.time.ZonedDateTime;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.lecture.domain.ProcessingPhase;

/**
 * One lecture unit a worker is currently working on.
 * <p>
 * {@code lastProgressAt} and {@code lastHeartbeatAt} are deliberately both reported: a run whose
 * heartbeat is fresh but whose progress is frozen is wedged rather than slow, and that distinction is
 * the whole point of showing this table to an operator.
 *
 * @param lectureUnitId   the unit being worked on
 * @param lectureUnitName the unit's name, for a feed a human can read
 * @param courseId        the course it belongs to
 * @param courseTitle     the course's title
 * @param phase           which phase it is in
 * @param stage           the pipeline stage the worker last reported, e.g. {@code vision}
 * @param stageProgress   how far into that stage it is, or null when the stage reports no counter
 * @param stageTotal      the stage's total work items, or null when unknown
 * @param startedAt       when this run started
 * @param lastProgressAt  when the progress counter last advanced
 * @param lastHeartbeatAt when the worker last renewed its lease
 * @param lockedBy        the boot id of the worker holding the lease
 * @param retryCount      how many attempts this unit has already burned
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record RunningIngestionDTO(long lectureUnitId, @Nullable String lectureUnitName, @Nullable Long courseId, @Nullable String courseTitle, ProcessingPhase phase,
        @Nullable String stage, @Nullable Integer stageProgress, @Nullable Integer stageTotal, @Nullable ZonedDateTime startedAt, @Nullable ZonedDateTime lastProgressAt,
        @Nullable ZonedDateTime lastHeartbeatAt, @Nullable String lockedBy, int retryCount) {
}
