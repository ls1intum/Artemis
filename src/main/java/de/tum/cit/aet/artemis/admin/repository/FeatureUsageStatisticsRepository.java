package de.tum.cit.aet.artemis.admin.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.admin.dto.FeatureUsageEntryDTO;
import de.tum.cit.aet.artemis.admin.dto.FeatureUsageModuleCallsDTO;
import de.tum.cit.aet.artemis.admin.dto.FeatureUsageRoleShareDTO;
import de.tum.cit.aet.artemis.admin.dto.FeatureUsageTrendPointDTO;
import de.tum.cit.aet.artemis.core.domain.TrackedFeature;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.core.security.Role;

/**
 * Read side of the feature usage analysis. Aggregates the daily buckets for the admin page.
 */
@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface FeatureUsageStatisticsRepository extends ArtemisJpaRepository<TrackedFeature, Long> {

    /**
     * Aggregates the window into one row per feature.
     * <p>
     * Driven from the inventory with a LEFT JOIN, not from the buckets, so a feature with no usage in the window still
     * comes back with zero counts. Reporting those is the main purpose of the page, and an inner join or a query over the
     * buckets alone would silently drop exactly the rows that matter most.
     * <p>
     * The window condition sits in the join, not in a WHERE clause: in a WHERE clause it would filter away the rows whose
     * join produced no bucket, turning the outer join back into an inner one.
     *
     * @param from the first day to include
     * @return one entry per known feature, in no particular order
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.admin.dto.FeatureUsageEntryDTO(
                feature.id,
                feature.featureKind,
                feature.module,
                feature.identifier,
                feature.featureLabel,
                COALESCE(SUM(bucket.callCount), 0L),
                COALESCE(SUM(bucket.errorCount), 0L),
                COALESCE(SUM(bucket.durationSumMs), 0L),
                COALESCE(MAX(bucket.durationMaxMs), 0),
                COUNT(DISTINCT bucket.usageDay),
                MAX(bucket.usageDay),
                feature.lastRegisteredAt)
            FROM TrackedFeature feature
                LEFT JOIN FeatureUsageDaily bucket ON bucket.featureId = feature.id AND bucket.usageDay >= :from
            GROUP BY feature.id, feature.featureKind, feature.module, feature.identifier, feature.featureLabel, feature.lastRegisteredAt
            """)
    List<FeatureUsageEntryDTO> findUsageSince(@Param("from") LocalDate from);

    /**
     * Same aggregate, restricted to callers of one role.
     * <p>
     * A separate method rather than a nullable parameter because the role predicate belongs in the join condition, next to
     * the window condition: in a WHERE clause it would drop every feature the role never called, which is precisely the
     * set the caller is asking about ("which instructor features does nobody use").
     *
     * @param from       the first day to include
     * @param callerRole the role to restrict the counters to
     * @return one entry per known feature, in no particular order
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.admin.dto.FeatureUsageEntryDTO(
                feature.id,
                feature.featureKind,
                feature.module,
                feature.identifier,
                feature.featureLabel,
                COALESCE(SUM(bucket.callCount), 0L),
                COALESCE(SUM(bucket.errorCount), 0L),
                COALESCE(SUM(bucket.durationSumMs), 0L),
                COALESCE(MAX(bucket.durationMaxMs), 0),
                COUNT(DISTINCT bucket.usageDay),
                MAX(bucket.usageDay),
                feature.lastRegisteredAt)
            FROM TrackedFeature feature
                LEFT JOIN FeatureUsageDaily bucket ON bucket.featureId = feature.id AND bucket.usageDay >= :from AND bucket.callerRole = :callerRole
            GROUP BY feature.id, feature.featureKind, feature.module, feature.identifier, feature.featureLabel, feature.lastRegisteredAt
            """)
    List<FeatureUsageEntryDTO> findUsageSinceForRole(@Param("from") LocalDate from, @Param("callerRole") Role callerRole);

    /**
     * The most recent time any node reported its endpoints, i.e. the point the inventory was last confirmed against
     * running code.
     *
     * @return the newest registration timestamp, or empty if the inventory has never been written
     */
    @Query("""
            SELECT MAX(feature.lastRegisteredAt)
            FROM TrackedFeature feature
            """)
    Optional<Instant> findInventoryRefreshedAt();

    /**
     * When this deployment started recording, i.e. when the first feature entered the inventory.
     * <p>
     * Reported so the page cannot overstate its own confidence. "Unused over 180 days" means something quite different on
     * an instance that has been recording for a year than on one upgraded last week, and nothing else on the page would
     * reveal the difference.
     *
     * @return the oldest first-seen timestamp, or empty if the inventory has never been written
     */
    @Query("""
            SELECT MIN(feature.firstSeenAt)
            FROM TrackedFeature feature
            """)
    Optional<Instant> findRecordingSince();

    /**
     * Totals the window per caller role. At most one row per role, so this is cheap enough to run alongside the overview.
     *
     * @param from the first day to include
     * @return one entry per role that made at least one call
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.admin.dto.FeatureUsageRoleShareDTO(bucket.callerRole, SUM(bucket.callCount))
            FROM FeatureUsageDaily bucket
            WHERE bucket.usageDay >= :from
            GROUP BY bucket.callerRole
            ORDER BY SUM(bucket.callCount) DESC
            """)
    List<FeatureUsageRoleShareDTO> findRoleDistributionSince(@Param("from") LocalDate from);

    /**
     * Totals calls per module over a closed day range.
     * <p>
     * Used by the weekly digest to compare the window against the one before it. An inner join is right here, unlike in the
     * report: this only supplies the comparison figure, and a module with no calls in the earlier window simply has nothing
     * to compare against.
     *
     * @param from the first day to include
     * @param to   the last day to include
     * @return one entry per module that saw at least one call in the range
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.admin.dto.FeatureUsageModuleCallsDTO(feature.module, SUM(bucket.callCount))
            FROM TrackedFeature feature
                JOIN FeatureUsageDaily bucket ON bucket.featureId = feature.id
            WHERE bucket.usageDay >= :from
                AND bucket.usageDay <= :to
            GROUP BY feature.module
            """)
    List<FeatureUsageModuleCallsDTO> findModuleCallsBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /**
     * Returns the daily calls of a single feature, for the trend chart. Days without usage are absent.
     *
     * @param featureIds the inventory rows to chart, summed per day. A labelled feature usually covers several endpoints,
     *                       and a chart of one of them would not be the chart of the feature.
     * @param from       the first day to include
     * @return the daily totals in chronological order
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.admin.dto.FeatureUsageTrendPointDTO(bucket.usageDay, SUM(bucket.callCount))
            FROM FeatureUsageDaily bucket
            WHERE bucket.featureId IN :featureIds
                AND bucket.usageDay >= :from
            GROUP BY bucket.usageDay
            ORDER BY bucket.usageDay ASC
            """)
    List<FeatureUsageTrendPointDTO> findDailyUsageSince(@Param("featureIds") Collection<Long> featureIds, @Param("from") LocalDate from);
}
