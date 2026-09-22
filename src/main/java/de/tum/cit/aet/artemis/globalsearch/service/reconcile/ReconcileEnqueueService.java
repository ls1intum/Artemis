package de.tum.cit.aet.artemis.globalsearch.service.reconcile;

import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.globalsearch.config.WeaviateEnabled;
import de.tum.cit.aet.artemis.globalsearch.config.WeaviateReconcileProperties;
import de.tum.cit.aet.artemis.globalsearch.domain.IngestionEventKind;
import de.tum.cit.aet.artemis.globalsearch.domain.WeaviateOutboxOrigin;
import de.tum.cit.aet.artemis.globalsearch.repository.WeaviateOutboxRepository;
import de.tum.cit.aet.artemis.globalsearch.service.IngestionEventLogService;
import de.tum.cit.aet.artemis.globalsearch.service.SearchableEntityWeaviateService;

/**
 * The one route by which a reconcile pass queues a repair, so pacing and deduplication are enforced once rather
 * than in each pass.
 * <p>
 * The pacing is a feedback loop rather than a rate: a pass only tops the outbox up when the dispatcher has
 * worked it down again. A slow or unavailable Weaviate therefore stops the passes on its own, and the day the
 * missing pass first runs against an empty ledger it trickles the corpus through instead of queueing all of it
 * at once.
 */
@Lazy
@Service
@Conditional(WeaviateEnabled.class)
public class ReconcileEnqueueService {

    private static final Logger log = LoggerFactory.getLogger(ReconcileEnqueueService.class);

    private final WeaviateOutboxRepository outboxRepository;

    private final SearchableEntityWeaviateService searchableEntityWeaviateService;

    private final WeaviateReconcileProperties reconcileProperties;

    private final IngestionEventLogService ingestionEventLogService;

    /**
     * Whether the depth limit was already blocking last time it was checked, so the transitions are logged rather
     * than every blocked check. A pass ticks forever, and a backlog can take days to drain.
     */
    private final AtomicBoolean blocked = new AtomicBoolean(false);

    public ReconcileEnqueueService(WeaviateOutboxRepository outboxRepository, SearchableEntityWeaviateService searchableEntityWeaviateService,
            WeaviateReconcileProperties reconcileProperties, IngestionEventLogService ingestionEventLogService) {
        this.outboxRepository = outboxRepository;
        this.searchableEntityWeaviateService = searchableEntityWeaviateService;
        this.reconcileProperties = reconcileProperties;
        this.ingestionEventLogService = ingestionEventLogService;
    }

    /**
     * Returns whether there is room to queue more repairs. Call this before each batch rather than once per tick,
     * so a long tick cannot push the queue far past the limit.
     * <p>
     * The count is cheap precisely because this limit keeps the table small: rows are deleted once their write is
     * confirmed, so the outbox holds only what is still pending.
     *
     * @return true if the queue is below the configured depth
     */
    public boolean canEnqueue() {
        boolean roomAvailable = outboxRepository.count() < reconcileProperties.maxOutboxDepth();
        if (!roomAvailable && blocked.compareAndSet(false, true)) {
            log.warn("Global search reconcile paused: the outbox is at its depth limit of {}. It resumes as the dispatcher drains it.", reconcileProperties.maxOutboxDepth());
        }
        else if (roomAvailable && blocked.compareAndSet(true, false)) {
            log.info("Global search reconcile resumed: the outbox has drained below its depth limit");
        }
        return roomAvailable;
    }

    /**
     * Queues a write for an entity, unless one is already queued for it.
     *
     * @param entityType the {@code SearchableEntitySchema.TypeValues} discriminator
     * @param entityId   the database id of the entity
     * @param origin     which pass is asking
     * @return true if a row was queued, false if one was already pending
     */
    public boolean enqueueUpsert(String entityType, long entityId, WeaviateOutboxOrigin origin) {
        if (alreadyQueued(entityType, entityId)) {
            return false;
        }
        searchableEntityWeaviateService.enqueueUpsert(entityType, entityId, origin);
        recordDetection(entityType, entityId, origin, "queued a rewrite");
        return true;
    }

    /**
     * Queues the removal of an entity's row, unless one is already queued for it.
     *
     * @param entityType the {@code SearchableEntitySchema.TypeValues} discriminator
     * @param entityId   the database id of the entity
     * @param origin     which pass is asking
     * @return true if a row was queued, false if one was already pending
     */
    public boolean enqueueDelete(String entityType, long entityId, WeaviateOutboxOrigin origin) {
        if (alreadyQueued(entityType, entityId)) {
            return false;
        }
        searchableEntityWeaviateService.enqueueDeleteEntity(entityType, entityId, origin);
        recordDetection(entityType, entityId, origin, "queued a removal");
        return true;
    }

    /**
     * Skips an entity that is already waiting, so a slowly draining backlog does not accumulate duplicates of the
     * same work, and two passes that both notice the same entity queue it once.
     * <p>
     * Best effort by design: two passes can check at the same moment and both queue. That is harmless, because the
     * dispatcher re-derives current state when it applies a row, so a duplicate simply writes the same thing twice.
     * Locking to prevent it would cost more than the duplicate does.
     */
    private boolean alreadyQueued(String entityType, long entityId) {
        return outboxRepository.existsByEntityTypeAndEntityId(entityType, entityId);
    }

    /**
     * Records what a pass just found, for the admin activity feed.
     * <p>
     * This is the one place every reconcile repair passes through, so recording here covers the drift,
     * missing and orphan passes without any of them knowing about the log. The event names a detection
     * rather than a repair: the write it queued is applied later by the dispatcher, which records its own
     * {@link IngestionEventKind#INDEX_REPAIRED} once it lands.
     *
     * @param entityType the entity type the pass acted on
     * @param entityId   the entity id
     * @param origin     which pass asked, which is what identifies the kind of finding
     * @param action     what was queued, for the feed's detail line
     */
    private void recordDetection(String entityType, long entityId, WeaviateOutboxOrigin origin, String action) {
        IngestionEventKind kind = switch (origin) {
            case RECONCILE_DRIFT -> IngestionEventKind.DRIFT_DETECTED;
            case RECONCILE_MISSING -> IngestionEventKind.MISSING_DETECTED;
            case RECONCILE_ORPHAN -> IngestionEventKind.ORPHAN_DETECTED;
            // A live edit is ordinary traffic, not a finding, and recording it would mirror every content
            // change in Artemis into this log.
            case LIVE -> null;
        };
        if (kind != null) {
            ingestionEventLogService.record(kind, entityType, entityId, null, action);
        }
    }
}
