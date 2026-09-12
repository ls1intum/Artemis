package de.tum.cit.aet.artemis.lecture.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Aggregated vector index state of one lecture unit, as reported by the Pyris ingestion census.
 * <p>
 * A unit appears in the census when ANY collection still holds rows for it, so orphaned content
 * whose unit row is gone stays visible. The {@code contentFingerprint} is the stamp Pyris wrote
 * verbatim from the ingestion request; equality with the ledger's fingerprint is what proves the
 * index holds content derived from the unit's current sources.
 *
 * @param lectureId          the lecture the unit row claims to belong to; {@code null} when no unit row exists
 * @param lectureUnitId      the lecture unit id the rows are keyed by
 * @param contentFingerprint the fingerprint stamped into the unit row; {@code null} for pre-fingerprint rows
 * @param unitRowCount       number of unit rows (exactly 1 for a healthy unit)
 * @param expectedChunkCount total chunk count the last certified run recorded; {@code null} for pre-ledger rows
 * @param pipelineVersion    ingestion pipeline version stamped into the unit row; {@code null} for pre-ledger rows
 * @param qualityScore       deterministic quality score of the ingested content (0..1); {@code null} for pre-ledger rows
 * @param chunkCount         number of page chunk rows
 * @param chunkPageMin       smallest page number among the chunks; {@code null} without chunks
 * @param chunkPageMax       largest page number among the chunks; {@code null} without chunks
 * @param chunkVersionMin    smallest attachment version among the chunks; {@code null} without chunks
 * @param chunkVersionMax    largest attachment version among the chunks; {@code null} without chunks
 * @param transcriptionCount number of transcription rows
 * @param segmentCount       number of segment summary rows
 * @param segmentPageMin     smallest page number among the segments; {@code null} without segments
 * @param segmentPageMax     largest page number among the segments; {@code null} without segments
 * @param missingPageCount   interior page-coverage holes: pages in 1..max with no real chunk; 0 for contiguous coverage
 * @param nullDisplayCount   real page chunks whose display page number was never resolved (legacy null)
 * @param courseLanguage     resolved language the unit was ingested under, from the unit row; {@code null} for pre-ledger rows
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IngestionCensusUnitDTO(@Nullable Long lectureId, long lectureUnitId, @Nullable String contentFingerprint, int unitRowCount, @Nullable Integer expectedChunkCount,
        @Nullable Integer pipelineVersion, @Nullable Double qualityScore, int chunkCount, int generationCount, @Nullable Integer chunkPageMin, @Nullable Integer chunkPageMax,
        @Nullable Integer chunkVersionMin, @Nullable Integer chunkVersionMax, int transcriptionCount, int segmentCount, @Nullable Integer segmentPageMin,
        @Nullable Integer segmentPageMax, int missingPageCount, int nullDisplayCount, @Nullable String courseLanguage) {
}
