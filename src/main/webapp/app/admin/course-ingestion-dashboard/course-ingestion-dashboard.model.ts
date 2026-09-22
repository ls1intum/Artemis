/**
 * Client models for the admin-only ingestion-coverage observability dashboard. These mirror the read-only response
 * DTOs served by the server `IngestionCoverageResource` under `api/global-search/admin/`.
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
    /**
     * The prefix this installation resolves its own collection names with. Installations sharing one Weaviate
     * cluster are kept apart only by this differing between them, so two servers showing the same value are
     * reading and overwriting each other's entities.
     */
    collectionPrefix?: string;
    /** This installation's own address, which separates its rows inside the shared Iris content collections. */
    baseUrl?: string;
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

/**
 * The full stored record of one `SearchableEntities` row, shown in the detail pane. The heavy counterpart of
 * {@link IndexedEntity}, fetched for the one type an admin selected rather than for the whole course.
 */
export interface IndexedEntityRecord {
    /** The indexed entity type. */
    type: string;
    /** The database id of the entity this row represents. */
    entityId: number;
    /** The stored title, absent if the row has none. */
    title?: string;
    /** When Weaviate created the object (ISO string), absent if it could not be read. */
    ingestedAt?: string;
    /** The stored properties, minus the ones this row has no value for. */
    properties: Record<string, unknown>;
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
    /** The parent lecture, set on lecture units so the tree can place one the index does not hold. */
    lectureId?: number;
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

/** Everything the content browser loads when it opens a course, all diffed from one pair of id-sets. */
export interface CourseBrowserData {
    entities: IndexedEntity[];
    contentPresence: IndexedContentPresence[];
    missingEntities: MissingEntity[];
    contentGaps: MissingContent[];
    /**
     * The per-type counts for this course, diffed from the same sets as the gap lists above. The matrix row carries
     * counts too, but in a stored-projection view those are as old as the last recompute, so pairing them with these
     * live gaps would let the scoreboard call a type complete while the pane beside it names what is missing.
     */
    typeCounts: IngestionTypeCount[];
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

/**
 * What happened to one indexed thing. Mirrors the server `IngestionEventKind`.
 *
 * The three `*_DETECTED` kinds are recorded when a reconcile sweep finds a problem and queues the fix;
 * `INDEX_REPAIRED` is recorded separately once that fix actually lands, so a repair appears twice by design.
 */
export type IngestionEventKind =
    'INGESTED' | 'FAILED' | 'CLAIMED' | 'REQUEUED' | 'STALLED' | 'QUALITY_FLAGGED' | 'DRIFT_DETECTED' | 'MISSING_DETECTED' | 'ORPHAN_DETECTED' | 'INDEX_REPAIRED';

/** One row of the activity feed: a single thing that happened to one indexed entity. */
export interface IngestionEvent {
    /** The event's own id, used as the feed's stable list key. */
    id: number;
    /** What happened. */
    kind: IngestionEventKind;
    /** When it happened, as an ISO timestamp. */
    occurredAt: string;
    /** The kind of thing it happened to, e.g. `LectureUnit`. */
    entityType: string;
    /** The id of that thing. */
    entityId: number;
    /** The course it belongs to, absent when the event is not course-scoped. */
    courseId?: number;
    /** Short human-readable context, rendered verbatim. */
    detail?: string;
}

/** The activity feed payload: the events plus the rolling per-kind totals shown above them. */
export interface IngestionActivity {
    /** The most recent events, newest first. */
    events: IngestionEvent[];
    /** How many events of each kind happened inside `summaryWindowHours`. Absent kinds counted zero. */
    countsByKind: Partial<Record<IngestionEventKind, number>>;
    /** The width of the window `countsByKind` was counted over. */
    summaryWindowHours: number;
}

/** A lecture unit's position in the ingestion state machine. Mirrors the server `ProcessingPhase`. */
export type ProcessingPhase = 'IDLE' | 'TRANSCRIBING' | 'INGESTING' | 'DONE' | 'FAILED' | 'SKIPPED';

/** What enqueued an outbox row: a live content edit, or one of the reconcile sweeps. */
export type WeaviateOutboxOrigin = 'LIVE' | 'RECONCILE_MISSING' | 'RECONCILE_DRIFT' | 'RECONCILE_ORPHAN';

/** Which reconcile sweep a status row describes. */
export type ReconcilePass = 'MISSING' | 'DRIFT' | 'ORPHAN';

/**
 * One lecture unit a worker is currently working on.
 *
 * `lastProgressAt` and `lastHeartbeatAt` are both present on purpose: a run whose heartbeat is fresh but whose
 * progress is frozen is wedged rather than slow, and that is the distinction the view exists to show.
 */
export interface RunningIngestion {
    lectureUnitId: number;
    lectureUnitName?: string;
    courseId?: number;
    courseTitle?: string;
    phase: ProcessingPhase;
    /** The pipeline stage the worker last reported, e.g. `vision`. */
    stage?: string;
    stageProgress?: number;
    stageTotal?: number;
    startedAt?: string;
    /** When the progress counter last advanced. */
    lastProgressAt?: string;
    /** When the worker last renewed its lease. */
    lastHeartbeatAt?: string;
    /** The boot id of the worker holding the lease. */
    lockedBy?: string;
    retryCount?: number;
}

/** One lecture unit waiting to be claimed, in dispatch order. */
export interface QueuedIngestion {
    lectureUnitId: number;
    lectureUnitName?: string;
    courseId?: number;
    courseTitle?: string;
    /** 0 for fresh work, higher for backfill and reconcile work. */
    dispatchPriority?: number;
    retryCount?: number;
    /** When a failed unit becomes claimable again; absent when it is not a retry. */
    retryEligibleAt?: string;
    /** Whether this unit is queued for a forced re-ingestion (quality or drift). */
    forceReingest?: boolean;
}

/** The pull-based lecture ingestion queue: how deep it is, what is running, and what is next. */
export interface LectureIngestionQueue {
    /** How many units sit in each phase. Absent phases hold none. */
    countsByPhase?: Partial<Record<ProcessingPhase, number>>;
    /** Absent rather than empty when nothing is running: the server omits empty collections. */
    running?: RunningIngestion[];
    /** Absent rather than empty when nothing is queued. */
    nextUp?: QueuedIngestion[];
    /** How many failed units are waiting out their retry backoff. */
    retryWaiting?: number;
}

/** One row waiting in the Weaviate outbox. */
export interface OutboxEntry {
    id: number;
    operation: string;
    origin: WeaviateOutboxOrigin;
    entityType?: string;
    entityId?: number;
    /** How many times this write has already failed. */
    attempts?: number;
    nextAttemptAt: string;
    createdAt: string;
}

/**
 * The durable Weaviate write queue. Rows are deleted once their write is confirmed, so this is always the
 * backlog and never a history: a healthy queue is near-empty, and a deep one whose `maxAttempts` is climbing
 * means writes are failing rather than merely arriving faster than they drain.
 */
export interface WeaviateOutboxQueue {
    total?: number;
    /** How many are past their backoff and would be picked up on the next drain. */
    dueNow?: number;
    countsByOrigin?: Partial<Record<WeaviateOutboxOrigin, number>>;
    oldestEnqueued?: string;
    maxAttempts?: number;
    /** Absent rather than empty when the queue is drained. */
    head?: OutboxEntry[];
}

/** Where one reconcile sweep is in its current cycle. Counters are cycle-scoped and reset when it wraps. */
export interface ReconcilePassStatus {
    pass: ReconcilePass;
    lastRunAt?: string;
    cycleStartedAt?: string;
    entitiesChecked?: number;
    repairsEnqueued?: number;
    /** Only the orphan pass removes rows. */
    rowsRemoved?: number;
}

/**
 * One ingestion worker, seen from the leases it holds. Artemis keeps no worker registry, so a worker holding
 * no run is invisible here — which is correct for a view whose question is what is being worked on.
 */
export interface IngestionWorker {
    bootId: string;
    activeRuns?: number;
    lastHeartbeatAt?: string;
}

/** Every queue this feature owns, and what each is doing right now. */
export interface QueueOverview {
    lectureIngestion?: LectureIngestionQueue;
    weaviateOutbox?: WeaviateOutboxQueue;
    reconcilePasses?: ReconcilePassStatus[];
    workers?: IngestionWorker[];
}

/** Which service produced a log record. */
export type IngestionLogSource = 'ARTEMIS' | 'IRIS';

/**
 * One captured ingestion log record. Artemis and Iris each keep a bounded in-memory buffer of their own
 * ingestion records; the server merges both into one time-ordered list, so a reader follows a single story
 * across the two services instead of correlating two lists by timestamp.
 */
export interface IngestionLogEntry {
    source: IngestionLogSource;
    /** When it was logged, as an ISO timestamp. */
    occurredAt: string;
    /** Level name, e.g. `DEBUG`. */
    level: string;
    /** The logger that emitted it. */
    logger: string;
    message: string;
    /** The rendered throwable or traceback, absent when the record carried none. */
    stackTrace?: string;
}
