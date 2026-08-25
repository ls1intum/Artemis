/**
 * Frontend models for the admin-only ingestion-coverage observability dashboard. These mirror the read-only response
 * DTOs served by the backend `IngestionCoverageResource` under `api/global-search/ingestion-dashboard/`.
 */

/** Overall Weaviate index coverage of a course. */
export type IngestionCoverageStatus = 'COMPLETE' | 'INCOMPLETE' | 'EMPTY';

/**
 * The live object count of one indexed Weaviate collection. A collection that cannot be read (not present on this
 * instance, or unreachable) is reported with `readable = false` and a missing count rather than failing the overview.
 */
export interface IndexedCollectionCount {
    /** The collection name as addressed (the prefixed Artemis collection or an exact Iris collection name). */
    collection: string;
    /** The number of objects in the collection, or `null` if it could not be read. */
    count: number | null;
    /** Whether the count could be read. */
    readable: boolean;
}

/**
 * The top-band overview of the search index: whether Weaviate is reachable and at which address, whether the Iris module
 * is enabled, and the live object count of each tracked collection.
 */
export interface IndexOverview {
    /** Whether the Weaviate instance is currently reachable. */
    weaviateReachable: boolean;
    /** The configured Weaviate address (shown whether or not it is reachable). */
    weaviateAddress: string;
    /** Whether the Iris module is enabled (the Iris content collections only exist when it is). */
    irisEnabled: boolean;
    /** The per-collection live object counts. */
    collections: IndexedCollectionCount[];
}

/**
 * Per-type coverage counts for one course and one indexed entity type. Compares what the database expects against what
 * the Weaviate index actually holds. The two summary content types are present-only, so their `missing` is always 0.
 */
export interface IngestionTypeCount {
    /** The indexed entity type these counts refer to (e.g. `exercise`, `slides`, `transcript`). */
    type: string;
    /** The number the database expects to be indexed. */
    expected: number;
    /** The number actually present in the Weaviate index. */
    indexed: number;
    /** Expected but not indexed. */
    missing: number;
    /** Indexed but no longer expected. */
    orphaned: number;
}

/**
 * One course's index coverage for the dashboard matrix. Both the stored cross-course view and the live per-page view
 * return this shape.
 */
export interface IngestionCoverage {
    /** The id of the course. */
    courseId: number;
    /** The course title. */
    courseTitle: string;
    /** The course start/release date (ISO string), or `null`. */
    releaseDate: string | null;
    /** Whether the course is currently active. */
    active: boolean;
    /** The course semester, or `null`. */
    semester: string | null;
    /** The overall coverage status. */
    status: IngestionCoverageStatus;
    /** The precomputed worst-first severity (higher is worse; the total missing count). */
    coverageGapScore: number;
    /** When this coverage was computed (ISO string). */
    computedAt: string;
    /** The most recent index write across the course's objects (ISO string), or `null` if nothing is indexed. */
    lastIngestedAt: string | null;
    /** The per-type expected/indexed/missing/orphaned counts. */
    typeCounts: IngestionTypeCount[];
}
