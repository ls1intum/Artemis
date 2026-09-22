package de.tum.cit.aet.artemis.globalsearch.domain;

/**
 * What happened to one indexed thing, as recorded in {@link IngestionEventLogEntry}.
 * <p>
 * Every other table in this area is a current-state snapshot that is overwritten in place, so the
 * transient outcomes below — a drift repair, an orphan removal, a lapsed lease being requeued — leave
 * no trace once the sweep that produced them moves on. These constants name exactly those outcomes so
 * the admin activity feed can show what the pipelines did rather than only what they currently hold.
 */
public enum IngestionEventKind {

    /**
     * A lecture unit finished ingesting and passed its read-back audit.
     */
    INGESTED,

    /**
     * An ingestion run ended in failure; the detail carries the error key.
     */
    FAILED,

    /**
     * A worker claimed a unit and started working on it.
     */
    CLAIMED,

    /**
     * A run was put back on the queue without a terminal failure — a lapsed worker lease, an abandoned
     * dispatch claim, or a reconcile pass finding the stored index diverged from what Artemis expects.
     */
    REQUEUED,

    /**
     * A run stopped making progress while its worker kept heartbeating: wedged rather than slow.
     */
    STALLED,

    /**
     * A unit was re-ingested because its stored content scored below the quality threshold.
     */
    QUALITY_FLAGGED,

    /**
     * The drift sweep found an entity whose indexed content no longer matched its source, and queued a
     * rewrite. Recorded at detection rather than at completion: the repair itself is a queued write that
     * may not land for another tick, and {@link #INDEX_REPAIRED} is what records its outcome.
     */
    DRIFT_DETECTED,

    /**
     * The missing sweep found an entity that had never been confirmed written, and queued it for indexing.
     */
    MISSING_DETECTED,

    /**
     * The orphan sweep found an index row with no backing Artemis entity, and queued its removal.
     */
    ORPHAN_DETECTED,

    /**
     * A repair the reconcile passes queued was confirmed applied to the index. Only repair work is
     * recorded here; an ordinary live edit is not, or the log would mirror every content change in Artemis.
     */
    INDEX_REPAIRED
}
