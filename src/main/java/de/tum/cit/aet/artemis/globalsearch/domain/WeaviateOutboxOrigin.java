package de.tum.cit.aet.artemis.globalsearch.domain;

/**
 * Why a {@link WeaviateOutboxEntry} exists: the path that enqueued it.
 * <p>
 * This is diagnostic, not dispatch ordering. The dispatcher drains strictly in enqueue order and treats every
 * origin identically, because the bulk-delete fence relies on apply order matching outbox id order.
 * <p>
 * Recording it makes "where did this queued work come from" a {@code GROUP BY} over the outbox rather than a
 * log grep, and keeps a post-mortem answerable from the database after the logs have rotated away. It also
 * separates the two very different situations that produce a backlog: a burst of user edits, and a reconcile
 * pass working through a corpus.
 */
public enum WeaviateOutboxOrigin {

    /**
     * Enqueued by the request path in response to an actual change to the underlying entity.
     */
    LIVE,

    /**
     * Enqueued by the reconcile pass that finds entities with no confirmed write on record, which includes
     * everything that predates the outbox.
     */
    RECONCILE_MISSING,

    /**
     * Enqueued by the reconcile pass that re-derives an entity and finds its content no longer matches what was
     * last written.
     */
    RECONCILE_DRIFT,

    /**
     * Enqueued by the reconcile pass that scans the index itself, either because a row has no backing entity or
     * because its stored content disagrees with what we believe we wrote.
     */
    RECONCILE_ORPHAN
}
