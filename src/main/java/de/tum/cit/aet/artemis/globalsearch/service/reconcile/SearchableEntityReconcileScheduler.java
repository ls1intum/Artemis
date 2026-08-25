package de.tum.cit.aet.artemis.globalsearch.service.reconcile;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_SCHEDULING;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import de.tum.cit.aet.artemis.globalsearch.config.WeaviateEnabled;
import de.tum.cit.aet.artemis.globalsearch.config.WeaviateReconcileProperties;
import de.tum.cit.aet.artemis.globalsearch.domain.ReconcilePass;

/**
 * Ticks the reconcile passes that keep the {@code SearchableEntities} index in step with the database. Runs on
 * the scheduling node only, matching the dispatcher that drains what these passes queue.
 * <p>
 * Each pass is scheduled, enabled and isolated separately: they cost different things, the one that deletes
 * should be switched on last, and one failing is no reason for the others to stop. A tick that finds nothing
 * logs nothing, since these run forever and a noisy quiet tick would bury the ticks that matter.
 */
@Lazy
@Service
@Conditional(WeaviateEnabled.class)
@Profile(PROFILE_SCHEDULING)
public class SearchableEntityReconcileScheduler {

    private static final Logger log = LoggerFactory.getLogger(SearchableEntityReconcileScheduler.class);

    private final WeaviateReconcileProperties reconcileProperties;

    private final SearchableEntityMissingSweep missingSweep;

    private final SearchableEntityDriftSweep driftSweep;

    public SearchableEntityReconcileScheduler(WeaviateReconcileProperties reconcileProperties, SearchableEntityMissingSweep missingSweep, SearchableEntityDriftSweep driftSweep) {
        this.reconcileProperties = reconcileProperties;
        this.missingSweep = missingSweep;
        this.driftSweep = driftSweep;
    }

    /**
     * Records the effective configuration once, so what a node was doing is answerable from its log alone. Silent
     * while every pass is disabled, which is the default.
     */
    @PostConstruct
    public void logEffectiveConfiguration() {
        if (!reconcileProperties.anyPassEnabled()) {
            log.debug("Global search reconcile is disabled");
            return;
        }
        log.info(
                "Global search reconcile enabled (missing={}, drift={}, orphan={}); types={}, maxOutboxDepth={}, budgets: missing={}, drift={}, orphan={}x{} rows, deleteCap={}, abortRatio={}",
                reconcileProperties.missingSweepEnabled(), reconcileProperties.driftSweepEnabled(), reconcileProperties.orphanSweepEnabled(), reconcileProperties.entityTypes(),
                reconcileProperties.maxOutboxDepth(), reconcileProperties.missingBatchSize(), reconcileProperties.driftBatchSize(), reconcileProperties.orphanPagesPerTick(),
                reconcileProperties.orphanPageSize(), reconcileProperties.orphanDeleteCapPerTick(), reconcileProperties.orphanAbortRatio());
    }

    /**
     * Re-derives the least recently verified entities and queues a repair where the index no longer matches.
     */
    @Scheduled(cron = "${artemis.scheduling.weaviate-reconcile-drift-time:0 */5 * * * *}")
    public void reconcileDrift() {
        runPass(ReconcilePass.DRIFT, reconcileProperties.driftSweepEnabled(), driftSweep::sweep);
    }

    /**
     * Queues a write for entities the index has never confirmed holding.
     */
    @Scheduled(cron = "${artemis.scheduling.weaviate-reconcile-missing-time:0 */10 * * * *}")
    public void reconcileMissing() {
        runPass(ReconcilePass.MISSING, reconcileProperties.missingSweepEnabled(), missingSweep::sweep);
    }

    /**
     * Scans the index for rows whose entity is gone, and for rows whose stored content disagrees with what was last
     * written. Runs overnight by default: the heaviest pass, and the only one that deletes.
     */
    @Scheduled(cron = "${artemis.scheduling.weaviate-reconcile-orphan-time:0 0 3 * * *}")
    public void reconcileOrphans() {
        runPass(ReconcilePass.ORPHAN, reconcileProperties.orphanSweepEnabled(), () -> {
            // The pass itself lands in a later change; scheduling, gating and isolation are in place now.
        });
    }

    /**
     * Runs one pass, swallowing whatever it throws. {@code @Scheduled} suppresses further executions of a method
     * that throws, which would silently retire the pass until the next restart, so a transient failure has to be
     * caught here to stay transient.
     *
     * @param pass    the pass being run, for the log
     * @param enabled whether it is switched on
     * @param body    the work itself
     */
    void runPass(ReconcilePass pass, boolean enabled, Runnable body) {
        if (!enabled) {
            return;
        }
        try {
            // Give the pass an authorization context, matching the dispatcher it queues work for.
            SecurityUtils.setAuthorizationObject();
            body.run();
        }
        catch (Exception e) {
            log.error("Global search reconcile pass {} failed and will be retried on its next tick: {}", pass, e.getMessage(), e);
        }
    }
}
