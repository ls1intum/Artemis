package de.tum.cit.aet.artemis.globalsearch.service.reconcile;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.HashMap;
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
import de.tum.cit.aet.artemis.globalsearch.repository.SearchableEntityReconcileStateRepository;
import de.tum.cit.aet.artemis.globalsearch.repository.SearchableEntitySyncStateRepository;
import de.tum.cit.aet.artemis.globalsearch.service.SearchableEntityIndexScanService;
import de.tum.cit.aet.artemis.globalsearch.service.SearchableEntityIndexScanService.IndexedRow;

/**
 * Walks the index itself and repairs what it finds there, which is the only check that reads what is actually
 * stored rather than what the database and the ledger agree about.
 * <p>
 * It answers two questions the other passes cannot. Does this row still have an entity behind it, or is it an
 * orphan left by a lost delete? And does its stored content match what was last written for it, or has the index
 * diverged through a restore, a write that reported success without persisting, or an edit made outside Artemis?
 * <p>
 * This is the only pass that removes anything, so its guards matter more than its logic. It refuses to act on a
 * type it does not manage, on a type whose module is disabled, and on a result so surprising that a bug is the
 * likelier explanation. It also cannot remove more than a set number of rows in one tick, which bounds the damage
 * of being wrong about all of the above.
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

    public SearchableEntityOrphanSweep(SearchableEntityIndexScanService indexScanService, SearchableEntityIdEnumerator idEnumerator,
            SearchableEntitySyncStateRepository syncStateRepository, SearchableEntityReconcileStateRepository reconcileStateRepository, ReconcileEnqueueService enqueueService,
            WeaviateReconcileProperties reconcileProperties) {
        this.indexScanService = indexScanService;
        this.idEnumerator = idEnumerator;
        this.syncStateRepository = syncStateRepository;
        this.reconcileStateRepository = reconcileStateRepository;
        this.enqueueService = enqueueService;
        this.reconcileProperties = reconcileProperties;
    }

    /**
     * Reads one bounded slice of the index, repairs what is wrong with it, and saves where it got to.
     */
    public void sweep() {
        if (reconcileProperties.entityTypes().isEmpty() || !enqueueService.canEnqueue()) {
            return;
        }

        SearchableEntityReconcileState state = reconcileStateRepository.findByPass(ReconcilePass.ORPHAN).orElseGet(() -> new SearchableEntityReconcileState(ReconcilePass.ORPHAN));

        var slice = indexScanService.scanFrom(state.getPositionCursor(), reconcileProperties.orphanPageSize(), reconcileProperties.orphanPagesPerTick());
        if (slice.rows().isEmpty()) {
            finishCycle(state);
            return;
        }

        Outcome outcome = repair(slice.rows());
        if (outcome == null) {
            // The pass aborted. Leave the cursor where it was so the next tick re-reads the same slice rather than
            // stepping over whatever caused it.
            return;
        }

        state.recordProgress(slice.rows().size(), outcome.repaired(), outcome.removed());
        if (slice.nextCursor() == null) {
            finishCycle(state);
            return;
        }
        state.setPositionCursor(slice.nextCursor());
        reconcileStateRepository.save(state);

        if (outcome.repaired() > 0 || outcome.removed() > 0) {
            log.info("Index sweep checked {} rows: {} disagreed with what was last written, {} had no entity behind them", slice.rows().size(), outcome.repaired(),
                    outcome.removed());
        }
    }

    /**
     * Decides what to do with each scanned row, grouping by type so each type costs one lookup rather than one per
     * row.
     *
     * @return what was queued, or {@code null} if the pass aborted and nothing should be recorded
     */
    private Outcome repair(List<IndexedRow> rows) {
        Map<String, List<IndexedRow>> rowsByType = rows.stream().filter(row -> row.entityType() != null && row.entityId() != null)
                .collect(Collectors.groupingBy(IndexedRow::entityType));

        long repaired = 0;
        long removed = 0;
        for (var entry : rowsByType.entrySet()) {
            String entityType = entry.getKey();
            if (!reconcileProperties.managesEntityType(entityType)) {
                // Removing rows of a type no pass repairs would delete an orphaned post while nothing ever re-adds
                // a missing one, which is worse than leaving the type alone entirely.
                log.debug("Skipping {} rows in the index sweep: the type is not managed", entityType);
                continue;
            }

            List<IndexedRow> candidates = entry.getValue();
            Optional<Set<Long>> shouldBeIndexed = idEnumerator.indexableIdsAmong(entityType, candidates.stream().map(IndexedRow::entityId).toList());
            if (shouldBeIndexed.isEmpty()) {
                // Nothing is known about this type, so every row of it would look orphaned.
                log.debug("Skipping {} rows in the index sweep: its module is disabled", entityType);
                continue;
            }

            List<IndexedRow> orphans = candidates.stream().filter(row -> !shouldBeIndexed.get().contains(row.entityId())).toList();
            if (abortsOnOrphanRatio(entityType, orphans.size(), candidates.size())) {
                return null;
            }

            removed += remove(entityType, orphans, removed);
            repaired += repairDivergent(entityType, candidates, orphans);
        }
        return new Outcome(repaired, removed);
    }

    /**
     * Refuses to act when the result is too surprising to trust. Most rows looking orphaned means the lookup, the
     * scan, or this pass is wrong far more often than it means the index really is that far out of step, and
     * deleting on that reading is unrecoverable.
     */
    private boolean abortsOnOrphanRatio(String entityType, int orphanCount, int candidateCount) {
        if (candidateCount == 0 || orphanCount == 0) {
            return false;
        }
        double ratio = (double) orphanCount / candidateCount;
        if (ratio <= reconcileProperties.orphanAbortRatio()) {
            return false;
        }
        log.error("Index sweep aborted: {} of {} scanned {} rows had no entity behind them, above the {} threshold. Nothing was removed.", orphanCount, candidateCount, entityType,
                reconcileProperties.orphanAbortRatio());
        return true;
    }

    private long remove(String entityType, List<IndexedRow> orphans, long alreadyRemoved) {
        long removed = 0;
        for (IndexedRow orphan : orphans) {
            if (alreadyRemoved + removed >= reconcileProperties.orphanDeleteCapPerTick()) {
                log.warn("Index sweep stopped at its per-tick removal limit of {}; the rest is left for the next tick", reconcileProperties.orphanDeleteCapPerTick());
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
    private long repairDivergent(String entityType, List<IndexedRow> candidates, List<IndexedRow> orphans) {
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

    private void finishCycle(SearchableEntityReconcileState state) {
        ZonedDateTime cycleStartedAt = state.getCycleStartedAt();
        String elapsed = cycleStartedAt == null ? "unknown" : Duration.between(cycleStartedAt, ZonedDateTime.now()).toString();
        log.info("Index sweep completed a cycle: {} rows checked, {} rewritten, {} removed, elapsed {}", state.getEntitiesChecked(), state.getRepairsEnqueued(),
                state.getRowsRemoved(), elapsed);
        state.startNewCycle();
        reconcileStateRepository.save(state);
    }

    private record Outcome(long repaired, long removed) {
    }
}
