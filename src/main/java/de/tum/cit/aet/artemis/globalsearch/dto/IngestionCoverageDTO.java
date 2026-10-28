package de.tum.cit.aet.artemis.globalsearch.dto;

import java.time.ZonedDateTime;
import java.util.List;

import org.jspecify.annotations.Nullable;

import de.tum.cit.aet.artemis.globalsearch.domain.IngestionCoverageStatus;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One course's index coverage for the dashboard matrix. Both the stored cross-course view and the live per-page view
 * return this shape, so the client consumes one type: the stored view maps it from the {@code ingestion_coverage}
 * projection, the live view computes it on the fly for the visible page. {@code computedAt} is the projection's stored
 * timestamp for the stored view, or the request time for the live view.
 * Every component remains present in JSON, including null metadata, empty strings, empty type-count lists, false and zero.
 *
 * @param courseId         the id of the course
 * @param courseTitle      the course title, if available
 * @param releaseDate      the course start/release date (nullable)
 * @param active           whether the course is currently active
 * @param semester         the course semester (nullable)
 * @param status           the overall coverage status
 * @param coverageGapScore the precomputed worst-first severity (higher is worse; the total missing count)
 * @param computedAt       when this coverage was computed
 * @param lastIngestedAt   the most recent index write across the course's objects, or {@code null} if nothing is indexed
 * @param typeCounts       the per-type expected/indexed/missing/orphaned counts
 */
public record IngestionCoverageDTO(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) long courseId,
        @Nullable @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String courseTitle,
        @Nullable @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) ZonedDateTime releaseDate,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean active, @Nullable @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String semester,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) IngestionCoverageStatus status, @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int coverageGapScore,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ZonedDateTime computedAt,
        @Nullable @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) ZonedDateTime lastIngestedAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<IngestionTypeCountDTO> typeCounts) {
}
