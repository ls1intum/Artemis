package de.tum.cit.aet.artemis.lecture.dto;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Vector index state of every lecture unit of one course, as reported by the Pyris ingestion census.
 * The reconciler compares this against the processing state ledger to find units whose index content
 * is missing, stale, or orphaned.
 *
 * @param courseId               the course the census was taken for
 * @param currentPipelineVersion the ingestion pipeline version the reporting Iris currently runs;
 *                                   drives the once-per-version quality re-ingestion rule
 * @param units                  one entry per lecture unit any collection still knows about
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IngestionCensusDTO(long courseId, @Nullable Integer currentPipelineVersion, List<IngestionCensusUnitDTO> units) {
}
