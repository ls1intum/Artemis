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

import de.tum.cit.aet.artemis.admin.dto.FeatureUsageActiveDaysDTO;
import de.tum.cit.aet.artemis.admin.dto.FeatureUsageEntryDTO;
import de.tum.cit.aet.artemis.admin.dto.FeatureUsageLabelCallsDTO;
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
     * Aggregates the window into one row per inventory entry, that is per endpoint, git operation or background feature.
     * <p>
     * Driven from the inventory with a LEFT JOIN, not from the buckets, so a feature with no usage in the window still
     * comes back with zero counts. Reporting those is the main purpose of the page, and an inner join or a query over the
     * buckets alone would silently drop exactly the rows that matter most.
     * <p>
     * The window condition sits in the join, not in a WHERE clause: in a WHERE clause it would filter away the rows whose
     * join produced no bucket, turning the outer join back into an inner one.
     *
     * @param from the first day to include
     * @return one entry per inventory row, in no particular order
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.admin.dto.FeatureUsageEntryDTO(
                feature.id,
                feature.featureKind,
                feature.module,
                feature.identifier,
                feature.featureLabel,
                feature.interaction,
                feature.resource,
                COALESCE(SUM(bucket.callCount), 0L),
                COALESCE(SUM(bucket.errorCount), 0L),
                COALESCE(SUM(bucket.durationSumMs), 0L),
                COALESCE(MAX(bucket.durationMaxMs), 0),
                COUNT(DISTINCT bucket.usageDay),
                MAX(bucket.usageDay),
                feature.lastRegisteredAt)
            FROM TrackedFeature feature
                LEFT JOIN FeatureUsageDaily bucket ON bucket.featureId = feature.id AND bucket.usageDay >= :from
            GROUP BY feature.id, feature.featureKind, feature.module, feature.identifier, feature.featureLabel, feature.interaction, feature.resource, feature.lastRegisteredAt
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
     * @return one entry per inventory row, in no particular order
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.admin.dto.FeatureUsageEntryDTO(
                feature.id,
                feature.featureKind,
                feature.module,
                feature.identifier,
                feature.featureLabel,
                feature.interaction,
                feature.resource,
                COALESCE(SUM(bucket.callCount), 0L),
                COALESCE(SUM(bucket.errorCount), 0L),
                COALESCE(SUM(bucket.durationSumMs), 0L),
                COALESCE(MAX(bucket.durationMaxMs), 0),
                COUNT(DISTINCT bucket.usageDay),
                MAX(bucket.usageDay),
                feature.lastRegisteredAt)
            FROM TrackedFeature feature
                LEFT JOIN FeatureUsageDaily bucket ON bucket.featureId = feature.id AND bucket.usageDay >= :from AND bucket.callerRole = :callerRole
            GROUP BY feature.id, feature.featureKind, feature.module, feature.identifier, feature.featureLabel, feature.interaction, feature.resource, feature.lastRegisteredAt
            """)
    List<FeatureUsageEntryDTO> findUsageSinceForRole(@Param("from") LocalDate from, @Param("callerRole") Role callerRole);

    /**
     * The distinct days each feature was used on, once counting actions and views and once counting actions only.
     * <p>
     * The per-endpoint {@code activeDays} in {@link FeatureUsageEntryDTO} cannot be combined into this afterwards:
     * summing double counts a day on which two endpoints behind one feature were both used, and taking the largest
     * undercounts when they were used on different days. Only a {@code COUNT(DISTINCT)} over the whole feature answers it.
     * <p>
     * Automatic and system calls are left out on purpose. A status probe that the client sends on every page load would
     * otherwise make every feature it belongs to active on every single day, which is exactly the signal active days exist
     * to separate from real use.
     *
     * @param from the first day to include
     * @return one entry per feature label that saw an action or a view in the window
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.admin.dto.FeatureUsageActiveDaysDTO(
                feature.featureLabel,
                COUNT(DISTINCT bucket.usageDay),
                COUNT(DISTINCT CASE WHEN feature.interaction = de.tum.cit.aet.artemis.core.domain.FeatureInteraction.ACTION THEN bucket.usageDay ELSE NULL END))
            FROM TrackedFeature feature
                JOIN FeatureUsageDaily bucket ON bucket.featureId = feature.id AND bucket.usageDay >= :from
            WHERE feature.featureLabel IS NOT NULL
                AND feature.interaction IN (de.tum.cit.aet.artemis.core.domain.FeatureInteraction.ACTION, de.tum.cit.aet.artemis.core.domain.FeatureInteraction.VIEW)
            GROUP BY feature.featureLabel
            """)
    List<FeatureUsageActiveDaysDTO> findActiveDaysPerFeatureSince(@Param("from") LocalDate from);

    /**
     * The distinct days each feature was used on, restricted to one caller role.
     *
     * @param from       the first day to include
     * @param callerRole the role to restrict the days to
     * @return one entry per feature label that saw an action or a view by this role in the window
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.admin.dto.FeatureUsageActiveDaysDTO(
                feature.featureLabel,
                COUNT(DISTINCT bucket.usageDay),
                COUNT(DISTINCT CASE WHEN feature.interaction = de.tum.cit.aet.artemis.core.domain.FeatureInteraction.ACTION THEN bucket.usageDay ELSE NULL END))
            FROM TrackedFeature feature
                JOIN FeatureUsageDaily bucket ON bucket.featureId = feature.id AND bucket.usageDay >= :from AND bucket.callerRole = :callerRole
            WHERE feature.featureLabel IS NOT NULL
                AND feature.interaction IN (de.tum.cit.aet.artemis.core.domain.FeatureInteraction.ACTION, de.tum.cit.aet.artemis.core.domain.FeatureInteraction.VIEW)
            GROUP BY feature.featureLabel
            """)
    List<FeatureUsageActiveDaysDTO> findActiveDaysPerFeatureSinceForRole(@Param("from") LocalDate from, @Param("callerRole") Role callerRole);

    /**
     * The most recent time any node reported its endpoints, i.e. the point the inventory was last confirmed against
     * running code.
     * <p>
     * Restricted to REST features on purpose, because they are the only kind re-registered from the mapping table on
     * every startup, which is what makes this timestamp mean "the inventory was confirmed". Git and background features
     * are registered the first time they are used, so their {@code lastRegisteredAt} is a first-use time. Including them
     * would let one such feature, first used later than the registration tolerance after startup, become the newest
     * timestamp and push the retirement reference past every REST endpoint registered at startup - marking all of them
     * retired and emptying the unused list the page is for.
     *
     * @return the newest REST registration timestamp, or empty if no REST feature has been registered yet
     */
    @Query("""
            SELECT MAX(feature.lastRegisteredAt)
            FROM TrackedFeature feature
            WHERE feature.featureKind = de.tum.cit.aet.artemis.core.domain.FeatureKind.REST
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
     * Totals the actions and views of the window per caller role. At most one row per role, so this is cheap enough to run
     * alongside the overview.
     * <p>
     * Automatic calls are left out for the same reason as everywhere else: a poll on every page load says how many pages
     * each role opened, not which features it used.
     *
     * @param from the first day to include
     * @return one entry per role that made at least one action or view
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.admin.dto.FeatureUsageRoleShareDTO(bucket.callerRole, SUM(bucket.callCount))
            FROM TrackedFeature feature
                JOIN FeatureUsageDaily bucket ON bucket.featureId = feature.id
            WHERE bucket.usageDay >= :from
                AND feature.interaction IN (de.tum.cit.aet.artemis.core.domain.FeatureInteraction.ACTION, de.tum.cit.aet.artemis.core.domain.FeatureInteraction.VIEW)
            GROUP BY bucket.callerRole
            ORDER BY SUM(bucket.callCount) DESC
            """)
    List<FeatureUsageRoleShareDTO> findRoleDistributionSince(@Param("from") LocalDate from);

    /**
     * Totals calls per feature label and interaction over a closed day range.
     * <p>
     * Used by the weekly digest to compare the window against the one before it. An inner join is right here, unlike in the
     * report: this only supplies the comparison figure, and a feature with no calls in the earlier window simply has nothing
     * to compare against. Retired entries are included on both sides of the comparison, like in the report, so that an
     * endpoint removed between the two windows does not read as a drop in use.
     *
     * @param from the first day to include
     * @param to   the last day to include
     * @return one entry per feature label and interaction that saw at least one call in the range
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.admin.dto.FeatureUsageLabelCallsDTO(feature.featureLabel, feature.interaction, SUM(bucket.callCount))
            FROM TrackedFeature feature
                JOIN FeatureUsageDaily bucket ON bucket.featureId = feature.id
            WHERE bucket.usageDay >= :from
                AND bucket.usageDay <= :to
            GROUP BY feature.featureLabel, feature.interaction
            """)
    List<FeatureUsageLabelCallsDTO> findFeatureCallsBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /**
     * Returns the daily calls of one feature per interaction, for the trend chart. Days without calls are absent.
     *
     * @param featureLabel the feature to chart, summed over every endpoint that serves it
     * @param from         the first day to include
     * @return the daily totals per interaction in chronological order
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.admin.dto.FeatureUsageTrendPointDTO(bucket.usageDay, feature.interaction, SUM(bucket.callCount))
            FROM TrackedFeature feature
                JOIN FeatureUsageDaily bucket ON bucket.featureId = feature.id
            WHERE feature.featureLabel = :featureLabel
                AND bucket.usageDay >= :from
            GROUP BY bucket.usageDay, feature.interaction
            ORDER BY bucket.usageDay ASC
            """)
    List<FeatureUsageTrendPointDTO> findDailyUsageOfFeatureSince(@Param("featureLabel") String featureLabel, @Param("from") LocalDate from);

    /**
     * Returns the daily calls of one feature per interaction, restricted to one caller role.
     * <p>
     * The overview can be narrowed to a role, and the chart has to answer the same question: summing every role here
     * would silently widen the numbers the moment a chart is opened on a filtered table.
     *
     * @param featureLabel the feature to chart
     * @param from         the first day to include
     * @param callerRole   the role to restrict the totals to
     * @return the daily totals per interaction in chronological order
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.admin.dto.FeatureUsageTrendPointDTO(bucket.usageDay, feature.interaction, SUM(bucket.callCount))
            FROM TrackedFeature feature
                JOIN FeatureUsageDaily bucket ON bucket.featureId = feature.id
            WHERE feature.featureLabel = :featureLabel
                AND bucket.usageDay >= :from
                AND bucket.callerRole = :callerRole
            GROUP BY bucket.usageDay, feature.interaction
            ORDER BY bucket.usageDay ASC
            """)
    List<FeatureUsageTrendPointDTO> findDailyUsageOfFeatureSinceForRole(@Param("featureLabel") String featureLabel, @Param("from") LocalDate from,
            @Param("callerRole") Role callerRole);

    /**
     * Returns the daily calls of individual inventory rows per interaction, for the chart of a single endpoint.
     *
     * @param featureIds the inventory rows to chart, summed per day
     * @param from       the first day to include
     * @return the daily totals per interaction in chronological order
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.admin.dto.FeatureUsageTrendPointDTO(bucket.usageDay, feature.interaction, SUM(bucket.callCount))
            FROM TrackedFeature feature
                JOIN FeatureUsageDaily bucket ON bucket.featureId = feature.id
            WHERE feature.id IN :featureIds
                AND bucket.usageDay >= :from
            GROUP BY bucket.usageDay, feature.interaction
            ORDER BY bucket.usageDay ASC
            """)
    List<FeatureUsageTrendPointDTO> findDailyUsageSince(@Param("featureIds") Collection<Long> featureIds, @Param("from") LocalDate from);

    /**
     * Returns the daily calls of individual inventory rows per interaction, restricted to one caller role.
     *
     * @param featureIds the inventory rows to chart, summed per day
     * @param from       the first day to include
     * @param callerRole the role to restrict the totals to
     * @return the daily totals per interaction in chronological order
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.admin.dto.FeatureUsageTrendPointDTO(bucket.usageDay, feature.interaction, SUM(bucket.callCount))
            FROM TrackedFeature feature
                JOIN FeatureUsageDaily bucket ON bucket.featureId = feature.id
            WHERE feature.id IN :featureIds
                AND bucket.usageDay >= :from
                AND bucket.callerRole = :callerRole
            GROUP BY bucket.usageDay, feature.interaction
            ORDER BY bucket.usageDay ASC
            """)
    List<FeatureUsageTrendPointDTO> findDailyUsageSinceForRole(@Param("featureIds") Collection<Long> featureIds, @Param("from") LocalDate from,
            @Param("callerRole") Role callerRole);
}
