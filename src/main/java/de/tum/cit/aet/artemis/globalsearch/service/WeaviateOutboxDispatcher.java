package de.tum.cit.aet.artemis.globalsearch.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_SCHEDULING;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import de.tum.cit.aet.artemis.globalsearch.config.WeaviateEnabled;
import de.tum.cit.aet.artemis.globalsearch.config.WeaviateOutboxProperties;
import de.tum.cit.aet.artemis.globalsearch.config.schema.entityschemas.SearchableEntitySchema;
import de.tum.cit.aet.artemis.globalsearch.domain.SearchableEntitySyncState;
import de.tum.cit.aet.artemis.globalsearch.domain.WeaviateOutboxEntry;
import de.tum.cit.aet.artemis.globalsearch.domain.WeaviateOutboxOperation;
import de.tum.cit.aet.artemis.globalsearch.repository.SearchableEntitySyncStateRepository;
import de.tum.cit.aet.artemis.globalsearch.repository.WeaviateOutboxRepository;

/**
 * Single-writer dispatcher that drains the {@code weaviate_outbox} and performs the actual writes to the
 * shared {@code SearchableEntities} Weaviate collection.
 * <p>
 * It runs only on the scheduling node ({@code @Profile(PROFILE_SCHEDULING)}), which Artemis requires to be
 * exactly one instance, so it is the single writer to the collection. It reads due rows in id (enqueue) order
 * with a plain query and processes them sequentially. Each Weaviate write happens outside any transaction, so a
 * slow or hung write never holds a database connection; the outcome is then recorded with plain repository calls:
 * on success the {@code searchable_entity_sync_state} ledger is refreshed for an upsert and the row is deleted; on
 * failure the row survives with an incremented attempt count and an exponentially backed-off
 * {@code next_attempt_at}, so a Weaviate outage self-heals when Weaviate recovers.
 * <p>
 * No transaction spans the outcome writes either: Artemis keeps transaction boundaries inside repositories (see
 * {@code ArchitectureTest.testNoProgrammaticTransactionManagement}), so {@link #confirmWrite} orders its statements
 * so that a crash between them is harmless. The row delete comes last and is the acknowledgement; every statement
 * before it is idempotent, and a row that survives a crash is simply re-read and re-applied on the next drain.
 * <p>
 * A confirmed per-entity write drops all older outbox rows for that entity ({@link #collapseSupersededRows}),
 * so a failed older row deferred by backoff cannot wake up after a newer row for the same entity succeeded and
 * overwrite it. This preserves latest-wins for single-entity races (same type and id) under the single writer.
 * Bulk deletes have no single entity key and so are not collapsed; instead they are fenced by {@code source_seq}:
 * every upserted row is stamped with the outbox id of the write that produced it, and a bulk delete removes only
 * rows written before its own outbox id. A bulk delete deferred by backoff therefore cannot erase a newer
 * in-scope upsert that already succeeded (see {@code SearchableEntityWeaviateService#writtenBefore}). A later
 * reconcile pass remains the backstop for writes lost before they reached the outbox and for content drift.
 * <p>
 * A drain is triggered two ways: a periodic {@link #scheduledDrain()} tick (the cross-node path and safety
 * net) and an asynchronous {@link #onOutboxEnqueued(WeaviateOutboxEnqueuedEvent)} nudge for the freshness of
 * enqueues made on this node. A {@link ReentrantLock} guarantees only one drain runs at a time on this node.
 * <p>
 * Being the single writer, it needs no lock or lease to protect a row during processing: the read does not
 * mutate the row, so a crash mid-batch simply leaves it to be re-read and re-applied (writes are idempotent).
 */
@Lazy
@Component
@Conditional(WeaviateEnabled.class)
@Profile(PROFILE_SCHEDULING)
public class WeaviateOutboxDispatcher {

    private static final Logger log = LoggerFactory.getLogger(WeaviateOutboxDispatcher.class);

    /**
     * A row's first few failures log at warn; further retries log at debug so a sustained Weaviate outage with a
     * backlog does not emit one warning per row per retry.
     */
    private static final int MAX_WARN_ATTEMPTS = 3;

