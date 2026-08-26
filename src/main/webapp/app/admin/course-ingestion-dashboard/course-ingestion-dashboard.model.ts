/**
 * Frontend models for the admin-only ingestion-coverage observability dashboard. These mirror the read-only response
 * DTOs served by the backend `IngestionCoverageResource` under `api/global-search/admin/`.
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
    /** Whether Iris is actually answering. Always false when the module is disabled. */
    irisReachable: boolean;
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

/**
 * One row stored in the `SearchableEntities` collection for a course, reduced to what the tree needs to place it. The
 * full stored record is read for a single entity when one is selected, because the property map carries the course's
 * body text and returning it per row made opening a course ship all of it.
 */
export interface IndexedEntity {
    /** The indexed entity type (e.g. `lecture`, `lecture_unit`). */
    type: string;
    /** The database id of the entity this row represents. */
    entityId: number;
    /** The stored title, absent if the row has none. */
    title?: string;
    /** The parent lecture, set on lecture units; the tree nests units by it. */
    lectureId?: number;
    /** When Weaviate created the object (ISO string), absent if it could not be read. */
    ingestedAt?: string;
}

/**
 * Which lecture units hold content in one Iris collection. Presence, not payload: the tree needs to know which units
 * have slides or a transcript in order to draw, and reads the objects themselves only once a node is selected.
 */
export interface IndexedContentPresence {
    /** The content key: `slides`, `transcript`, `unit_summary` or `segments`. */
    key: string;
    /** The ids of the lecture units holding at least one object in the backing collection. */
    unitIds: number[];
}

/** One object stored in an Iris lecture-content collection, shown when a collection node is selected. */
export interface IndexedContentObject {
    /** When Weaviate created the object (ISO string), absent if it could not be read. */
    ingestedAt?: string;
    /** The populated stored properties. */
    properties: Record<string, unknown>;
}

/** One entity the database expects to be indexed that the index does not hold. */
export interface MissingEntity {
    /** The indexed entity type. */
    type: string;
    /** The database id of the missing entity. */
    entityId: number;
    /** The entity's title or name, absent if it could no longer be resolved. */
    title?: string;
}

/** One lecture unit that should have ingested content but does not. */
export interface MissingContent {
    /** The database id of the lecture unit. */
    lectureUnitId: number;
    /** The unit's name, absent if it could no longer be resolved. */
    title?: string;
    /** Which content is absent. */
    kind: 'slides' | 'transcript';
}

/** The four datasets the content browser loads when it opens a course. */
export interface CourseBrowserData {
    entities: IndexedEntity[];
    contentPresence: IndexedContentPresence[];
    missingEntities: MissingEntity[];
    contentGaps: MissingContent[];
}

/**
 * What the browser's detail pane is currently showing. A discriminated union rather than a parsed string, so the detail
 * pane switches on `kind` and cannot misread one id as another.
 */
export type BrowserSelection =
    { kind: 'type'; type: string } | { kind: 'lecture'; lectureId: number } | { kind: 'unit'; unitId: number } | { kind: 'collection'; unitId: number; key: string };

/** Stable string form of a selection, used to track expansion and to mark nodes in the DOM. */
export function selectionKey(selection: BrowserSelection): string {
    switch (selection.kind) {
        case 'type':
            return `type:${selection.type}`;
        case 'lecture':
            return `lecture:${selection.lectureId}`;
        case 'unit':
            return `unit:${selection.unitId}`;
        case 'collection':
            return `coll:${selection.unitId}:${selection.key}`;
    }
}
