package de.tum.cit.aet.artemis.admin.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.admin.dto.FeatureAdoptionDTO;
import de.tum.cit.aet.artemis.admin.dto.FeatureUsageActiveDaysDTO;
import de.tum.cit.aet.artemis.admin.dto.FeatureUsageEntryDTO;
import de.tum.cit.aet.artemis.admin.dto.FeatureUsageOverviewDTO;
import de.tum.cit.aet.artemis.admin.dto.FeatureUsageStatus;
import de.tum.cit.aet.artemis.admin.dto.FeatureUsageTrendPointDTO;
import de.tum.cit.aet.artemis.admin.dto.UserFeatureUsageDTO;
import de.tum.cit.aet.artemis.admin.repository.FeatureUsageStatisticsRepository;
import de.tum.cit.aet.artemis.core.domain.FeatureInteraction;
import de.tum.cit.aet.artemis.core.domain.FeatureKind;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.service.featureusage.FeatureAdoptionContributor;
import de.tum.cit.aet.artemis.core.service.featureusage.FeatureAdoptionEntry;
import de.tum.cit.aet.artemis.core.service.featureusage.UserFeature;

/**
 * Assembles the feature usage report for the admin page and the weekly email.
 * <p>
 * The inventory is kept per endpoint; this is where it is rolled up into the user-facing features of the
 * {@link UserFeature} catalogue. Only actions and views count as use. Automatic calls by the client and calls by other
 * systems are reported next to them, but never decide whether a feature was used.
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

    private static final Map<String, UserFeature> FEATURES_BY_NAME = Arrays.stream(UserFeature.values())
            .collect(Collectors.toUnmodifiableMap(UserFeature::name, Function.identity()));

    /**
     * The moment before which a REST registration counts as retired, given the newest registration in the inventory.
     *
     * @param inventoryRefreshedAt the newest REST registration timestamp
     * @return the cutoff to compare a feature's {@code lastRegisteredAt} against
     */
    private static Instant retirementCutoff(Instant inventoryRefreshedAt) {
        return inventoryRefreshedAt.minus(REGISTRATION_TOLERANCE);
    }

    /**
     * Builds the report for the last {@code days} days.
     * <p>
     * The endpoints are grouped into user-facing features here rather than on the client, so the page and the weekly email
     * are built from one aggregation and cannot disagree.
     *
     * @param days       the length of the window
     * @param callerRole restrict the counters to callers of this role, or {@code null} for every caller
     * @return the report, with every catalogue feature and every inventory row
     */
    public FeatureUsageOverviewDTO getOverview(int days, @Nullable Role callerRole) {
        LocalDate from = windowStart(days);
        Instant inventoryRefreshedAt = featureUsageStatisticsRepository.findInventoryRefreshedAt().orElse(Instant.EPOCH);
        Instant retiredBefore = retirementCutoff(inventoryRefreshedAt);
        List<FeatureUsageEntryDTO> endpoints = (callerRole == null ? featureUsageStatisticsRepository.findUsageSince(from)
                : featureUsageStatisticsRepository.findUsageSinceForRole(from, callerRole)).stream().map(entry -> entry.withRetired(isRetired(entry, retiredBefore)))
                .sorted(Comparator.comparingLong(FeatureUsageEntryDTO::callCount).reversed().thenComparing(FeatureUsageEntryDTO::module)
                        .thenComparing(FeatureUsageEntryDTO::identifier))
                .toList();

        // Grouped per feature in the database: the per-endpoint counts cannot be combined into this without either double
        // counting a shared day or losing the days only one endpoint was used on.
        Map<String, FeatureUsageActiveDaysDTO> activeDaysByLabel = (callerRole == null ? featureUsageStatisticsRepository.findActiveDaysPerFeatureSince(from)
                : featureUsageStatisticsRepository.findActiveDaysPerFeatureSinceForRole(from, callerRole)).stream()
                .collect(Collectors.toMap(FeatureUsageActiveDaysDTO::featureLabel, Function.identity()));
        List<UserFeatureUsageDTO> features = summarizePerFeature(endpoints, activeDaysByLabel);

        return new FeatureUsageOverviewDTO(days, from, callerRole, countFeatures(features, feature -> feature.status() != FeatureUsageStatus.NOT_AVAILABLE),
                countFeatures(features, feature -> feature.status() == FeatureUsageStatus.USED),
                countFeatures(features, feature -> feature.status() == FeatureUsageStatus.ONLY_AUTOMATIC),
                countFeatures(features, feature -> feature.status() == FeatureUsageStatus.UNUSED),
                countFeatures(features, feature -> feature.status() == FeatureUsageStatus.NOT_AVAILABLE), countFeatures(features, UserFeatureUsageDTO::noActions),
                endpoints.stream().filter(FeatureUsageEntryDTO::retired).count(), sumCalls(endpoints, FeatureInteraction.ACTION), sumCalls(endpoints, FeatureInteraction.VIEW),
                sumCalls(endpoints, FeatureInteraction.AUTOMATIC), sumCalls(endpoints, FeatureInteraction.SYSTEM), inventoryRefreshedAt,
                featureUsageStatisticsRepository.findRecordingSince().orElse(null), features, endpoints, featureUsageStatisticsRepository.findRoleDistributionSince(from));
    }

    /**
     * Resolves the feature an inventory row serves.
     * <p>
     * A row that is no longer offered keeps the label it was last registered with, which may be a label from before the
     * catalogue or a constant that has since been removed. Such a row is reported as an endpoint, but belongs to no feature.
     *
     * @param featureLabel the stored label
     * @return the catalogue entry, or {@code null} if the label resolves to none
     */
    @Nullable
    public static UserFeature resolveFeature(@Nullable String featureLabel) {
        return featureLabel == null ? null : FEATURES_BY_NAME.get(featureLabel);
    }

    /**
     * Rolls the inventory rows up into one entry per catalogue feature, in catalogue order.
     * <p>
     * Every catalogue feature gets an entry, including the ones without a single inventory row: those are the features this
     * deployment does not offer, and leaving them out would hide that from the page. Retired rows contribute their calls,
     * because they are part of the feature's history in the window, but not to whether the feature is still offered.
     *
     * @param endpoints         the inventory rows of the window
     * @param activeDaysByLabel the exact distinct-day counts per feature label
     * @return one entry per catalogue feature
     */
    static List<UserFeatureUsageDTO> summarizePerFeature(List<FeatureUsageEntryDTO> endpoints, Map<String, FeatureUsageActiveDaysDTO> activeDaysByLabel) {
        Map<UserFeature, List<FeatureUsageEntryDTO>> endpointsByFeature = new EnumMap<>(UserFeature.class);
        for (FeatureUsageEntryDTO endpoint : endpoints) {
            UserFeature feature = resolveFeature(endpoint.featureLabel());
            if (feature != null) {
                endpointsByFeature.computeIfAbsent(feature, key -> new ArrayList<>()).add(endpoint);
            }
        }
        return Arrays.stream(UserFeature.values()).map(feature -> summarize(feature, endpointsByFeature.getOrDefault(feature, List.of()), activeDaysByLabel.get(feature.name())))
                .toList();
    }

    private static UserFeatureUsageDTO summarize(UserFeature feature, List<FeatureUsageEntryDTO> endpoints, @Nullable FeatureUsageActiveDaysDTO activeDays) {
        List<FeatureUsageEntryDTO> offered = endpoints.stream().filter(endpoint -> !endpoint.retired()).toList();
        List<FeatureUsageEntryDTO> use = endpoints.stream().filter(FeatureUsageEntryDTO::countsAsUse).toList();
        long actionCount = sumCalls(endpoints, FeatureInteraction.ACTION);
        long viewCount = sumCalls(endpoints, FeatureInteraction.VIEW);
        long automaticCount = sumCalls(endpoints, FeatureInteraction.AUTOMATIC);
        long systemCount = sumCalls(endpoints, FeatureInteraction.SYSTEM);
        boolean hasActionEndpoints = offered.stream().anyMatch(endpoint -> endpoint.interaction() == FeatureInteraction.ACTION);

        FeatureUsageStatus status;
        if (offered.isEmpty()) {
            status = FeatureUsageStatus.NOT_AVAILABLE;
        }
        else if (actionCount + viewCount > 0) {
            status = FeatureUsageStatus.USED;
        }
        else if (automaticCount + systemCount > 0) {
            status = FeatureUsageStatus.ONLY_AUTOMATIC;
        }
        else {
            status = FeatureUsageStatus.UNUSED;
        }
        boolean noActions = status == FeatureUsageStatus.USED && hasActionEndpoints && actionCount == 0;

        return new UserFeatureUsageDTO(feature, feature.getArea(), status, noActions, actionCount, viewCount, automaticCount, systemCount,
                use.stream().mapToLong(FeatureUsageEntryDTO::errorCount).sum(), use.stream().mapToLong(FeatureUsageEntryDTO::durationSumMs).sum(),
                use.stream().mapToInt(FeatureUsageEntryDTO::durationMaxMs).max().orElse(0), activeDays == null ? 0 : activeDays.activeDays(),
                activeDays == null ? 0 : activeDays.actionDays(), latestDay(use),
                latestDay(use.stream().filter(endpoint -> endpoint.interaction() == FeatureInteraction.ACTION).toList()),
                offered.stream().map(FeatureUsageEntryDTO::module).distinct().sorted().toList(), offered.size(), hasActionEndpoints);
    }

    @Nullable
    private static LocalDate latestDay(List<FeatureUsageEntryDTO> endpoints) {
        return endpoints.stream().map(FeatureUsageEntryDTO::lastUsedDay).filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null);
    }

    private static long sumCalls(List<FeatureUsageEntryDTO> endpoints, FeatureInteraction interaction) {
        return endpoints.stream().filter(endpoint -> endpoint.interaction() == interaction).mapToLong(FeatureUsageEntryDTO::callCount).sum();
    }

    private static long countFeatures(List<UserFeatureUsageDTO> features, Predicate<UserFeatureUsageDTO> condition) {
        return features.stream().filter(condition).count();
    }

    /**
     * The first day of a window of {@code days} days that ends today, in UTC like the buckets.
     *
     * @param days the length of the window
     * @return the first day to include
     */
    static LocalDate windowStart(int days) {
        return LocalDate.now(ZoneOffset.UTC).minusDays(days - 1L);
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
     * Returns the daily calls of one feature per interaction over the last {@code days} days.
     *
     * @param feature    the feature to chart, summed over every endpoint that serves it
     * @param days       the length of the window
     * @param callerRole optional filter, restricting the totals to callers whose highest global role is this one
     * @return the daily totals per interaction in chronological order, without the days that saw no calls
     */
    public List<FeatureUsageTrendPointDTO> getFeatureTrend(UserFeature feature, int days, @Nullable Role callerRole) {
        LocalDate from = windowStart(days);
        // Mirrors getOverview: a chart opened on a role-filtered table has to answer the same question the table does.
        if (callerRole != null) {
            return featureUsageStatisticsRepository.findDailyUsageOfFeatureSinceForRole(feature.name(), from, callerRole);
        }
        return featureUsageStatisticsRepository.findDailyUsageOfFeatureSince(feature.name(), from);
    }

    /**
     * Returns the daily calls of individual inventory rows per interaction over the last {@code days} days.
     *
     * @param featureIds the inventory rows to chart, summed per day
     * @param days       the length of the window
     * @param callerRole optional filter, restricting the totals to callers whose highest global role is this one
     * @return the daily totals per interaction in chronological order, without the days that saw no calls
     */
    public List<FeatureUsageTrendPointDTO> getTrend(Collection<Long> featureIds, int days, @Nullable Role callerRole) {
        if (featureIds.isEmpty()) {
            return List.of();
        }
        LocalDate from = windowStart(days);
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
     * @return the adoption entries, grouped by module and then by setting
     */
    public List<FeatureAdoptionDTO> getAdoption() {
        return adoptionContributors.stream().flatMap(contributor -> collectSafely(contributor).stream())
                .map(entry -> new FeatureAdoptionDTO(entry.module(), entry.key(), entry.feature(), entry.count(), entry.total()))
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