    /**
     * Drain and retry tuning, bound from {@code artemis.weaviate.outbox}.
     */
    private final WeaviateOutboxProperties outboxProperties;

    private final WeaviateOutboxRepository outboxRepository;

    private final SearchableEntitySyncStateRepository syncStateRepository;

    private final SearchableEntityWeaviateService searchableEntityWeaviateService;

    /**
     * Serializes drains on this node so the scheduled tick and the after-commit nudge never overlap.
     */
    private final ReentrantLock drainLock = new ReentrantLock();

    public WeaviateOutboxDispatcher(WeaviateOutboxRepository outboxRepository, SearchableEntitySyncStateRepository syncStateRepository,
            SearchableEntityWeaviateService searchableEntityWeaviateService, WeaviateOutboxProperties outboxProperties) {
        this.outboxRepository = outboxRepository;
        this.syncStateRepository = syncStateRepository;
        this.searchableEntityWeaviateService = searchableEntityWeaviateService;
        this.outboxProperties = outboxProperties;
    }

    /**
     * Periodic safety-net drain, and the path by which enqueues made on non-scheduling nodes reach Weaviate. On the
     * scheduling node the enqueue nudge already makes local enqueues fresh, so this tick primarily drains
     * enqueues made on other nodes and covers any missed nudge.
     * <p>
     * The cadence defaults to 5 seconds and is overridable via {@code artemis.weaviate.outbox.drain-interval-seconds}.
     * Query-count profiling tests raise it so this background query cannot land inside their measurement window.
     */
    @Scheduled(fixedDelayString = "${artemis.weaviate.outbox.drain-interval-seconds:5}", timeUnit = TimeUnit.SECONDS)
    public void scheduledDrain() {
        drain();
    }

    /**
     * Nudge so an enqueue made on this node is applied promptly, keeping freshness comparable to the previous
     * fire-and-forget write. Runs asynchronously off the caller's thread. The enqueue saves its outbox row through
     * a repository call, whose transaction has committed by the time the call returns and the event is published,
     * so the row is already visible to {@link #drainBatch} when this listener runs. Should a caller ever publish
     * from inside a transaction of its own, the nudge simply sees no row yet and the scheduled tick drains it.
     *
     * @param event the marker event published by {@code SearchableEntityWeaviateService} after an enqueue
     */
    @Async
    @EventListener
    public void onOutboxEnqueued(WeaviateOutboxEnqueuedEvent event) {
        drain();
    }

    /**
     * Drains all currently due outbox rows, one batch at a time. At most one drain runs at a time on this node;
     * a concurrent trigger returns immediately and lets the in-flight drain finish the work.
     */
    public void drain() {
        if (!drainLock.tryLock()) {
            // Another drain is already running on this node; it will pick up any rows we would have read.
            return;
        }
        try {
            // Give the drain thread an authorization context, matching the previous @Async write behavior.
            SecurityUtils.setAuthorizationObject();
            int processed;
            do {
                processed = drainBatch();
            }
            while (processed == outboxProperties.batchSize());
        }
        catch (Exception e) {
            // The database was unavailable while reading or recording. Give up for now; the next tick retries.
            log.error("Weaviate outbox drain aborted: {}", e.getMessage(), e);
        }
        finally {
            drainLock.unlock();
        }
    }

    /**
     * Reads one batch of due rows (no transaction, no lock) and processes each.
     *
     * @return the number of rows read (equal to the configured batch size while more may remain)
     */
    private int drainBatch() {
        ZonedDateTime now = ZonedDateTime.now();
        List<WeaviateOutboxEntry> batch = outboxRepository.findDueForDispatch(now, outboxProperties.batchSize());
        for (WeaviateOutboxEntry entry : batch) {
            processEntry(entry, now);
        }
        return batch.size();
    }

