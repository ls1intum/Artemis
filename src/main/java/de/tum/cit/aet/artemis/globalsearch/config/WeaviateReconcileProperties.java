package de.tum.cit.aet.artemis.globalsearch.config;

import java.util.List;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Positive;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for the reconcile passes that keep the {@code SearchableEntities} index in step with
 * the database. Uses a Java record for immutable configuration.
 * <p>
 * The three {@code *SweepEnabled} flags only seed the runtime feature toggle's first-ever value (see
 * {@code FeatureToggleService.initFeatures}); once that toggle exists, it — not these flags — decides whether a
 * pass runs, live, from the admin feature toggle page, no restart needed. They default to false because the
 * missing pass queues the entire corpus the first time it runs, and the orphan pass deletes, so enabling either
 * is an operational decision rather than a deployment side effect; the same caution applies to switching the
 * matching toggle on.
 * <p>
 * The two throttles protect different resources. {@code maxOutboxDepth} limits work handed to Weaviate; the
 * per-pass budgets limit queries against the database. A healthy system queues nothing, so the depth limit never
 * engages and the budgets are the only active protection.
 *
 * @param missingSweepEnabled    seeds the toggle covering the pass that finds never-indexed entities
 * @param driftSweepEnabled      seeds the toggle covering the pass that re-derives entities and compares content
 * @param orphanSweepEnabled     seeds the toggle covering the pass that scans the index; its deletions are inferred
 *                                   from the index alone rather than confirmed per entity against the database,
 *                                   unlike drift's own database-confirmed deletions
 * @param entityTypes            the entity types all three passes manage; posts and answer posts are excluded because they
 *                                   dominate the corpus and would stretch every other type's revisit period. A type left out
 *                                   here is never repaired and never deleted
 * @param maxOutboxDepth         how much queued work is allowed before the passes stop adding more; the only bound on how
 *                                   long a live metadata write can wait behind reconcile work. Each pass checks this once per
 *                                   tick, not per row, so every per-tick batch below is sized to stay well under it even in
 *                                   the worst case (an empty ledger, where every examined row turns out to need a write) —
 *                                   the check is a circuit breaker between ticks, not a hard cap within one
 * @param missingBatchSize       entity ids examined per tick by the missing pass (identity only, no entity is loaded)
 * @param driftBatchSize         entities re-derived per tick by the drift pass; far smaller, since each check loads an entity
 * @param orphanPageSize         index rows read per page by the orphan pass
 * @param orphanPagesPerTick     pages read per tick by the orphan pass
 * @param orphanDeleteCapPerTick the most rows a single tick may queue for deletion
 * @param orphanRepairCapPerTick the most rows a single tick may queue for a content rewrite
 * @param orphanAbortRatio       the share of scanned rows looking orphaned that aborts the pass instead; a high proportion
 *                                   indicates a bug or a stale read rather than real orphans
 */
@Validated
@ConfigurationProperties(prefix = "artemis.weaviate.reconcile", ignoreUnknownFields = false)
public record WeaviateReconcileProperties(@DefaultValue("false") boolean missingSweepEnabled, @DefaultValue("false") boolean driftSweepEnabled,
        @DefaultValue("false") boolean orphanSweepEnabled, @DefaultValue( {
                "course", "lecture", "lecture_unit", "exam", "exercise", "faq", "channel" }) List<String> entityTypes,
        @DefaultValue("500") @Positive int maxOutboxDepth, @DefaultValue("100") @Positive int missingBatchSize, @DefaultValue("200") @Positive int driftBatchSize,
        @DefaultValue("1000") @Positive int orphanPageSize, @DefaultValue("5") @Positive int orphanPagesPerTick, @DefaultValue("100") @Positive int orphanDeleteCapPerTick,
        @DefaultValue("100") @Positive int orphanRepairCapPerTick, @DefaultValue("0.25") @Positive @DecimalMax("1.0") double orphanAbortRatio){

    /**
     * Returns whether a type is managed by the reconcile passes.
     *
     * @param entityType the {@code SearchableEntitySchema.TypeValues} discriminator, or {@code null} (a bulk
     *                       delete's outbox entry carries no single type), which is never managed
     * @return true if the passes may repair and remove rows of this type
     */
    public boolean managesEntityType(String entityType) {
        return entityType != null && entityTypes.contains(entityType);
    }
}
