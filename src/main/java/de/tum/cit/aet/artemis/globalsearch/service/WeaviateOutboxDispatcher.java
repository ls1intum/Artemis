package de.tum.cit.aet.artemis.globalsearch.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_SCHEDULING;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import de.tum.cit.aet.artemis.globalsearch.config.WeaviateEnabled;
import de.tum.cit.aet.artemis.globalsearch.domain.SearchableEntitySyncState;
import de.tum.cit.aet.artemis.globalsearch.domain.WeaviateOutboxEntry;
import de.tum.cit.aet.artemis.globalsearch.repository.SearchableEntitySyncStateRepository;
import de.tum.cit.aet.artemis.globalsearch.repository.WeaviateOutboxRepository;

/**
 * Single-writer dispatcher that drains the {@code weaviate_outbox} and performs the actual writes to the
 * shared {@code SearchableEntities} Weaviate collection.
 * <p>
 * It runs only on the scheduling node ({@code @Profile(PROFILE_SCHEDULING)}), which Artemis requires to be
 * exactly one instance, so it is the single writer to the collection. It reads due rows in id (enqueue) order
 * with a plain query and processes them sequentially. Each Weaviate write happens outside any transaction, so a
 * slow or hung write never holds a database connection; the outcome is then recorded in a short transaction: on
 * success the row is deleted and, for an upsert, the {@code searchable_entity_sync_state} ledger is refreshed;
 * on failure the row survives with an incremented attempt count and an exponentially backed-off
 * {@code next_attempt_at}, so a Weaviate outage self-heals when Weaviate recovers.
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
 * net) and an after-commit {@link #onOutboxEnqueued(WeaviateOutboxEnqueuedEvent)} nudge for the freshness of
 * enqueues made on this node. A {@link ReentrantLock} guarantees only one drain runs at a time on this node.
 * <p>
 * Being the single writer, it needs no lock or lease to protect a row during processing: the read does not
 * mutate the row, so a crash mid-batch simply leaves it to be re-read and re-applied (writes are idempotent).
 * Only the short outcome writes use a transaction, via a {@link TransactionTemplate}.
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
     * Maximum number of rows read per batch. A drain keeps reading batches until one comes back smaller than
     * this, so a burst larger than one batch still drains fully within a single drain call.
     * Overridable via {@code artemis.weaviate.outbox.batch-size} (default 100).
     */
    private final int batchSize;

    /**
     * Base of the exponential retry backoff, overridable via {@code artemis.weaviate.outbox.base-backoff-seconds}
     * (default 10).
     */
    private final long baseBackoffSeconds;

    /**
     * Cap of the exponential retry backoff, overridable via {@code artemis.weaviate.outbox.max-backoff-seconds}
     * (default 300).
     */
    private final long maxBackoffSeconds;

    private final WeaviateOutboxRepository outboxRepository;

    private final SearchableEntitySyncStateRepository syncStateRepository;

    private final SearchableEntityWeaviateService searchableEntityWeaviateService;

    private final TransactionTemplate transactionTemplate;

    /**
     * Serializes drains on this node so the scheduled tick and the after-commit nudge never overlap.
     */
    private final ReentrantLock drainLock = new ReentrantLock();

    public WeaviateOutboxDispatcher(WeaviateOutboxRepository outboxRepository, SearchableEntitySyncStateRepository syncStateRepository,
            SearchableEntityWeaviateService searchableEntityWeaviateService, PlatformTransactionManager transactionManager,
            @Value("${artemis.weaviate.outbox.batch-size:100}") int batchSize, @Value("${artemis.weaviate.outbox.base-backoff-seconds:10}") long baseBackoffSeconds,
            @Value("${artemis.weaviate.outbox.max-backoff-seconds:300}") long maxBackoffSeconds) {
        this.outboxRepository = outboxRepository;
        this.syncStateRepository = syncStateRepository;
        this.searchableEntityWeaviateService = searchableEntityWeaviateService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.batchSize = batchSize;
        this.baseBackoffSeconds = baseBackoffSeconds;
        this.maxBackoffSeconds = maxBackoffSeconds;
    }

    /**
     * Periodic safety-net drain, and the path by which enqueues made on non-scheduling nodes reach Weaviate. On the
     * scheduling node the after-commit nudge already makes local enqueues fresh, so this tick primarily drains
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
     * After-commit nudge so an enqueue made on this node is applied promptly, keeping freshness comparable to
     * the previous fire-and-forget write. Runs asynchronously off the caller's thread. {@code fallbackExecution}
     * covers enqueues made outside any transaction (REST resources), which commit immediately on save.
     *
     * @param event the marker event published by {@code SearchableEntityWeaviateService} after an enqueue
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
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
            while (processed == batchSize);
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
     * @return the number of rows read (equal to {@link #batchSize} while more may remain)
     */
    private int drainBatch() {
        ZonedDateTime now = ZonedDateTime.now();
        List<WeaviateOutboxEntry> batch = outboxRepository.findDueForDispatch(now, batchSize);
        for (WeaviateOutboxEntry entry : batch) {
            processEntry(entry, now);
        }
        return batch.size();
    }

    /**
     * Applies a single outbox entry to Weaviate outside any transaction, then records the outcome in a short
     * transaction: {@link #confirmWrite} on success, {@link #scheduleRetry} on failure.
     */
    private void processEntry(WeaviateOutboxEntry entry, ZonedDateTime now) {
        try {
            Optional<String> writtenContentHash = searchableEntityWeaviateService.applyOutboxEntry(entry);
            logApplied(entry, writtenContentHash);
            if (entry.getAttempts() > 0) {
                log.info("Weaviate outbox entry {} succeeded after {} failed attempt(s)", entry.getId(), entry.getAttempts());
            }
            transactionTemplate.executeWithoutResult(status -> confirmWrite(entry, now, writtenContentHash));
        }
        catch (Exception e) {
            int attempt = entry.getAttempts() + 1;
            // Compute the retry time now, not from the batch-start timestamp: a slow write could otherwise make the backoff already due.
            ZonedDateTime nextAttempt = ZonedDateTime.now().plusSeconds(backoffSeconds(attempt));
            transactionTemplate.executeWithoutResult(status -> scheduleRetry(entry, nextAttempt));
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
     * Records a confirmed write in one short transaction: collapses superseded rows, refreshes the ledger, and
     * removes the row.
     */
    private void confirmWrite(WeaviateOutboxEntry entry, ZonedDateTime now, Optional<String> writtenContentHash) {
        collapseSupersededRows(entry);
        refreshSyncLedger(entry, now, writtenContentHash);
        outboxRepository.delete(entry);
    }

    /**
     * Records a failed write in one short transaction: increments the attempt count and backs off.
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
     * not treat it as still synced. Bulk deletes (null entity id) leave the ledger to a later reconcile pass.
     */
    private void refreshSyncLedger(WeaviateOutboxEntry entry, ZonedDateTime now, Optional<String> writtenContentHash) {
        if (writtenContentHash.isPresent()) {
            String hash = writtenContentHash.get();
            syncStateRepository.findByEntityTypeAndEntityId(entry.getEntityType(), entry.getEntityId()).ifPresentOrElse(state -> {
                state.setContentHash(hash);
                state.setSyncedAt(now);
                syncStateRepository.save(state);
            }, () -> syncStateRepository.save(new SearchableEntitySyncState(entry.getEntityType(), entry.getEntityId(), hash, now)));
        }
        else if (entry.getEntityId() != null) {
            syncStateRepository.deleteByEntityTypeAndEntityId(entry.getEntityType(), entry.getEntityId());
        }
    }

    /**
     * Exponential backoff capped at {@code maxBackoffSeconds}: with the defaults, 10s, 20s, 40s, ... then a steady
     * 5 minutes. The row is never dropped, so once Weaviate recovers the entry is applied on the next eligible
     * attempt; a later reconcile pass is the ultimate backstop for a genuinely poison row.
     */
    private long backoffSeconds(int attempts) {
        int shift = Math.min(attempts - 1, 30);
        long backoff = baseBackoffSeconds << shift;
        return Math.min(backoff, maxBackoffSeconds);
    }
}
