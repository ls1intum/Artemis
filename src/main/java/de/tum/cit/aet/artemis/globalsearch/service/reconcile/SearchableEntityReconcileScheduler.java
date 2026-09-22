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
import de.tum.cit.aet.artemis.core.service.feature.Feature;
import de.tum.cit.aet.artemis.core.service.feature.FeatureToggleService;
import de.tum.cit.aet.artemis.globalsearch.config.WeaviateEnabled;
import de.tum.cit.aet.artemis.globalsearch.config.WeaviateReconcileProperties;
import de.tum.cit.aet.artemis.globalsearch.domain.ReconcilePass;

/**
 * Ticks the reconcile passes that keep the {@code SearchableEntities} index in step with the database. Runs on
 * the scheduling node only, matching the dispatcher that drains what these passes queue.
 * <p>
 * Each pass is scheduled and isolated separately: they cost different things, and one failing is no reason for the
 * others to stop. A tick that finds nothing logs nothing, since these run forever and a noisy quiet tick would bury
 * the ticks that matter.
 * <p>
 * Whether a pass actually runs is a runtime {@link FeatureToggleService} check, not the static
 * {@link WeaviateReconcileProperties} flags — those only seed the toggle's first-ever value (see
 * {@code FeatureToggleService.initFeatures}), so an admin can switch reconcile on or off live, with no restart.
 * Missing and drift share {@link Feature#GlobalSearchReconcile}. Missing only adds entities the index has never
 * confirmed holding; drift also removes a row once it directly confirms, by re-deriving that one entity from the
 * database, that it is gone or no longer indexable — a targeted, database-confirmed deletion, not a heuristic
 * one. Orphan is gated separately by {@link Feature#GlobalSearchReconcileOrphan} and should be switched on last:
 * its deletions are inferred from a bulk scan of the index alone, with no per-row database confirmation, which is
 * why it carries the extra abort-ratio and cross-check safeguards the other two passes don't need.
 */
@Lazy
@Service
@Conditional(WeaviateEnabled.class)
@Profile(PROFILE_SCHEDULING)
public class SearchableEntityReconcileScheduler {

    private static final Logger log = LoggerFactory.getLogger(SearchableEntityReconcileScheduler.class);

    private final WeaviateReconcileProperties reconcileProperties;

    private final FeatureToggleService featureToggleService;

    private final SearchableEntityMissingSweep missingSweep;

    private final SearchableEntityDriftSweep driftSweep;

    private final SearchableEntityOrphanSweep orphanSweep;

    public SearchableEntityReconcileScheduler(WeaviateReconcileProperties reconcileProperties, FeatureToggleService featureToggleService, SearchableEntityMissingSweep missingSweep,
            SearchableEntityDriftSweep driftSweep, SearchableEntityOrphanSweep orphanSweep) {
        this.reconcileProperties = reconcileProperties;
        this.featureToggleService = featureToggleService;
        this.missingSweep = missingSweep;
        this.driftSweep = driftSweep;
        this.orphanSweep = orphanSweep;
    }

    /**
     * Records the effective tuning once, so what a node is configured to do is answerable from its log alone. The
     * on/off state is a live toggle, not logged here since it can change at any time after this line runs; check
     * the admin feature toggle page for the current state.
     */
    @PostConstruct
    public void logEffectiveConfiguration() {
        log.info(
                "Global search reconcile configured; types={}, maxOutboxDepth={}, budgets: missing={}, drift={}, orphan={}x{} rows, deleteCap={}, repairCap={}, abortRatio={}. "
                        + "Missing/drift and orphan are switched on independently via the admin feature toggle page.",
                reconcileProperties.entityTypes(), reconcileProperties.maxOutboxDepth(), reconcileProperties.missingBatchSize(), reconcileProperties.driftBatchSize(),
                reconcileProperties.orphanPagesPerTick(), reconcileProperties.orphanPageSize(), reconcileProperties.orphanDeleteCapPerTick(),
                reconcileProperties.orphanRepairCapPerTick(), reconcileProperties.orphanAbortRatio());
    }

    /**
     * Re-derives the least recently verified entities and queues a repair where the index no longer matches.
     */
    @Scheduled(cron = "${artemis.scheduling.weaviate-reconcile-drift-time:0 */5 * * * *}")
    public void reconcileDrift() {
        runPass(ReconcilePass.DRIFT, featureToggleService.isFeatureEnabled(Feature.GlobalSearchReconcile), driftSweep::sweep);
    }

    /**
     * Queues a write for entities the index has never confirmed holding.
     */
    @Scheduled(cron = "${artemis.scheduling.weaviate-reconcile-missing-time:0 */10 * * * *}")
    public void reconcileMissing() {
        runPass(ReconcilePass.MISSING, featureToggleService.isFeatureEnabled(Feature.GlobalSearchReconcile), missingSweep::sweep);
    }

    /**
     * Scans the index for rows whose entity is gone, and for rows whose stored content disagrees with what was last
     * written. Runs overnight by default: the heaviest pass, and the only one whose deletions are inferred from the
     * index itself rather than confirmed against the database for each row (see the class javadoc for why that
     * makes it materially riskier than drift's own database-confirmed deletions).
     */
    @Scheduled(cron = "${artemis.scheduling.weaviate-reconcile-orphan-time:0 0 3 * * *}")
    public void reconcileOrphans() {
        runPass(ReconcilePass.ORPHAN, featureToggleService.isFeatureEnabled(Feature.GlobalSearchReconcileOrphan), orphanSweep::sweep);
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
