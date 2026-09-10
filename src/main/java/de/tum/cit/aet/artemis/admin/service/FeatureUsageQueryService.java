package de.tum.cit.aet.artemis.admin.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.admin.dto.FeatureAdoptionDTO;
import de.tum.cit.aet.artemis.admin.dto.FeatureUsageEntryDTO;
import de.tum.cit.aet.artemis.admin.dto.FeatureUsageOverviewDTO;
import de.tum.cit.aet.artemis.admin.dto.FeatureUsageTrendPointDTO;
import de.tum.cit.aet.artemis.admin.repository.FeatureUsageStatisticsRepository;
import de.tum.cit.aet.artemis.core.domain.FeatureKind;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.service.featureusage.FeatureAdoptionContributor;
import de.tum.cit.aet.artemis.core.service.featureusage.FeatureAdoptionEntry;

/**
 * Assembles the feature usage report for the admin page.
 */
@Profile(PROFILE_CORE)
@Service
@Lazy
public class FeatureUsageQueryService {

    private static final Logger log = LoggerFactory.getLogger(FeatureUsageQueryService.class);

    private final FeatureUsageStatisticsRepository featureUsageStatisticsRepository;

    private final List<FeatureAdoptionContributor> adoptionContributors;

    public FeatureUsageQueryService(FeatureUsageStatisticsRepository featureUsageStatisticsRepository, List<FeatureAdoptionContributor> adoptionContributors) {
        this.featureUsageStatisticsRepository = featureUsageStatisticsRepository;
        this.adoptionContributors = adoptionContributors;
    }

    /**
     * How much older than the newest registration a feature may be and still count as currently offered.
     * <p>
     * Nodes of a cluster restart at different moments, and a rolling deployment can spread over a while, so the newest
     * timestamp alone would classify perfectly live features as retired for as long as one node lags. A day of slack is far
     * longer than any deployment and far shorter than a release cycle.
     */
    private static final Duration REGISTRATION_TOLERANCE = Duration.ofDays(1);

    /**
     * The moment before which a REST registration counts as retired, given the newest registration in the inventory.
     * <p>
     * Exposed because the weekly digest has to apply exactly the same cutoff. Deriving it twice from separate constants is
     * how the email and the page would drift apart, and the email states that retired entries are excluded from its counts.
     *
     * @param inventoryRefreshedAt the newest REST registration timestamp
     * @return the cutoff to compare a feature's {@code lastRegisteredAt} against
     */
    public static Instant retirementCutoff(Instant inventoryRefreshedAt) {
        return inventoryRefreshedAt.minus(REGISTRATION_TOLERANCE);
    }

    /**
     * Builds the report for the last {@code days} days.
     *
     * @param days       the length of the window
     * @param callerRole restrict the counters to callers of this role, or {@code null} for every caller
     * @return the report, with the busiest features first and the unused ones last
     */
    public FeatureUsageOverviewDTO getOverview(int days, @Nullable Role callerRole) {
        LocalDate from = LocalDate.now(ZoneOffset.UTC).minusDays(days - 1L);
        List<FeatureUsageEntryDTO> features = (callerRole == null ? featureUsageStatisticsRepository.findUsageSince(from)
                : featureUsageStatisticsRepository.findUsageSinceForRole(from, callerRole)).stream()
                .sorted(Comparator.comparingLong(FeatureUsageEntryDTO::callCount).reversed().thenComparing(FeatureUsageEntryDTO::module)
                        .thenComparing(FeatureUsageEntryDTO::identifier))
                .toList();

        Instant inventoryRefreshedAt = featureUsageStatisticsRepository.findInventoryRefreshedAt().orElse(Instant.EPOCH);
        Instant retiredBefore = retirementCutoff(inventoryRefreshedAt);
        List<FeatureUsageEntryDTO> annotated = features.stream().map(feature -> feature.withRetired(isRetired(feature, retiredBefore))).toList();

        long retiredFeatures = annotated.stream().filter(FeatureUsageEntryDTO::retired).count();
        // A retired feature's zero usage is not a finding, so it must not inflate the one number the page leads with
        long unusedFeatures = annotated.stream().filter(feature -> feature.callCount() == 0 && !feature.retired()).count();
        long totalCalls = annotated.stream().mapToLong(FeatureUsageEntryDTO::callCount).sum();

        // Grouped per label in the database: the client cannot derive this from the per-endpoint counts without either
        // double counting a shared day or losing the days only one endpoint was used on.
        var activeDaysPerFeature = callerRole == null ? featureUsageStatisticsRepository.findActiveDaysPerFeatureSince(from)
                : featureUsageStatisticsRepository.findActiveDaysPerFeatureSinceForRole(from, callerRole);

        return new FeatureUsageOverviewDTO(days, from, callerRole, annotated.size(), unusedFeatures, retiredFeatures, totalCalls, inventoryRefreshedAt,
                featureUsageStatisticsRepository.findRecordingSince().orElse(null), annotated, featureUsageStatisticsRepository.findRoleDistributionSince(from),
                activeDaysPerFeature);
    }

    /**
     * Only REST features can be recognised as retired, because they are re-registered from the mapping table on every
     * startup. Git and background features are registered the first time they are used, so an old timestamp there means
     * "rarely used", not "gone", and treating it as gone would quietly hide them from the report.
     */
    private static boolean isRetired(FeatureUsageEntryDTO feature, Instant retiredBefore) {
        return feature.featureKind() == FeatureKind.REST && feature.lastRegisteredAt().isBefore(retiredBefore);
    }

    /**
     * Returns the daily usage of one feature over the last {@code days} days.
     *
     * @param featureIds the inventory rows behind the feature, summed per day
     * @param days       the length of the window
     * @param callerRole optional filter, restricting the totals to callers whose highest global role is this one
     * @return the daily totals in chronological order, without the days that saw no usage
     */
    public List<FeatureUsageTrendPointDTO> getTrend(Collection<Long> featureIds, int days, @Nullable Role callerRole) {
        if (featureIds.isEmpty()) {
            return List.of();
        }
        LocalDate from = LocalDate.now(ZoneOffset.UTC).minusDays(days - 1L);
        // Mirrors getOverview: a chart opened on a role-filtered table has to answer the same question the table does.
        if (callerRole != null) {
            return featureUsageStatisticsRepository.findDailyUsageSinceForRole(featureIds, from, callerRole);
        }
        return featureUsageStatisticsRepository.findDailyUsageSince(featureIds, from);
    }

    /**
     * Collects the adoption counts of every module that reports any.
     * <p>
     * A contributor that fails must not take the whole page down with it, so each is asked separately and a failure is
     * reported as no entries rather than as an error.
     *
     * @return the adoption entries, grouped by module and then by feature
     */
    public List<FeatureAdoptionDTO> getAdoption() {
        return adoptionContributors.stream().flatMap(contributor -> collectSafely(contributor).stream())
                .map(entry -> new FeatureAdoptionDTO(entry.module(), entry.key(), entry.count(), entry.total()))
                .sorted(Comparator.comparing(FeatureAdoptionDTO::module).thenComparing(FeatureAdoptionDTO::key)).toList();
    }

    private List<FeatureAdoptionEntry> collectSafely(FeatureAdoptionContributor contributor) {
        try {
            return contributor.collectAdoption();
        }
        catch (Exception e) {
            log.error("Feature adoption contributor {} failed, its entries are missing from the report", contributor.getClass().getSimpleName(), e);
            return List.of();
        }
    }
}
