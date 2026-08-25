package de.tum.cit.aet.artemis.globalsearch.service.reconcile;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.globalsearch.config.WeaviateEnabled;
import de.tum.cit.aet.artemis.globalsearch.config.WeaviateReconcileProperties;
import de.tum.cit.aet.artemis.globalsearch.domain.ReconcilePass;
import de.tum.cit.aet.artemis.globalsearch.domain.SearchableEntityReconcileState;
import de.tum.cit.aet.artemis.globalsearch.domain.WeaviateOutboxOrigin;
import de.tum.cit.aet.artemis.globalsearch.repository.SearchableEntityReconcileStateRepository;
import de.tum.cit.aet.artemis.globalsearch.repository.SearchableEntitySyncStateRepository;

/**
 * Queues a write for every entity the index has never confirmed holding.
 * <p>
 * An entity with no ledger row was never successfully written: it predates the outbox, or its change was lost
 * before reaching it. Finding those is a comparison of identity alone, so this pass never loads an entity.
 * <p>
 * The first run against an empty ledger reports the entire corpus as missing, which is correct rather than a
 * problem to design around: nothing written before the ledger existed can be assumed good. It is queued a page at
 * a time, throttled by the outbox depth limit, and drains over days without anyone noticing.
 */
@Lazy
@Service
@Conditional(WeaviateEnabled.class)
public class SearchableEntityMissingSweep {

    private static final Logger log = LoggerFactory.getLogger(SearchableEntityMissingSweep.class);

    private final SearchableEntityIdEnumerator idEnumerator;

    private final SearchableEntitySyncStateRepository syncStateRepository;

    private final SearchableEntityReconcileStateRepository reconcileStateRepository;

    private final ReconcileEnqueueService enqueueService;

    private final WeaviateReconcileProperties reconcileProperties;

    public SearchableEntityMissingSweep(SearchableEntityIdEnumerator idEnumerator, SearchableEntitySyncStateRepository syncStateRepository,
            SearchableEntityReconcileStateRepository reconcileStateRepository, ReconcileEnqueueService enqueueService, WeaviateReconcileProperties reconcileProperties) {
        this.idEnumerator = idEnumerator;
        this.syncStateRepository = syncStateRepository;
        this.reconcileStateRepository = reconcileStateRepository;
        this.enqueueService = enqueueService;
        this.reconcileProperties = reconcileProperties;
    }

    /**
     * Examines one page of ids and queues whatever the ledger has no record of, then saves where it got to.
     * <p>
     * One page per tick is the whole budget. That keeps the cost of this pass a constant the operator chooses,
     * rather than something that grows with the size of the corpus.
     */
    public void sweep() {
        List<String> types = reconcileProperties.entityTypes();
        if (types.isEmpty()) {
            return;
        }
        if (!enqueueService.canEnqueue()) {
            return;
        }

        SearchableEntityReconcileState state = reconcileStateRepository.findByPass(ReconcilePass.MISSING)
                .orElseGet(() -> new SearchableEntityReconcileState(ReconcilePass.MISSING));
        String currentType = resolveCurrentType(state, types);
        // Read after resolving, since falling back to another type also discards the watermark.
        long afterId = state.getPositionEntityId() == null ? 0L : state.getPositionEntityId();

        Optional<List<Long>> page = idEnumerator.nextIndexableIds(currentType, afterId, reconcileProperties.missingBatchSize());
        if (page.isEmpty()) {
            // The module owning this type is disabled, so nothing is known about it. Skipping is the only safe
            // reading: concluding "nothing exists" here would queue a repair for every indexed row of this type.
            log.debug("Skipping {} in the missing sweep: its module is disabled", currentType);
            advanceToNextType(state, currentType, types);
            reconcileStateRepository.save(state);
            return;
        }

        List<Long> candidateIds = page.get();
        if (candidateIds.isEmpty()) {
            advanceToNextType(state, currentType, types);
            reconcileStateRepository.save(state);
            return;
        }

        Set<Long> alreadySynced = syncStateRepository.findSyncedEntityIds(currentType, candidateIds);
        long enqueued = 0;
        for (Long entityId : candidateIds) {
            if (!alreadySynced.contains(entityId) && enqueueService.enqueueUpsert(currentType, entityId, WeaviateOutboxOrigin.RECONCILE_MISSING)) {
                enqueued++;
            }
        }

        state.setPositionEntityType(currentType);
        state.setPositionEntityId(candidateIds.getLast());
        state.recordProgress(candidateIds.size(), enqueued, 0);
        reconcileStateRepository.save(state);

        if (enqueued > 0) {
            log.info("Missing sweep queued {} of {} {} entities that the index had no record of", enqueued, candidateIds.size(), currentType);
        }
    }

    /**
     * Picks up where the pass left off, falling back to the first configured type when there is nothing to resume
     * or when the stored type is no longer configured.
     * <p>
     * Falling back also discards the watermark, because it counts ids within one type: carrying it across would
     * start the next type partway through and silently skip everything below it for a whole cycle.
     */
    private String resolveCurrentType(SearchableEntityReconcileState state, List<String> types) {
        String storedType = state.getPositionEntityType();
        if (storedType == null || !types.contains(storedType)) {
            String firstType = types.getFirst();
            state.setPositionEntityType(firstType);
            state.setPositionEntityId(null);
            return firstType;
        }
        return storedType;
    }

    /**
     * Moves to the next configured type, or wraps around and starts a fresh cycle after the last one.
     */
    private void advanceToNextType(SearchableEntityReconcileState state, String currentType, List<String> types) {
        int nextIndex = types.indexOf(currentType) + 1;
        if (nextIndex < types.size()) {
            state.setPositionEntityType(types.get(nextIndex));
            state.setPositionEntityId(null);
            return;
        }
        logCycleSummary(state);
        state.startNewCycle();
        state.setPositionEntityType(types.getFirst());
    }

    private void logCycleSummary(SearchableEntityReconcileState state) {
        ZonedDateTime cycleStartedAt = state.getCycleStartedAt();
        String elapsed = cycleStartedAt == null ? "unknown" : Duration.between(cycleStartedAt, ZonedDateTime.now()).toString();
        log.info("Missing sweep completed a cycle: {} entities checked, {} queued, elapsed {}", state.getEntitiesChecked(), state.getRepairsEnqueued(), elapsed);
    }
}
