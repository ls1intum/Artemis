package de.tum.cit.aet.artemis.globalsearch.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_SCHEDULING;

import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
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
import de.tum.cit.aet.artemis.globalsearch.config.WeaviateDispatcherExecutorConfiguration;
import de.tum.cit.aet.artemis.globalsearch.config.WeaviateEmbeddingProperties;
import de.tum.cit.aet.artemis.globalsearch.config.WeaviateEnabled;
import de.tum.cit.aet.artemis.globalsearch.config.WeaviateMigrationProperties;
import de.tum.cit.aet.artemis.globalsearch.config.WeaviateOutboxProperties;
import de.tum.cit.aet.artemis.globalsearch.config.WeaviateReconcileProperties;
import de.tum.cit.aet.artemis.globalsearch.domain.SearchableEntitySyncState;
import de.tum.cit.aet.artemis.globalsearch.domain.WeaviateOutboxEntry;
import de.tum.cit.aet.artemis.globalsearch.exception.EmbeddingFailedException;
import de.tum.cit.aet.artemis.globalsearch.repository.SearchableEntitySyncStateRepository;
import de.tum.cit.aet.artemis.globalsearch.repository.WeaviateOutboxRepository;
import de.tum.cit.aet.artemis.globalsearch.service.embedding.SearchableEntityEmbeddingClient;

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

    private final WeaviateReconcileProperties reconcileProperties;

    private final SearchableEntityEmbeddingClient embeddingClient;

    /**
     * Pace of background work (reconcile rows); writes for user actions are not limited by it.
     */
    private final BackgroundWriteBudget backgroundBudget;

    /**
     * Rows of one background step.
     */
    static final int BACKGROUND_STEP_ROWS = 25;

    /**
     * Vectors computed for rows that a step could not complete before its request budget ran out, by chunk text; the next
     * step starts with them. It holds at most the chunks of one step's rows and is only used by the drain thread.
     */
    private final Map<String, float[]> carriedVectors = new HashMap<>();

    /**
     * Number of bulk deletes confirmed so far. A bulk delete has no entity key, so the per-entity guard cannot tell which
     * waiting background rows it supersedes; a row prepared before it is therefore not written but re-read later.
     */
    private long confirmedBulkDeletes;

    /**
     * A background row waiting for its embeddings, with the number of bulk deletes confirmed when it was prepared.
     */
    private record WaitingRow(SearchableEntityWeaviateService.PreparedEntry prepared, long bulkDeletesAtPrepare) {
    }

    /**
     * Checks the index against the configuration and rebuilds it when needed; runs before the first write.
     */
    private final SearchableEntityIndexRebuildService rebuildService;

    /**
     * Failed index checks that log a warning; later ones log an error.
     */
    private final int indexCheckWarnAttempts;

    private final long indexCheckRetryDelayMillis;

    /**
     * Whether the index has been checked against the configuration. Until then nothing is written, so no row lands in a
     * collection that is about to be rebuilt. Only the drain thread reads and writes it, under {@link #drainLock}.
     */
    private boolean indexReady;

    /**
     * Earliest time of the next index check, in epoch milliseconds.
     */
    private long nextIndexCheckAt;

    private int failedIndexChecks;

    /**
     * Serializes drains on this node so the scheduled tick and the after-commit nudge never overlap.
     */
    private final ReentrantLock drainLock = new ReentrantLock();

    /**
     * Records that a scheduled tick or after-commit nudge requested a drain. The lock holder consumes this marker
     * after each completed pass, so a nudge arriving during a short batch is drained before the holder returns.
     */
    private final AtomicBoolean drainPending = new AtomicBoolean();

    public WeaviateOutboxDispatcher(WeaviateOutboxRepository outboxRepository, SearchableEntitySyncStateRepository syncStateRepository,
            SearchableEntityWeaviateService searchableEntityWeaviateService, WeaviateOutboxProperties outboxProperties, WeaviateReconcileProperties reconcileProperties,
            WeaviateEmbeddingProperties embeddingProperties, SearchableEntityEmbeddingClient embeddingClient, SearchableEntityIndexRebuildService rebuildService,
            WeaviateMigrationProperties migrationProperties) {
        this.outboxRepository = outboxRepository;
        this.syncStateRepository = syncStateRepository;
        this.searchableEntityWeaviateService = searchableEntityWeaviateService;
        this.outboxProperties = outboxProperties;
        this.reconcileProperties = reconcileProperties;
        this.embeddingClient = embeddingClient;
        this.backgroundBudget = new BackgroundWriteBudget(embeddingProperties.backgroundMaxRowsPerMinute(), embeddingProperties.backgroundMaxRequestsPerMinute(),
                Clock.systemUTC());
        this.rebuildService = rebuildService;
        this.indexCheckWarnAttempts = migrationProperties.maxAttempts();
        this.indexCheckRetryDelayMillis = migrationProperties.retryDelaySeconds() * 1000;
        this.nextIndexCheckAt = System.currentTimeMillis() + migrationProperties.initialDelaySeconds() * 1000;
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
     * so the row is already visible to the drain when this listener runs. Should a caller ever publish
     * from inside a transaction of its own, the nudge simply sees no row yet and the scheduled tick drains it.
     *
     * @param event the marker event published by {@code SearchableEntityWeaviateService} after an enqueue
     */
    @Async(WeaviateDispatcherExecutorConfiguration.EXECUTOR_NAME)
    @EventListener
    public void onOutboxEnqueued(WeaviateOutboxEnqueuedEvent event) {
        drain();
    }

    /**
     * Drains all currently due outbox rows, one batch at a time. At most one drain runs at a time on this node. Every
     * trigger marks work as pending before it competes for the lock; the holder consumes that mark after a completed
     * pass, so a nudge arriving while it processes a short batch is not delayed until the next scheduled tick.
     */
    public void drain() {
        drainPending.set(true);
        do {
            if (!drainLock.tryLock()) {
                return;
            }
            try {
                if (!ensureIndexReady()) {
                    return;
                }
                // Give the drain thread an authorization context, matching the previous @Async write behavior.
                SecurityUtils.setAuthorizationObject();
                while (drainPending.getAndSet(false)) {
                    drainAllDueRows();
                }
            }
            catch (Exception e) {
                // The database was unavailable while reading or recording. Give up for now; the next tick retries.
                // In particular, do not spin on a pending nudge after a failed database access.
                log.error("Weaviate outbox drain aborted: {}", e.getMessage(), e);
                return;
            }
            finally {
                drainLock.unlock();
            }
        }
        while (drainPending.get());
    }

    /**
     * One drain turn. Writes for user actions ({@code LIVE} rows) always go first: all due ones are applied, then one
     * small background step runs, then due {@code LIVE} rows again, and so on. The turn ends when no background row is
     * due or a background budget is exhausted; the turn never waits for a budget, so a {@code LIVE} row arriving later is
     * applied on the next nudge without delay.
     * <p>
     * {@code confirmedInTurn} records, per entity, the highest outbox id confirmed in this turn. An older entry for the
     * same entity that is still in memory (read before the newer one was confirmed) is skipped and removed as superseded,
     * so it can never overwrite the newer write or lower its {@code source_seq}.
     */
    private void drainAllDueRows() {
        Map<String, Long> confirmedInTurn = new HashMap<>();
        while (true) {
            applyDueLiveRows(confirmedInTurn);
            if (!runBackgroundStep(confirmedInTurn)) {
                return;
            }
        }
    }

    private void applyDueLiveRows(Map<String, Long> confirmedInTurn) {
        List<WeaviateOutboxEntry> batch;
        do {
            ZonedDateTime now = ZonedDateTime.now();
            batch = outboxRepository.findDueLiveForDispatch(now, outboxProperties.batchSize());
            for (WeaviateOutboxEntry entry : batch) {
                if (!skipIfSuperseded(entry, confirmedInTurn)) {
                    processEntry(entry, now, confirmedInTurn);
                }
            }
        }
        while (batch.size() == outboxProperties.batchSize());
    }

    /**
     * Applies up to {@link #BACKGROUND_STEP_ROWS} due background rows. Each row takes one row budget unit. The new chunks
     * of the rows are collected and embedded together, {@code max-inputs-per-request} per request, and each request takes
     * one request budget unit. Between two requests, due {@code LIVE} rows are applied, so a write for a user action waits
     * for at most one embedding request. A row is written as soon as all its chunks have vectors; a write for the same
     * entity confirmed meanwhile supersedes it.
     * <p>
     * When the request budget runs out, the step ends: rows that are not complete stay due, untouched, and the vectors
     * already computed for them are kept for the next step ({@link #carriedVectors}), so a row that needs more requests
     * than one minute allows still completes over several steps.
     *
     * @return whether the step used its whole page within the budget and another step may follow in this turn
     */
    private boolean runBackgroundStep(Map<String, Long> confirmedInTurn) {
        if (!backgroundBudget.hasRowCapacity()) {
            return false;
        }
        ZonedDateTime now = ZonedDateTime.now();
        List<WeaviateOutboxEntry> rows = outboxRepository.findDueBackgroundForDispatch(now, BACKGROUND_STEP_ROWS);
        Map<String, float[]> embedded = new HashMap<>(carriedVectors);
        carriedVectors.clear();
        Set<String> failedChunks = new HashSet<>();
        Set<String> pendingChunks = new LinkedHashSet<>();
        List<WaitingRow> waiting = new ArrayList<>();
        boolean rowBudgetExhausted = false;
        boolean requestBudgetExhausted = false;
        for (WeaviateOutboxEntry entry : rows) {
            if (skipIfSuperseded(entry, confirmedInTurn)) {
                continue;
            }
            if (!backgroundBudget.tryAcquireRow()) {
                rowBudgetExhausted = true;
                break;
            }
            try {
                SearchableEntityWeaviateService.PreparedEntry prepared = searchableEntityWeaviateService.prepareOutboxEntry(entry);
                missingChunks(prepared).stream().filter(chunk -> !embedded.containsKey(chunk)).forEach(pendingChunks::add);
                waiting.add(new WaitingRow(prepared, confirmedBulkDeletes));
            }
            catch (Exception e) {
                recordFailure(entry, e);
                continue;
            }
            while (pendingChunks.size() >= embeddingClient.maxInputsPerRequest() && !requestBudgetExhausted) {
                requestBudgetExhausted = !embedNextRequest(pendingChunks, embedded, failedChunks, confirmedInTurn);
                applyCompleteRows(waiting, embedded, failedChunks, now, confirmedInTurn);
            }
            if (requestBudgetExhausted) {
                break;
            }
        }
        // The rows taken so far are embedded even when the row budget ran out; only the request budget stops this.
        while (!pendingChunks.isEmpty() && !requestBudgetExhausted) {
            requestBudgetExhausted = !embedNextRequest(pendingChunks, embedded, failedChunks, confirmedInTurn);
        }
        applyCompleteRows(waiting, embedded, failedChunks, now, confirmedInTurn);
        waiting.forEach(row -> carryVectors(row, embedded));
        return !rowBudgetExhausted && !requestBudgetExhausted && rows.size() == BACKGROUND_STEP_ROWS;
    }

    private static List<String> missingChunks(SearchableEntityWeaviateService.PreparedEntry prepared) {
        return prepared instanceof SearchableEntityWeaviateService.PreparedUpsert upsert ? upsert.row().missingChunks() : List.of();
    }

    /**
     * Sends one embedding request for the first {@code max-inputs-per-request} pending chunks, then applies the due
     * {@code LIVE} rows. A failed request marks its chunks as failed, so their rows are retried with backoff.
     *
     * @return false if the request budget is used up (nothing was sent)
     */
    private boolean embedNextRequest(Set<String> pendingChunks, Map<String, float[]> embedded, Set<String> failedChunks, Map<String, Long> confirmedInTurn) {
        if (!backgroundBudget.tryAcquireRequest()) {
            return false;
        }
        List<String> batch = pendingChunks.stream().limit(embeddingClient.maxInputsPerRequest()).toList();
        batch.forEach(pendingChunks::remove);
        try {
            List<float[]> vectors = embeddingClient.embedDocuments(batch);
            for (int i = 0; i < batch.size(); i++) {
                embedded.put(batch.get(i), vectors.get(i));
            }
        }
        catch (Exception e) {
            failedChunks.addAll(batch);
            log.warn("Embedding request for {} chunks failed: {}", batch.size(), e.getMessage());
        }
        applyDueLiveRows(confirmedInTurn);
        return true;
    }

    /**
     * Drops every waiting row that a confirmed write for the same entity superseded, leaves every waiting row prepared
     * before a confirmed bulk delete due for a later step, writes every waiting row whose chunks all have vectors, and
     * records a failure for every row with a failed chunk; all of them leave the waiting list.
     */
    private void applyCompleteRows(List<WaitingRow> waiting, Map<String, float[]> embedded, Set<String> failedChunks, ZonedDateTime now, Map<String, Long> confirmedInTurn) {
        var iterator = waiting.iterator();
        while (iterator.hasNext()) {
            WaitingRow row = iterator.next();
            SearchableEntityWeaviateService.PreparedEntry prepared = row.prepared();
            List<String> missing = missingChunks(prepared);
            // A write for the same entity confirmed meanwhile (for example a user's delete) makes the row obsolete,
            // whether or not its chunks could be embedded.
            if (skipIfSuperseded(prepared.entry(), confirmedInTurn)) {
                iterator.remove();
            }
            else if (row.bulkDeletesAtPrepare() != confirmedBulkDeletes) {
                // A bulk delete applied meanwhile may have removed this entity; leave the row due so it is re-derived
                // from the database in a later step, and keep its vectors so nothing is embedded twice.
                iterator.remove();
                carryVectors(row, embedded);
            }
            else if (missing.stream().anyMatch(failedChunks::contains)) {
                iterator.remove();
                recordFailure(prepared.entry(), new EmbeddingFailedException("An embedding request for the row failed"));
            }
            else if (missing.stream().allMatch(embedded::containsKey)) {
                iterator.remove();
                applyPrepared(prepared, embedded, now, confirmedInTurn);
            }
        }
    }

    private void carryVectors(WaitingRow row, Map<String, float[]> embedded) {
        missingChunks(row.prepared()).stream().filter(embedded::containsKey).forEach(chunk -> carriedVectors.put(chunk, embedded.get(chunk)));
    }

    /**
     * Skips an entry whose entity already had a newer write confirmed in this turn, and removes it as superseded.
     *
     * @return whether the entry was skipped
     */
    private boolean skipIfSuperseded(WeaviateOutboxEntry entry, Map<String, Long> confirmedInTurn) {
        if (entry.getEntityId() == null) {
            return false;
        }
        Long confirmed = confirmedInTurn.get(entityKey(entry));
        if (confirmed == null || confirmed < entry.getId()) {
            return false;
        }
        outboxRepository.delete(entry);
        return true;
    }

    private static String entityKey(WeaviateOutboxEntry entry) {
        return entry.getEntityType() + ":" + entry.getEntityId();
    }

    /**
     * Makes sure the index matches the configuration before anything is written: the first drain after the initial delay
     * ({@code artemis.weaviate.migration.initial-delay-seconds}) runs {@link SearchableEntityIndexRebuildService}, which
     * rebuilds the collection if its signature does not match. It runs under the drain lock, so no write can land in a
     * collection while it is replaced; changes made meanwhile stay queued and are applied afterwards, re-derived from the
     * database. A failed check (for example, Weaviate being unavailable) is retried every
     * {@code retry-delay-seconds} until it succeeds; until then the outbox keeps every write.
     *
     * @return whether the index is ready for writes
     */
    private boolean ensureIndexReady() {
        if (indexReady) {
            return true;
        }
        long now = System.currentTimeMillis();
        if (now < nextIndexCheckAt) {
            return false;
        }
        try {
            rebuildService.ensureIndexMatchesConfiguration();
            indexReady = true;
            return true;
        }
        catch (Exception exception) {
            failedIndexChecks++;
            nextIndexCheckAt = now + indexCheckRetryDelayMillis;
            if (failedIndexChecks < indexCheckWarnAttempts) {
                log.warn("Global search index check attempt {} failed; retrying in {}s. Index writes stay queued until it succeeds.", failedIndexChecks,
                        indexCheckRetryDelayMillis / 1000, exception);
            }
            else {
                log.error("Global search index check attempt {} failed; retrying in {}s. Index writes stay queued until it succeeds. Check that Weaviate is reachable.",
                        failedIndexChecks, indexCheckRetryDelayMillis / 1000, exception);
            }
            return false;
        }
    }

    /**
     * Applies a single outbox entry to Weaviate outside any transaction, embedding its missing chunks right away, then
     * records the outcome: {@link #confirmWrite} on success, {@link #scheduleRetry} on failure.
     */
    private void processEntry(WeaviateOutboxEntry entry, ZonedDateTime now, Map<String, Long> confirmedInTurn) {
        try {
            Optional<String> writtenContentHash = searchableEntityWeaviateService.applyOutboxEntry(entry);
            recordSuccess(entry, now, writtenContentHash, confirmedInTurn);
        }
        catch (Exception e) {
            recordFailure(entry, e);
        }
    }

    private void applyPrepared(SearchableEntityWeaviateService.PreparedEntry prepared, Map<String, float[]> embedded, ZonedDateTime now, Map<String, Long> confirmedInTurn) {
        try {
            Optional<String> writtenContentHash = searchableEntityWeaviateService.applyPrepared(prepared, embedded);
            recordSuccess(prepared.entry(), now, writtenContentHash, confirmedInTurn);
        }
        catch (Exception e) {
            recordFailure(prepared.entry(), e);
        }
    }

    private void recordSuccess(WeaviateOutboxEntry entry, ZonedDateTime now, Optional<String> writtenContentHash, Map<String, Long> confirmedInTurn) {
        logApplied(entry, writtenContentHash);
        if (entry.getAttempts() > 0) {
            log.info("Weaviate outbox entry {} succeeded after {} failed attempt(s)", entry.getId(), entry.getAttempts());
        }
        confirmWrite(entry, now, writtenContentHash);
        if (entry.getEntityId() != null) {
            confirmedInTurn.merge(entityKey(entry), entry.getId(), Math::max);
        }
        else {
            confirmedBulkDeletes++;
        }
    }

    private void recordFailure(WeaviateOutboxEntry entry, Exception e) {
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
     * not treat it as still synced. Bulk deletes (null entity id) leave the ledger to a later reconcile pass.
     * <p>
     * A type no reconcile pass manages ({@link WeaviateReconcileProperties#entityTypes()}) never gets a ledger row
     * at all: the ledger exists only so a reconcile pass can detect a never-indexed or drifted entity, and nothing
     * reads it back for a type reconcile does not cover. Writing one anyway would only grow the table forever,
     * since a bulk delete has no entity id to clean a specific row up with, and no reconcile pass ever revisits an
     * unmanaged type to notice and remove a stale one either. Post and answer post are the only unmanaged types
     * today (see the property's own javadoc for why).
     */
    private void refreshSyncLedger(WeaviateOutboxEntry entry, ZonedDateTime now, Optional<String> writtenContentHash) {
        if (!reconcileProperties.managesEntityType(entry.getEntityType())) {
            return;
        }
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
