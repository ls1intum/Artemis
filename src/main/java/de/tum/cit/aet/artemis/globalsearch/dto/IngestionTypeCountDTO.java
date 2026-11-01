package de.tum.cit.aet.artemis.globalsearch.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Per-type coverage counts for one course and one indexed entity type (e.g. {@code exercise}, or the content types
 * {@code slides} / {@code transcript}). Stored as a JSON array on the coverage projection; each entry compares what the
 * database expects against what the Weaviate index actually holds.
 *
 * @param type     the indexed entity type these counts refer to
 * @param expected the number the database expects to be indexed
 * @param indexed  the number actually present in the Weaviate index
 * @param missing  expected but not indexed
 * @param orphaned indexed but no longer expected
 */
// Rows outlive the shape that wrote them: this is read back by whatever the record looks like when the projection is
// next read, so a component removed in a later release must not make the rows written before it unreadable.
@JsonIgnoreProperties(ignoreUnknown = true)
public record IngestionTypeCountDTO(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String type, @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long expected,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long indexed, @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long missing,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long orphaned) {
}
