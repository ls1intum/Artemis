/**
 * Object count for a single Weaviate collection.
 * {@link objectCount} is null when the collection could not be read (see {@link available}).
 */
export interface IndexCollectionCount {
    collection: string;
    objectCount: number | null;
    available: boolean;
}

/** One live pipeline step of an in-flight ingestion: its name, state, and (once finished) its duration. */
export interface IngestionActivity {
    id: string;
    kind: string;
    name: string;
    state: 'RUNNING' | 'FINISHED' | 'FAILED';
    detail?: string;
    result?: string;
    durationMillis?: number;
}

/** A lecture ingestion currently in flight, with its live per-step activity list. */
export interface ActiveIngestion {
    jobId: string;
    courseId: number;
    lectureId: number;
    lectureUnitId: number;
    lectureUnitName?: string;
    lectureName?: string;
    runState: string;
    startedAt?: string;
    lastUpdatedAt?: string;
    activities?: IngestionActivity[];
}

/** A finished or failed lecture ingestion kept for the recent-history view. */
export interface RecentIngestion {
    jobId: string;
    courseId: number;
    lectureId: number;
    lectureUnitId: number;
    lectureUnitName?: string;
    lectureName?: string;
    outcome: 'FINISHED' | 'FAILED';
    startedAt?: string;
    finishedAt: string;
    totalMillis?: number;
    activities?: IngestionActivity[];
    failedStepName?: string;
    errorMessage?: string;
}

/** A pipeline step for display: its label, its state (including pending steps not yet started), and its duration. */
export interface DisplayStep {
    name: string;
    label: string;
    state: 'PENDING' | 'RUNNING' | 'FINISHED' | 'FAILED';
    durationMillis?: number;
    detail?: string;
}

/**
 * Read-only snapshot of the Weaviate index: reachability plus per-collection object counts.
 */
export interface IndexOverview {
    weaviateUp: boolean;
    weaviateAddress?: string;
    collections: IndexCollectionCount[];
}

/**
 * Index census for a single entity type within a course: how many keys the database expects, how many are indexed
 * in Weaviate, and the missing / orphaned counts from their set difference. {@link expected}, {@link missing} and
 * {@link orphaned} are null for types without a database source yet (present-only).
 */
export interface TypeIndexCensus {
    type: string;
    expected: number | null;
    present: number;
    missing: number | null;
    orphaned: number | null;
}

/**
 * File-level lecture-content completeness for a course: of the units that have this file kind (pdf or video), how many
 * have content ingested into the corresponding Iris collection.
 */
export interface ContentCensus {
    key: string;
    expected: number;
    present: number;
    missing: number;
}

/** One object stored in Weaviate for a course: its type, id, title, when it was ingested, and its full stored fields. */
export interface IndexedEntity {
    type: string;
    entityId: number;
    title?: string;
    ingestedAt?: string;
    properties: Record<string, unknown>;
}

/** One object stored in an Iris lecture-content collection: its ingestion time and full stored fields. */
export interface IndexedContentObject {
    ingestedAt?: string;
    properties: Record<string, unknown>;
}

/** The objects stored in one Iris lecture-content collection (slides, transcript, unit summaries, segments). */
export interface IndexedContent {
    key: string;
    count: number;
    objects: IndexedContentObject[];
}

/** One entity the database expects to be indexed but that is absent from Weaviate, resolved to its title. */
export interface MissingEntity {
    type: string;
    entityId: number;
    title?: string;
}

/** One lecture unit expected to have ingested content (slides or transcript) that is absent from the Iris collections. */
export interface MissingContent {
    lectureUnitId: number;
    title?: string;
    kind: 'slides' | 'transcript';
}

/**
 * Per-type index census for one course, plus the Iris lecture-content completeness (empty when Iris is not enabled).
 */
export interface CourseIndexCensus {
    courseId: number;
    courseTitle: string | null;
    semester: string | null;
    startDate: string | null;
    active: boolean;
    types: TypeIndexCensus[];
    content: ContentCensus[];
}
