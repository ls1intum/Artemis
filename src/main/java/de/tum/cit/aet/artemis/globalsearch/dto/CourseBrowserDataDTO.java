package de.tum.cit.aet.artemis.globalsearch.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Everything the content browser needs to open a course, in one response. Empty lists remain present in JSON.
 * <p>
 * These are served together rather than from an endpoint each because they are all derived from the same two
 * id-sets: what the database expects indexed, and what the index holds. Fetched separately, each request reloaded those
 * sets, so opening one course paid for the same reads three times over.
 *
 * @param entities        the {@code SearchableEntities} rows stored for the course
 * @param contentPresence which lecture units hold content in each Iris collection
 * @param missingEntities the entities the database expects that the index does not hold, named
 * @param contentGaps     the lecture units whose slide or transcript content was never ingested, named
 * @param typeCounts      the per-type counts for this course, diffed from the same sets as the two gap lists, so the
 *                            browser never pairs its live gaps with the coverage matrix's separately computed row
 */
public record CourseBrowserDataDTO(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<IndexedEntityDTO> entities,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<IndexedContentPresenceDTO> contentPresence,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<MissingEntityDTO> missingEntities,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<MissingContentDTO> contentGaps,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<IngestionTypeCountDTO> typeCounts) {
}
