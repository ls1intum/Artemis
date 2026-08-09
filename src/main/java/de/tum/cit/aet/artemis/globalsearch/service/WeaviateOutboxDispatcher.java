package de.tum.cit.aet.artemis.globalsearch.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_SCHEDULING;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZonedDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * slow or hung write never holds a database connection; the outcome is then recorded in a short transaction: on
 * success the row is deleted and, for an upsert, the {@code searchable_entity_sync_state} ledger is refreshed;
 * on failure the row survives with an incremented attempt count and an exponentially backed-off
 * {@code next_attempt_at}, so a Weaviate outage self-heals when Weaviate recovers.
 * <p>
 * A confirmed per-entity write drops all older outbox rows for that entity ({@link #collapseSupersededRows}).
 * Without this, a failed older row deferred by backoff could wake up after a newer row for the same entity
 * already succeeded and overwrite it. This preserves latest-wins for a single writer; two scheduling nodes at
 * once are a misconfiguration, and a later reconcile pass is the backstop.
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
     * Maximum number of rows read per batch. A drain keeps reading batches until one comes back smaller than
     * this, so a burst larger than one batch still drains fully within a single drain call.
     */
    private static final int BATCH_SIZE = 100;

    /**
     * Interval of the safety-net / cross-node tick. On this (scheduling) node the after-commit nudge already
     * makes local enqueues fresh; this tick primarily drains enqueues made on other nodes and covers any
     * missed nudge.
     */
    private static final long DRAIN_INTERVAL_MS = 5000;

    private static final long BASE_BACKOFF_SECONDS = 10;

    private static final long MAX_BACKOFF_SECONDS = 300;

    private final WeaviateOutboxRepository outboxRepository;

    private final SearchableEntitySyncStateRepository syncStateRepository;

    private final SearchableEntityWeaviateService searchableEntityWeaviateService;

    private final TransactionTemplate transactionTemplate;

    /**
     * Serializes drains on this node so the scheduled tick and the after-commit nudge never overlap.
     */
    private final ReentrantLock drainLock = new ReentrantLock();

    public WeaviateOutboxDispatcher(WeaviateOutboxRepository outboxRepository, SearchableEntitySyncStateRepository syncStateRepository,
            SearchableEntityWeaviateService searchableEntityWeaviateService, PlatformTransactionManager transactionManager) {
        this.outboxRepository = outboxRepository;
        this.syncStateRepository = syncStateRepository;
        this.searchableEntityWeaviateService = searchableEntityWeaviateService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * Periodic safety-net drain. Also the path by which enqueues made on non-scheduling nodes reach Weaviate.
     */
    @Scheduled(fixedDelay = DRAIN_INTERVAL_MS)
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
            while (processed == BATCH_SIZE);
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
     * @return the number of rows read (equal to {@link #BATCH_SIZE} while more may remain)
     */
    private int drainBatch() {
        ZonedDateTime now = ZonedDateTime.now();
        List<WeaviateOutboxEntry> batch = outboxRepository.findDueForDispatch(now, BATCH_SIZE);
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
            searchableEntityWeaviateService.applyOutboxEntry(entry);
            transactionTemplate.executeWithoutResult(status -> confirmWrite(entry, now));
        }
        catch (Exception e) {
            ZonedDateTime nextAttempt = now.plusSeconds(backoffSeconds(entry.getAttempts() + 1));
            transactionTemplate.executeWithoutResult(status -> scheduleRetry(entry, nextAttempt));
            log.warn("Failed to apply Weaviate outbox entry {} (attempt {}), retrying after {}: {}", entry.getId(), entry.getAttempts(), nextAttempt, e.getMessage());
        }
    }

    /**
     * Records a confirmed write in one short transaction: collapses superseded rows, refreshes the ledger, and
     * removes the row.
     */
    private void confirmWrite(WeaviateOutboxEntry entry, ZonedDateTime now) {
        collapseSupersededRows(entry);
        refreshSyncLedger(entry, now);
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
     * Refreshes the {@code searchable_entity_sync_state} ledger after a confirmed write. Single-entity upserts
     * record the written content hash; single-entity deletes remove the ledger row so a later reconcile does
     * not treat a deleted entity as still synced. Bulk deletes leave the ledger to a later reconcile pass.
     */
    private void refreshSyncLedger(WeaviateOutboxEntry entry, ZonedDateTime now) {
        if (entry.getOperation() == WeaviateOutboxOperation.UPSERT) {
            String hash = sha256Hex(entry.getPayload());
            syncStateRepository.findByEntityTypeAndEntityId(entry.getEntityType(), entry.getEntityId()).ifPresentOrElse(state -> {
                state.setContentHash(hash);
                state.setSyncedAt(now);
                syncStateRepository.save(state);
            }, () -> syncStateRepository.save(new SearchableEntitySyncState(entry.getEntityType(), entry.getEntityId(), hash, now)));
        }
        else if (entry.getOperation() == WeaviateOutboxOperation.DELETE_ENTITY) {
            syncStateRepository.deleteByEntityTypeAndEntityId(entry.getEntityType(), entry.getEntityId());
        }
    }

    /**
     * Exponential backoff capped at {@link #MAX_BACKOFF_SECONDS}: 10s, 20s, 40s, ... then a steady 5 minutes.
     * The row is never dropped, so once Weaviate recovers the entry is applied on the next eligible attempt;
     * a later reconcile pass is the ultimate backstop for a genuinely poison row.
     */
    private static long backoffSeconds(int attempts) {
        int shift = Math.min(attempts - 1, 30);
        long backoff = BASE_BACKOFF_SECONDS << shift;
        return Math.min(backoff, MAX_BACKOFF_SECONDS);
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        }
        catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed to be available on every JVM.
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
