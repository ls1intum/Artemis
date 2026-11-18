package de.tum.cit.aet.artemis.globalsearch.service.reconcile;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.globalsearch.config.WeaviateEnabled;
import de.tum.cit.aet.artemis.globalsearch.config.WeaviateReconcileProperties;
import de.tum.cit.aet.artemis.globalsearch.config.schema.entityschemas.SearchableEntitySchema;
import de.tum.cit.aet.artemis.globalsearch.domain.ReconcilePass;
import de.tum.cit.aet.artemis.globalsearch.domain.SearchableEntityReconcileState;
import de.tum.cit.aet.artemis.globalsearch.domain.WeaviateOutboxOrigin;
import de.tum.cit.aet.artemis.globalsearch.repository.SearchableEntityReconcileStateRepository;

/**
 * After a rebuild of the index, queues every post and then every answer post once. The other passes do not manage
 * these types, so without this pass they would only come back when they are edited.
 * <p>
 * The pass is active only while its state row has a position, which the rebuild creates. It walks the ids page by page,
 * within the remaining outbox capacity and a time budget per tick, and persists its position after each page. A page
 * whose enqueue fails is retried from the same position on the next tick, so no id is skipped. When both types are
 * exhausted the position is cleared and the pass is idle until the next rebuild.
 */
@Lazy
@Service
@Conditional(WeaviateEnabled.class)
public class SearchableEntityRefillSweep {

    private static final Logger log = LoggerFactory.getLogger(SearchableEntityRefillSweep.class);

    private static final List<String> TYPES = List.of(SearchableEntitySchema.TypeValues.POST, SearchableEntitySchema.TypeValues.ANSWER_POST);

    private final SearchableEntityIdEnumerator idEnumerator;

    private final SearchableEntityReconcileStateRepository reconcileStateRepository;

    private final ReconcileEnqueueService enqueueService;

    private final WeaviateReconcileProperties reconcileProperties;

    public SearchableEntityRefillSweep(SearchableEntityIdEnumerator idEnumerator, SearchableEntityReconcileStateRepository reconcileStateRepository,
            ReconcileEnqueueService enqueueService, WeaviateReconcileProperties reconcileProperties) {
        this.idEnumerator = idEnumerator;
        this.reconcileStateRepository = reconcileStateRepository;
        this.enqueueService = enqueueService;
        this.reconcileProperties = reconcileProperties;
    }

    /**
     * Queues the next pages of posts and answer posts, if a refill is in progress.
     */
    public void sweep() {
        Optional<SearchableEntityReconcileState> active = reconcileStateRepository.findByPass(ReconcilePass.REFILL)
                .filter(state -> state.getPositionEntityType() != null && TYPES.contains(state.getPositionEntityType()));
        if (active.isEmpty()) {
            return;
        }
        SearchableEntityReconcileState state = active.get();
        long deadline = System.nanoTime() + SearchableEntityMissingSweep.TICK_TIME_BUDGET.toNanos();
        while (System.nanoTime() < deadline && state.getPositionEntityType() != null) {
            int capacity = enqueueService.remainingCapacity();
            if (capacity <= 0) {
                return;
            }
            refillPage(state, Math.min(capacity, reconcileProperties.missingBatchSize()));
        }
    }

    private void refillPage(SearchableEntityReconcileState state, int pageSize) {
        String type = state.getPositionEntityType();
        long afterId = state.getPositionEntityId() == null ? 0L : state.getPositionEntityId();
        List<Long> ids = idEnumerator.nextIndexableIds(type, afterId, pageSize).orElse(List.of());
        if (ids.isEmpty()) {
            int next = TYPES.indexOf(type) + 1;
            state.setPositionEntityType(next < TYPES.size() ? TYPES.get(next) : null);
            state.setPositionEntityId(next < TYPES.size() ? 0L : null);
            reconcileStateRepository.save(state);
            if (next >= TYPES.size()) {
                log.info("Global search refill of posts and answer posts is complete ({} queued)", state.getRepairsEnqueued());
            }
            return;
        }
        long enqueued = 0;
        for (long id : ids) {
            // An id that is already pending counts as queued; a failing enqueue throws and leaves the position unchanged.
            if (enqueueService.enqueueUpsert(type, id, WeaviateOutboxOrigin.RECONCILE_MISSING)) {
                enqueued++;
            }
        }
        state.setPositionEntityId(ids.getLast());
        state.recordProgress(ids.size(), enqueued, 0);
        reconcileStateRepository.save(state);
        log.debug("Global search refill queued {} of {} {} ids after {}", enqueued, ids.size(), type, afterId);
    }
}
