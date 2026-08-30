package de.tum.cit.aet.artemis.globalsearch.dto;

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
public record IngestionTypeCountDTO(String type, long expected, long indexed, long missing, long orphaned) {
}
