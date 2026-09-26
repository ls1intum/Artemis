package de.tum.cit.aet.artemis.globalsearch.service.reconcile;

import java.time.Duration;
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
import de.tum.cit.aet.artemis.globalsearch.domain.ReconcilePass;
import de.tum.cit.aet.artemis.globalsearch.domain.SearchableEntityReconcileState;
import de.tum.cit.aet.artemis.globalsearch.domain.SearchableEntitySyncState;
import de.tum.cit.aet.artemis.globalsearch.domain.WeaviateOutboxOrigin;
import de.tum.cit.aet.artemis.globalsearch.repository.SearchableEntityReconcileStateRepository;
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

    private final SearchableEntityReconcileStateRepository reconcileStateRepository;

    private final SearchableEntityResolver resolver;

    private final SearchableEntityContentHasher contentHasher;

    private final SearchableEntityIdEnumerator idEnumerator;

    private final ReconcileEnqueueService enqueueService;

    private final WeaviateReconcileProperties reconcileProperties;

    public SearchableEntityDriftSweep(SearchableEntitySyncStateRepository syncStateRepository, SearchableEntityReconcileStateRepository reconcileStateRepository,
            SearchableEntityResolver resolver, SearchableEntityContentHasher contentHasher, SearchableEntityIdEnumerator idEnumerator, ReconcileEnqueueService enqueueService,
            WeaviateReconcileProperties reconcileProperties) {
        this.syncStateRepository = syncStateRepository;
        this.reconcileStateRepository = reconcileStateRepository;
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
        String runId = ReconcileRunId.next();
        // Only types whose module is available are read. A skipped row keeps its old verifiedAt, so rows of a disabled
        // module's type would otherwise lead every slice and stall the pass for every other type.
        List<String> types = reconcileProperties.entityTypes().stream().filter(idEnumerator::isTypeAvailable).toList();
        if (types.isEmpty() || !enqueueService.canEnqueue()) {
            return;
        }

        List<SearchableEntitySyncState> candidates = syncStateRepository.findLeastRecentlyVerified(types, PageRequest.ofSize(reconcileProperties.driftBatchSize()));
        if (candidates.isEmpty()) {
            return;
        }

        SearchableEntityReconcileState pass = reconcileStateRepository.findByPass(ReconcilePass.DRIFT).orElseGet(() -> new SearchableEntityReconcileState(ReconcilePass.DRIFT));
        if (hasComeFullCircle(pass, candidates.getFirst())) {
            logCycleSummary(runId, pass);
            pass.startNewCycle();
        }

        ZonedDateTime checkedAt = ZonedDateTime.now();
        long drifted = 0;
        long removed = 0;
        for (SearchableEntitySyncState state : candidates) {
            if (!idEnumerator.isTypeAvailable(state.getEntityType())) {
                // Safety net behind the filtered query: a module going away mid-pass must never turn into deletes.
                log.debug("[drift {}] skipping {} {}: its module is disabled", runId, state.getEntityType(), state.getEntityId());
                continue;
            }
            switch (check(state, checkedAt)) {
                case DRIFTED -> {
                    drifted++;
                    log.debug("[drift {}] {} {} no longer matches what was last written", runId, state.getEntityType(), state.getEntityId());
                }
                case GONE -> {
                    removed++;
                    log.debug("[drift {}] {} {} is gone or no longer indexable", runId, state.getEntityType(), state.getEntityId());
                }
                case MATCHED, AWAITING_REPAIR -> {
                    // Nothing to report: either the row is correct, or its repair is already queued.
                }
            }
        }

        pass.recordProgress(candidates.size(), drifted + removed, 0);
        reconcileStateRepository.save(pass);

        if (drifted > 0 || removed > 0) {
            log.info("[drift {}] checked {} entities: {} no longer matched the database, {} were gone or no longer indexable", runId, candidates.size(), drifted, removed);
        }
    }

    /**
     * Whether every managed entity has been checked since the current cycle began.
     * <p>
     * The pass has no position to compare against, so the corpus itself answers the question: the slice always
     * starts with the entity checked longest ago, and once even that one was checked after the cycle started,
     * nothing is left over from before it.
     */
    private boolean hasComeFullCircle(SearchableEntityReconcileState pass, SearchableEntitySyncState leastRecentlyVerified) {
        ZonedDateTime cycleStartedAt = pass.getCycleStartedAt();
        ZonedDateTime oldestCheck = leastRecentlyVerified.getVerifiedAt();
        return cycleStartedAt != null && oldestCheck != null && oldestCheck.isAfter(cycleStartedAt);
    }

    private void logCycleSummary(String runId, SearchableEntityReconcileState pass) {
        ZonedDateTime cycleStartedAt = pass.getCycleStartedAt();
        String elapsed = cycleStartedAt == null ? "unknown" : Duration.between(cycleStartedAt, ZonedDateTime.now()).toString();
        log.info("[drift {}] completed a cycle: {} entities checked, {} queued for repair, elapsed {}", runId, pass.getEntitiesChecked(), pass.getRepairsEnqueued(), elapsed);
    }

    /**
     * Compares one entity against what was last written for it, and records that it has now been looked at —
     * but only once the outcome is actually settled.
     * <p>
     * The row is marked checked whatever the outcome, including when it turned out to be gone and when a repair
     * for it is already waiting. Leaving it unmarked keeps it at the front of the queue, so every tick re-derives
     * the same entity until the dispatcher catches up, spending the slice on work already in flight. That is also
     * why the mark comes last: {@code enqueueDelete}/{@code enqueueUpsert} do real repository work and can throw,
     * and a row marked verified before that call would be pushed to the back of the "least recently verified"
     * queue on a failed repair, not just one still pending — leaving it unrepaired for up to a full cycle instead
     * of retried on the very next tick.
     */
    private CheckResult check(SearchableEntitySyncState state, ZonedDateTime checkedAt) {
        Optional<Map<String, Object>> desired = resolver.resolve(state.getEntityType(), state.getEntityId());

        if (desired.isEmpty()) {
            boolean enqueued = enqueueService.enqueueDelete(state.getEntityType(), state.getEntityId(), WeaviateOutboxOrigin.RECONCILE_DRIFT);
            markVerified(state, checkedAt);
            return enqueued ? CheckResult.GONE : CheckResult.AWAITING_REPAIR;
        }
        if (contentHasher.hash(desired.get()).equals(state.getContentHash())) {
            markVerified(state, checkedAt);
            return CheckResult.MATCHED;
        }
        boolean enqueued = enqueueService.enqueueUpsert(state.getEntityType(), state.getEntityId(), WeaviateOutboxOrigin.RECONCILE_DRIFT);
        markVerified(state, checkedAt);
        return enqueued ? CheckResult.DRIFTED : CheckResult.AWAITING_REPAIR;
    }

    /**
     * A scoped update, not a save of the whole (possibly now stale) entity: see {@code markVerified}'s own javadoc
     * on the repository.
     */
    private void markVerified(SearchableEntitySyncState state, ZonedDateTime checkedAt) {
        syncStateRepository.markVerified(state.getEntityType(), state.getEntityId(), checkedAt);
    }

    private enum CheckResult {
        /** The row still reflects the entity. */
        MATCHED,
        /** The entity changed and a repair was queued. */
        DRIFTED,
        /** The entity is gone or no longer indexable, and a removal was queued. */
        GONE,
        /**
         * A divergence, but a repair for this entity was already waiting. Counting it again would report the same
         * problem once per tick until the dispatcher works through the queue.
         */
        AWAITING_REPAIR
    }
}