    /**
     * Applies a single outbox entry to Weaviate outside any transaction, then records the outcome:
     * {@link #confirmWrite} on success, {@link #scheduleRetry} on failure.
     */
    private void processEntry(WeaviateOutboxEntry entry, ZonedDateTime now) {
        try {
            Optional<String> writtenContentHash = searchableEntityWeaviateService.applyOutboxEntry(entry);
            logApplied(entry, writtenContentHash);
            if (entry.getAttempts() > 0) {
                log.info("Weaviate outbox entry {} succeeded after {} failed attempt(s)", entry.getId(), entry.getAttempts());
            }
            confirmWrite(entry, now, writtenContentHash);
        }
        catch (Exception e) {
            int attempt = entry.getAttempts() + 1;
            // Compute the retry time now, not from the batch-start timestamp: a slow write could otherwise make the backoff already due.
            ZonedDateTime nextAttempt = ZonedDateTime.now().plusSeconds(backoffSeconds(attempt));
            scheduleRetry(entry, nextAttempt);
            if (attempt <= MAX_WARN_ATTEMPTS) {
                log.warn("Failed to apply Weaviate outbox entry {} (attempt {}), retrying after {}: {}", entry.getId(), attempt, nextAttempt, e.getMessage());
            }
            else {
                log.debug("Failed to apply Weaviate outbox entry {} (attempt {}), retrying after {}: {}", entry.getId(), attempt, nextAttempt, e.getMessage());
            }
        }
    }

    /**
     * Traces a confirmed apply at {@code DEBUG} so the full enqueue-to-write flow is legible in the logs (the
     * enqueue side already logs at {@code DEBUG}). The written content hash distinguishes the two upsert outcomes:
     * present means the row was written, empty means the entity was gone or no longer indexable and the dispatcher
     * re-derived it to a delete instead. Bulk deletes carry no single entity id, so they log by operation.
     */
    private static void logApplied(WeaviateOutboxEntry entry, Optional<String> writtenContentHash) {
        if (!log.isDebugEnabled()) {
            return;
        }
        if (entry.getEntityId() == null) {
            log.debug("Applied {} (outbox entry {})", entry.getOperation(), entry.getId());
        }
        else if (writtenContentHash.isPresent()) {
            log.debug("Applied upsert for {} {} (outbox entry {})", entry.getEntityType(), entry.getEntityId(), entry.getId());
        }
        else {
            log.debug("Removed {} {} from the index (outbox entry {})", entry.getEntityType(), entry.getEntityId(), entry.getId());
        }
    }

    /**
     * Records a confirmed write as three independent statements, ordered so a crash between them is harmless:
     * collapse superseded rows, refresh the ledger, then remove the row. The delete is the acknowledgement. If the
     * process dies before it, the row is re-read and re-applied on the next drain, and the two statements before it
     * are idempotent, so nothing is lost or duplicated.
     */
    private void confirmWrite(WeaviateOutboxEntry entry, ZonedDateTime now, Optional<String> writtenContentHash) {
        collapseSupersededRows(entry);
        refreshSyncLedger(entry, now, writtenContentHash);
        outboxRepository.delete(entry);
    }

    /**
     * Records a failed write with a single statement: increments the attempt count and backs off.
     */
    private void scheduleRetry(WeaviateOutboxEntry entry, ZonedDateTime nextAttempt) {
        entry.recordFailedAttempt(nextAttempt);
        outboxRepository.save(entry);
    }

    /**
     * Drops every older outbox row for this entity once its write is confirmed, so a row deferred by backoff
     * cannot later retry and overwrite the newer state. Bulk deletes (null entity id) are skipped.
     */
    private void collapseSupersededRows(WeaviateOutboxEntry entry) {
        if (entry.getEntityId() != null) {
            outboxRepository.deleteSupersededByEntity(entry.getEntityType(), entry.getEntityId(), entry.getId());
        }
    }

