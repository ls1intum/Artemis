package de.tum.cit.aet.artemis.globalsearch.service;

/**
 * Marker application event published after a {@code weaviate_outbox} row is enqueued.
 * <p>
 * The {@code WeaviateOutboxDispatcher} listens for it after commit and nudges a drain so a metadata change is
 * reflected in Weaviate with freshness comparable to the previous fire-and-forget {@code @Async} write. The
 * event is in-process only and carries no data (the dispatcher re-queries the outbox), so on a node that is
 * not the scheduling node it has no listener and is a harmless no-op; those enqueues are drained by the
 * dispatcher's scheduled tick instead. This never lets a non-scheduling node dispatch.
 */
public record WeaviateOutboxEnqueuedEvent() {
}
