package de.tum.cit.aet.artemis.globalsearch.service.reconcile;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.globalsearch.config.WeaviateEnabled;
import de.tum.cit.aet.artemis.globalsearch.config.WeaviateReconcileProperties;
import de.tum.cit.aet.artemis.globalsearch.domain.SearchableEntitySyncState;
import de.tum.cit.aet.artemis.globalsearch.domain.WeaviateOutboxOrigin;
import de.tum.cit.aet.artemis.globalsearch.repository.SearchableEntitySyncStateRepository;
import de.tum.cit.aet.artemis.globalsearch.service.SearchableEntityContentHasher;
import de.tum.cit.aet.artemis.globalsearch.service.SearchableEntityResolver;

/**
 * Re-derives entities that were confirmed written and queues a repair where the index no longer matches them.
 * <p>
 * Catches a change that never reached the outbox, and a shape change that leaves every stored row wrong at once,
 * such as a searchable field being added. It also catches the reverse: a ledger row still claiming an entity is
 * indexed after it was deleted or stopped being indexable.
 * <p>
 * The pass keeps no position of its own. It takes the rows that have gone longest without a check, and checking
 * them is what moves them to the back of the queue, so every entity comes round again on a fixed cycle.
 * <p>
 * This is the expensive pass: every check loads an entity with its associations. Nothing bounds that cost except
 * the configured slice size, since a healthy system queues nothing and never trips the outbox depth limit.
 */
@Lazy
@Service
@Conditional(WeaviateEnabled.class)
public class SearchableEntityDriftSweep {

    private static final Logger log = LoggerFactory.getLogger(SearchableEntityDriftSweep.class);

    private final SearchableEntitySyncStateRepository syncStateRepository;

    private final SearchableEntityResolver resolver;

    private final SearchableEntityContentHasher contentHasher;

    private final SearchableEntityIdEnumerator idEnumerator;

    private final ReconcileEnqueueService enqueueService;

    private final WeaviateReconcileProperties reconcileProperties;

    public SearchableEntityDriftSweep(SearchableEntitySyncStateRepository syncStateRepository, SearchableEntityResolver resolver, SearchableEntityContentHasher contentHasher,
            SearchableEntityIdEnumerator idEnumerator, ReconcileEnqueueService enqueueService, WeaviateReconcileProperties reconcileProperties) {
        this.syncStateRepository = syncStateRepository;
        this.resolver = resolver;
        this.contentHasher = contentHasher;
        this.idEnumerator = idEnumerator;
        this.enqueueService = enqueueService;
        this.reconcileProperties = reconcileProperties;
    }

    /**
     * Checks one slice of the least recently verified entities and queues a repair for any that no longer match.
     */
    public void sweep() {
        List<String> types = reconcileProperties.entityTypes();
        if (types.isEmpty() || !enqueueService.canEnqueue()) {
            return;
        }

        List<SearchableEntitySyncState> candidates = syncStateRepository.findLeastRecentlyVerified(types, PageRequest.ofSize(reconcileProperties.driftBatchSize()));
        if (candidates.isEmpty()) {
            return;
        }

        ZonedDateTime checkedAt = ZonedDateTime.now();
        long drifted = 0;
        long removed = 0;
        for (SearchableEntitySyncState state : candidates) {
            if (!idEnumerator.isTypeAvailable(state.getEntityType())) {
                // Resolving anything from a disabled module yields nothing, which here is indistinguishable from the
                // entity having been deleted. Acting on it would queue a delete for every indexed row of the type.
                log.debug("Skipping {} in the drift sweep: its module is disabled", state.getEntityType());
                continue;
            }
            switch (check(state, checkedAt)) {
                case DRIFTED -> drifted++;
                case GONE -> removed++;
                case MATCHED -> {
                    // Nothing to do: the row already reflects the entity.
                }
            }
        }

        if (drifted > 0 || removed > 0) {
            log.info("Drift sweep checked {} entities: {} no longer matched the database, {} were gone or no longer indexable", candidates.size(), drifted, removed);
        }
    }

    /**
     * Compares one entity against what was last written for it, and records that it has now been looked at.
     * <p>
     * A row is marked verified whatever the outcome, including when it drifted. The repair is queued and the
     * dispatcher will refresh the ledger when it lands, so leaving the row at the front of the queue would only
     * make the pass re-examine an entity it has already dealt with.
     */
    private CheckResult check(SearchableEntitySyncState state, ZonedDateTime checkedAt) {
        Optional<Map<String, Object>> desired = resolver.resolve(state.getEntityType(), state.getEntityId());
        if (desired.isEmpty()) {
            enqueueService.enqueueDelete(state.getEntityType(), state.getEntityId(), WeaviateOutboxOrigin.RECONCILE_DRIFT);
            return CheckResult.GONE;
        }

        state.setVerifiedAt(checkedAt);
        syncStateRepository.save(state);

        if (contentHasher.hash(desired.get()).equals(state.getContentHash())) {
            return CheckResult.MATCHED;
        }
        enqueueService.enqueueUpsert(state.getEntityType(), state.getEntityId(), WeaviateOutboxOrigin.RECONCILE_DRIFT);
        return CheckResult.DRIFTED;
    }

    private enum CheckResult {
        MATCHED, DRIFTED, GONE
    }
}