    /**
     * Refreshes the {@code searchable_entity_sync_state} ledger from what the write actually did. A row that was
     * upserted records its written content hash; a per-entity entry that resolved to a delete (an explicit delete,
     * or an upsert whose entity is gone or no longer indexable) removes the ledger row so a later reconcile does
     * not treat it as still synced. A bulk delete (null entity id) carries no single row to clean up after, so it
     * is handled separately by {@link #pruneLedgerAfterBulkDelete}.
     */
    private void refreshSyncLedger(WeaviateOutboxEntry entry, ZonedDateTime now, Optional<String> writtenContentHash) {
        if (writtenContentHash.isPresent()) {
            String hash = writtenContentHash.get();
            syncStateRepository.findByEntityTypeAndEntityId(entry.getEntityType(), entry.getEntityId()).ifPresentOrElse(state -> {
                state.setContentHash(hash);
                state.setSyncedAt(now);
                // Writing the row is also a verification of it, so a busy entity is not re-checked for no reason.
                state.setVerifiedAt(now);
                syncStateRepository.save(state);
            }, () -> syncStateRepository.save(new SearchableEntitySyncState(entry.getEntityType(), entry.getEntityId(), hash, now)));
        }
        else if (entry.getEntityId() != null) {
            syncStateRepository.deleteByEntityTypeAndEntityId(entry.getEntityType(), entry.getEntityId());
        }
        else {
            pruneLedgerAfterBulkDelete(entry.getOperation());
        }
    }

    /**
     * Prunes ledger rows a bulk delete just orphaned, for the two types nothing else ever revisits.
     * <p>
     * A bulk delete's Weaviate-side filter has no entity id to hand back, so unlike a per-entity write it cannot
     * name the rows it just removed. For a type a reconcile pass manages that gap is harmless: the drift pass
     * walks the ledger directly, ordered by how long ago each row was checked, and re-derives every row it reaches
     * regardless of why it might be stale — including one left behind by a bulk delete, not just an ordinary
     * missed enqueue. Post and answer post are excluded from every pass ({@code WeaviateReconcileProperties}), so
     * their ledger rows are never revisited that way; left alone, a bulk delete that removes their Weaviate rows
     * leaks the matching ledger rows permanently.
     * <p>
     * By the time a bulk delete confirms, the entities its filter targeted are essentially always already gone
     * from the database too: the enqueue happens before the caller's own delete (see, for example,
     * {@code CourseDeletionService}), and the two are not the same transaction. That rules out re-deriving which
     * rows this specific bulk delete affected, but not a plain existence check: a row for a post or answer post
     * that no longer exists is stale regardless of which deletion caused it, so the check below also sweeps up
     * any earlier leak the same way, not only the one from this confirm.
     */
    private void pruneLedgerAfterBulkDelete(WeaviateOutboxOperation operation) {
        switch (operation) {
            case DELETE_POSTS_FOR_CHANNEL, DELETE_POSTS_FOR_COURSE, DELETE_ALL_FOR_COURSE -> {
                syncStateRepository.deleteStalePostEntries(SearchableEntitySchema.TypeValues.POST);
                syncStateRepository.deleteStaleAnswerPostEntries(SearchableEntitySchema.TypeValues.ANSWER_POST);
            }
            case DELETE_ANSWER_POSTS_FOR_POST -> syncStateRepository.deleteStaleAnswerPostEntries(SearchableEntitySchema.TypeValues.ANSWER_POST);
            case DELETE_LECTURE_UNITS_FOR_LECTURE -> {
                // lecture_unit is managed by the reconcile passes; the drift sweep already prunes its stale rows.
            }
            case UPSERT, DELETE_ENTITY -> throw new IllegalStateException("Not a bulk delete: " + operation);
        }
    }

    /**
     * Exponential backoff capped at the configured maximum: with the defaults, 10s, 20s, 40s, ... then a steady
     * 5 minutes. The row is never dropped, so once Weaviate recovers the entry is applied on the next eligible
     * attempt; a later reconcile pass is the ultimate backstop for a genuinely poison row.
     */
    private long backoffSeconds(int attempts) {
        int shift = Math.min(attempts - 1, 30);
        long backoff = outboxProperties.baseBackoffSeconds() << shift;
        return Math.min(backoff, outboxProperties.maxBackoffSeconds());
    }
}
