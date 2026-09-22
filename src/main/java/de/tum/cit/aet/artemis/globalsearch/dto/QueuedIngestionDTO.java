package de.tum.cit.aet.artemis.globalsearch.dto;

import java.time.ZonedDateTime;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * One lecture unit waiting to be claimed, in dispatch order.
 *
 * @param lectureUnitId    the waiting unit
 * @param lectureUnitName  the unit's name
 * @param courseId         the course it belongs to
 * @param courseTitle      the course's title
 * @param dispatchPriority 0 for fresh work, higher for backfill and reconcile work
 * @param retryCount       how many attempts this unit has already burned
 * @param retryEligibleAt  when a failed unit becomes claimable again, or null when it is not a retry
 * @param forceReingest    whether this unit is queued for a forced re-ingestion (quality or drift)
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record QueuedIngestionDTO(long lectureUnitId, @Nullable String lectureUnitName, @Nullable Long courseId, @Nullable String courseTitle, int dispatchPriority, int retryCount,
        @Nullable ZonedDateTime retryEligibleAt, boolean forceReingest) {
}
