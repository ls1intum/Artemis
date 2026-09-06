package de.tum.cit.aet.artemis.globalsearch.dto;

import java.time.ZonedDateTime;
import java.util.List;

import de.tum.cit.aet.artemis.globalsearch.domain.IngestionCoverageStatus;

/**
 * One course's index coverage for the dashboard matrix. Both the stored cross-course view and the live per-page view
 * return this shape, so the frontend consumes one type: the stored view maps it from the {@code ingestion_coverage}
 * projection, the live view computes it on the fly for the visible page. {@code computedAt} is the projection's stored
 * timestamp for the stored view, or the request time for the live view.
 *
 * @param courseId         the id of the course
 * @param courseTitle      the course title
 * @param releaseDate      the course start/release date (nullable)
 * @param active           whether the course is currently active
 * @param semester         the course semester (nullable)
 * @param status           the overall coverage status
 * @param coverageGapScore the precomputed worst-first severity (higher is worse; the total missing count)
 * @param computedAt       when this coverage was computed
 * @param lastIngestedAt   the most recent index write across the course's objects, or {@code null} if nothing is indexed
 * @param typeCounts       the per-type expected/indexed/missing/orphaned counts
 */
public record IngestionCoverageDTO(long courseId, String courseTitle, ZonedDateTime releaseDate, boolean active, String semester, IngestionCoverageStatus status,
        int coverageGapScore, ZonedDateTime computedAt, ZonedDateTime lastIngestedAt, List<IngestionTypeCountDTO> typeCounts) {
}
