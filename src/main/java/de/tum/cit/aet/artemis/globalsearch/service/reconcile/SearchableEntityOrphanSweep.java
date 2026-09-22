package de.tum.cit.aet.artemis.globalsearch.service.reconcile;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.globalsearch.config.WeaviateEnabled;
import de.tum.cit.aet.artemis.globalsearch.config.WeaviateReconcileProperties;
import de.tum.cit.aet.artemis.globalsearch.domain.ReconcilePass;
import de.tum.cit.aet.artemis.globalsearch.domain.SearchableEntityReconcileState;
import de.tum.cit.aet.artemis.globalsearch.domain.SearchableEntitySyncState;
import de.tum.cit.aet.artemis.globalsearch.domain.WeaviateOutboxOrigin;
import de.tum.cit.aet.artemis.globalsearch.exception.WeaviateException;
import de.tum.cit.aet.artemis.globalsearch.repository.SearchableEntityReconcileStateRepository;
import de.tum.cit.aet.artemis.globalsearch.repository.SearchableEntitySyncStateRepository;
import de.tum.cit.aet.artemis.globalsearch.service.SearchableEntityIndexScanService;
import de.tum.cit.aet.artemis.globalsearch.service.SearchableEntityIndexScanService.IndexedRow;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Walks the index itself and repairs what it finds there, which is the only check that reads what is actually
 * stored rather than what the database and the ledger agree about.
 * <p>
 * It answers two questions the other passes cannot. Does this row still have an entity behind it, or is it an
 * orphan left by a lost delete? And does its stored content match what was last written for it, or has the index
 * diverged through a restore, a write that reported success without persisting, or an edit made outside Artemis?
 * <p>
 * This is the only pass that removes anything, so its guards matter more than its logic. It refuses to act on a
 * type it does not manage and on a type whose module is disabled. It also never deletes off one suspicious
 * reading: a type whose orphan ratio comes back over {@link WeaviateReconcileProperties#orphanAbortRatio()} is
 * only logged and skipped the first time, not acted on. The deterministic UUID scheme scatters one type's rows
 * across the whole keyspace (see {@code WeaviateUuidUtil}), so consecutive ticks sample different, independent
 * slices of it rather than reading the same rows twice; only once the same type comes back over the ratio again,
 * on a later tick, does the pass trust the reading enough to act, since a stale scan or a bug in the eligibility
 * check is very unlikely to reproduce identically on an unrelated later sample. Once trusted, a type stays
 * trusted on every later tick, however many it takes to fully drain a genuinely large batch, until a tick finds
 * it healthy again. It also cannot remove more than a set number of rows in one tick, which bounds the damage of
 * being wrong about all of the above even once trusted. One type failing every guard never withholds a tick's
 * progress from the others: each type in a scanned page is judged and acted on independently, and the cursor
 * always advances regardless of the outcome, so no type can block the rest of the collection from ever being
 * looked at again.
 * <p>
 * Rows written while a scan is in flight need no separate fence. Removal is decided against the database as it is
 * now rather than against anything the scan read, so a row that changed underneath makes no difference to it. A
 * content comparison can go stale, but the worst it produces is a rewrite that was already queued, and queuing
 * refuses to duplicate work already waiting for an entity.
 */
@Lazy
@Service
@Conditional(WeaviateEnabled.class)
public class SearchableEntityOrphanSweep {

    private static final Logger log = LoggerFactory.getLogger(SearchableEntityOrphanSweep.class);

    private final SearchableEntityIndexScanService indexScanService;

    private final SearchableEntityIdEnumerator idEnumerator;

    private final SearchableEntitySyncStateRepository syncStateRepository;

    private final SearchableEntityReconcileStateRepository reconcileStateRepository;

    private final ReconcileEnqueueService enqueueService;

    private final WeaviateReconcileProperties reconcileProperties;

    private final JsonMapper objectMapper;

    public SearchableEntityOrphanSweep(SearchableEntityIndexScanService indexScanService, SearchableEntityIdEnumerator idEnumerator,
            SearchableEntitySyncStateRepository syncStateRepository, SearchableEntityReconcileStateRepository reconcileStateRepository, ReconcileEnqueueService enqueueService,
            WeaviateReconcileProperties reconcileProperties, JsonMapper objectMapper) {
        this.indexScanService = indexScanService;
        this.idEnumerator = idEnumerator;
        this.syncStateRepository = syncStateRepository;
        this.reconcileStateRepository = reconcileStateRepository;
        this.enqueueService = enqueueService;
        this.reconcileProperties = reconcileProperties;
        this.objectMapper = objectMapper;
    }

    /**
     * Reads one bounded slice of the index, repairs what is wrong with it, and saves where it got to. Always
     * advances, whatever it found: a type withheld by the abort ratio is remembered for next time, not left to
     * block every type and every row behind it in the scan order forever.
     */
    public void sweep() {
        String runId = ReconcileRunId.next();
        if (reconcileProperties.entityTypes().isEmpty() || !enqueueService.canEnqueue()) {
            return;
        }

        SearchableEntityReconcileState state = reconcileStateRepository.findByPass(ReconcilePass.ORPHAN).orElseGet(() -> new SearchableEntityReconcileState(ReconcilePass.ORPHAN));

        var slice = indexScanService.scanFrom(state.getPositionCursor(), reconcileProperties.orphanPageSize(), reconcileProperties.orphanPagesPerTick());
        if (slice.rows().isEmpty()) {
            finishCycle(runId, state);
            return;
        }

        Map<String, Integer> streaks = deserializeStreaks(state.getOrphanAbortStreaks());
        Outcome outcome = repair(runId, slice.rows(), streaks);
        state.setOrphanAbortStreaks(serializeStreaks(streaks));

        state.recordProgress(slice.rows().size(), outcome.repaired(), outcome.removed());
        if (slice.nextCursor() == null) {
            finishCycle(runId, state);
            return;
        }
        state.setPositionCursor(slice.nextCursor());
        reconcileStateRepository.save(state);

        if (outcome.repaired() > 0 || outcome.removed() > 0) {
            log.info("[index {}] checked {} rows: {} disagreed with what was last written, {} had no entity behind them", runId, slice.rows().size(), outcome.repaired(),
                    outcome.removed());
        }
    }

    /**
     * Decides what to do with each scanned row, grouping by type so each type costs one lookup rather than one per
     * row. Each type is judged and acted on independently, in the same pass: one type withheld by the abort ratio
     * must never cost another, healthy type its repairs for the tick.
     *
     * @param streaks each type's consecutive-tick-over-ratio count, read before this call and updated in place so
     *                    the caller can persist it
     * @return what was queued
     */
    private Outcome repair(String runId, List<IndexedRow> rows, Map<String, Integer> streaks) {
        // A LinkedHashMap keeps type processing order reproducible (scan order) rather than left to HashMap's
        // unspecified iteration, which matters now that log lines and the delete/repair caps are order-sensitive.
        Map<String, List<IndexedRow>> rowsByType = rows.stream().filter(row -> row.entityType() != null && row.entityId() != null)
                .collect(Collectors.groupingBy(IndexedRow::entityType, LinkedHashMap::new, Collectors.toCollection(ArrayList::new)));

        long repaired = 0;
        long removed = 0;
        for (var entry : rowsByType.entrySet()) {
            String entityType = entry.getKey();
            if (!reconcileProperties.managesEntityType(entityType)) {
                // Removing rows of a type no pass repairs would delete an orphaned post while nothing ever re-adds
                // a missing one, which is worse than leaving the type alone entirely.
                log.debug("[index {}] skipping {} rows: the type is not managed", runId, entityType);
                continue;
            }

            List<IndexedRow> candidates = entry.getValue();
            Optional<Set<Long>> shouldBeIndexed = idEnumerator.indexableIdsAmong(entityType, candidates.stream().map(IndexedRow::entityId).toList());
            if (shouldBeIndexed.isEmpty()) {
                // Nothing is known about this type, so every row of it would look orphaned.
                log.debug("[index {}] skipping {} rows: its module is disabled", runId, entityType);
                continue;
            }

            List<IndexedRow> orphans = candidates.stream().filter(row -> !shouldBeIndexed.get().contains(row.entityId())).toList();
            if (!trusted(runId, entityType, orphans.size(), candidates.size(), streaks)) {
                continue;
            }

            removed += remove(runId, entityType, orphans, removed);
            repaired += repairDivergent(runId, entityType, candidates, orphans, repaired);
        }
        return new Outcome(repaired, removed);
    }

    /**
     * Whether this type's scanned candidates are safe to act on, updating its streak in {@code streaks} as a side
     * effect.
     * <p>
     * A ratio at or under the threshold is unremarkable: the type is healthy, any earlier streak is cleared, and
     * processing proceeds as normal. A ratio over the threshold is trusted only once it has now happened twice in
     * a row, since the previous tick that examined this type also found it over the ratio; a single reading, on
     * its own, is refused and remembered instead of acted on.
     */
    private boolean trusted(String runId, String entityType, int orphanCount, int candidateCount, Map<String, Integer> streaks) {
        if (candidateCount == 0 || orphanCount == 0) {
            streaks.remove(entityType);
            return true;
        }
        double ratio = (double) orphanCount / candidateCount;
        if (ratio <= reconcileProperties.orphanAbortRatio()) {
            streaks.remove(entityType);
            return true;
        }

        int previousStreak = streaks.getOrDefault(entityType, 0);
        streaks.put(entityType, previousStreak + 1);
        if (previousStreak >= 1) {
            log.warn("[index {}] {} of {} scanned {} rows had no entity behind them, above the {} threshold for the second time in a row; proceeding", runId, orphanCount,
                    candidateCount, entityType, reconcileProperties.orphanAbortRatio());
            return true;
        }
        log.error("[index {}] {} of {} scanned {} rows had no entity behind them, above the {} threshold; skipping this type this tick, acting only if it happens again", runId,
                orphanCount, candidateCount, entityType, reconcileProperties.orphanAbortRatio());
        return false;
    }

    private long remove(String runId, String entityType, List<IndexedRow> orphans, long alreadyRemoved) {
        long removed = 0;
        for (IndexedRow orphan : orphans) {
            if (alreadyRemoved + removed >= reconcileProperties.orphanDeleteCapPerTick()) {
                log.warn("[index {}] stopped at its per-tick removal limit of {}; the rest is left for the next tick", runId, reconcileProperties.orphanDeleteCapPerTick());
                break;
            }
            if (enqueueService.enqueueDelete(entityType, orphan.entityId(), WeaviateOutboxOrigin.RECONCILE_ORPHAN)) {
                removed++;
            }
        }
        return removed;
    }

    /**
     * Queues a rewrite for rows whose stored content disagrees with what the ledger says was last written, and for
     * rows carrying no hash at all, which are simply ones nothing has verified yet.
     */
    private long repairDivergent(String runId, String entityType, List<IndexedRow> candidates, List<IndexedRow> orphans, long alreadyRepaired) {
        Set<Long> orphanIds = orphans.stream().map(IndexedRow::entityId).collect(Collectors.toSet());
        List<IndexedRow> live = candidates.stream().filter(row -> !orphanIds.contains(row.entityId())).toList();
        if (live.isEmpty()) {
            return 0;
        }

        Map<Long, String> ledgerHashes = new HashMap<>();
        for (SearchableEntitySyncState ledgerRow : syncStateRepository.findAllByEntityTypeAndEntityIdIn(entityType, live.stream().map(IndexedRow::entityId).toList())) {
            ledgerHashes.put(ledgerRow.getEntityId(), ledgerRow.getContentHash());
        }

        long repaired = 0;
        for (IndexedRow row : live) {
            if (alreadyRepaired + repaired >= reconcileProperties.orphanRepairCapPerTick()) {
                log.warn("[index {}] stopped at its per-tick repair limit of {}; the rest is left for the next tick", runId, reconcileProperties.orphanRepairCapPerTick());
                break;
            }
            String ledgerHash = ledgerHashes.get(row.entityId());
            if (ledgerHash == null) {
                // Nothing was ever confirmed written for this row. The pass that finds never-indexed entities owns
                // that case, and it works from the database rather than from here.
                continue;
            }
            if (!ledgerHash.equals(row.contentHash()) && enqueueService.enqueueUpsert(entityType, row.entityId(), WeaviateOutboxOrigin.RECONCILE_ORPHAN)) {
                repaired++;
            }
        }
        return repaired;
    }

    /**
     * Deliberately does not touch {@link SearchableEntityReconcileState#getOrphanAbortStreaks()}: a type's
     * standing does not depend on where the cursor happens to be, and wiping it on every lap would let a type
     * that keeps tripping the ratio demand a fresh two-tick confirmation every single cycle, forever.
     */
    private void finishCycle(String runId, SearchableEntityReconcileState state) {
        ZonedDateTime cycleStartedAt = state.getCycleStartedAt();
        String elapsed = cycleStartedAt == null ? "unknown" : Duration.between(cycleStartedAt, ZonedDateTime.now()).toString();
        log.info("[index {}] completed a cycle: {} rows checked, {} rewritten, {} removed, elapsed {}", runId, state.getEntitiesChecked(), state.getRepairsEnqueued(),
                state.getRowsRemoved(), elapsed);
        state.startNewCycle();
        reconcileStateRepository.save(state);
    }

    private Map<String, Integer> deserializeStreaks(String json) {
        if (json == null) {
            return new HashMap<>();
        }
        try {
            return new HashMap<>(objectMapper.readValue(json, new TypeReference<Map<String, Integer>>() {
            }));
        }
        catch (JacksonException e) {
            throw new WeaviateException("Failed to deserialize orphan abort streaks: " + e.getMessage(), e);
        }
    }

    private String serializeStreaks(Map<String, Integer> streaks) {
        if (streaks.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(streaks);
        }
        catch (JacksonException e) {
            throw new WeaviateException("Failed to serialize orphan abort streaks: " + e.getMessage(), e);
        }
    }

    private record Outcome(long repaired, long removed) {
    }
}
