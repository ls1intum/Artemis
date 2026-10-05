package de.tum.cit.aet.artemis.globalsearch.domain;

/**
 * Overall Weaviate index coverage of a course, precomputed by the coverage recompute and stored on
 * {@link IngestionCoverageEntry} so the ingestion-observability dashboard can filter the matrix by status in a single
 * indexed read.
 */
public enum IngestionCoverageStatus {

    /** Every entity the course expects to have indexed in Weaviate is present. */
    COMPLETE,

    /** At least one expected entity is missing from the Weaviate index. */
    INCOMPLETE,

    /** The course has no indexable entities at all (nothing is expected to be indexed). */
    EMPTY
}
