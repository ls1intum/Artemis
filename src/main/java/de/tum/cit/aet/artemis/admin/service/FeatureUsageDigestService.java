package de.tum.cit.aet.artemis.admin.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import de.tum.cit.aet.artemis.admin.dto.FeatureUsageDigestDTO;
import de.tum.cit.aet.artemis.admin.dto.FeatureUsageEntryDTO;
import de.tum.cit.aet.artemis.admin.dto.FeatureUsageModuleCallsDTO;
import de.tum.cit.aet.artemis.admin.dto.FeatureUsageModuleSummaryDTO;
import de.tum.cit.aet.artemis.admin.dto.FeatureUsageOverviewDTO;
import de.tum.cit.aet.artemis.admin.repository.FeatureUsageStatisticsRepository;

/**
 * Aggregates the weekly feature usage digest.
 * <p>
 * Built on the same report the admin page shows, so the email and the page can never disagree, and rolled up per module
 * because a per-endpoint list is unreadable in an email and would defeat the point: the digest is there to make someone
 * open the page, not to replace it.
 */
@Profile(PROFILE_CORE)
@Service
@Lazy
public class FeatureUsageDigestService {

    /** A week. The email is weekly, so anything else would compare unlike windows. */
    public static final int DIGEST_WINDOW_IN_DAYS = 7;

    private final FeatureUsageQueryService featureUsageQueryService;

    private final FeatureUsageStatisticsRepository featureUsageStatisticsRepository;

    public FeatureUsageDigestService(FeatureUsageQueryService featureUsageQueryService, FeatureUsageStatisticsRepository featureUsageStatisticsRepository) {
        this.featureUsageQueryService = featureUsageQueryService;
        this.featureUsageStatisticsRepository = featureUsageStatisticsRepository;
    }

    /**
     * Builds the digest for the last {@link #DIGEST_WINDOW_IN_DAYS} days.
     *
     * @return the digest, with the busiest modules first
     */
    public FeatureUsageDigestDTO buildWeeklyDigest() {
        FeatureUsageOverviewDTO overview = featureUsageQueryService.getOverview(DIGEST_WINDOW_IN_DAYS, null);
        LocalDate to = LocalDate.now(ZoneOffset.UTC);
        LocalDate from = overview.from();
        // The equally long window immediately before this one, so the comparison is like for like - which means the same
        // set of features as well as the same number of days. Both windows exclude what this version no longer offers,
        // otherwise an endpoint removed in between would still count towards the earlier window and read as a drop.
        Instant retiredBefore = FeatureUsageQueryService.retirementCutoff(overview.inventoryRefreshedAt());
        Map<String, Long> previousCallsByModule = featureUsageStatisticsRepository.findModuleCallsBetween(from.minusDays(DIGEST_WINDOW_IN_DAYS), from.minusDays(1), retiredBefore)
                .stream().collect(Collectors.toMap(FeatureUsageModuleCallsDTO::module, FeatureUsageModuleCallsDTO::callCount));

        // Group before counting anything. The overview counts inventory rows, which are endpoints, while the page
        // collapses endpoints that share a @FeatureUsage label into one feature row. Reporting the row counts next to
        // the page made the digest contradict it, in the same way the page itself used to: "895 unused" over a list of
        // 131 features. Every feature count below is therefore derived from the grouped view.
        List<LogicalFeature> logicalFeatures = groupIntoLogicalFeatures(overview.features());
        List<FeatureUsageModuleSummaryDTO> summaries = summarizePerModule(logicalFeatures, previousCallsByModule);
        List<FeatureUsageModuleSummaryDTO> activeModules = summaries.stream().filter(summary -> summary.callCount() > 0)
                .sorted(Comparator.comparingLong(FeatureUsageModuleSummaryDTO::callCount).reversed()).toList();
        // Only the names: what matters about a module nobody touched is that it is on the list, not its row of zeros.
        List<String> quietModules = summaries.stream().filter(summary -> summary.callCount() == 0).map(FeatureUsageModuleSummaryDTO::module).sorted().toList();

        // Taken from the same set as the module rows rather than from overview.totalCalls(), which counts every inventory
        // row including the retired ones. The email states that retired entries are excluded from these counts, and the
        // headline disagreeing with the sum of the rows below it is exactly the kind of contradiction that makes a summary
        // worth less than no summary.
        long totalCalls = logicalFeatures.stream().filter(feature -> !feature.retired()).mapToLong(LogicalFeature::callCount).sum();
        long usedFeatures = logicalFeatures.stream().filter(feature -> !feature.retired() && feature.callCount() > 0).count();
        long stillOffered = logicalFeatures.stream().filter(feature -> !feature.retired()).count();
        // Derived here rather than taken from the overview, which counts endpoints
        long unusedFeatures = logicalFeatures.stream().filter(feature -> !feature.retired() && feature.callCount() == 0).count();
        long retiredFeatures = logicalFeatures.stream().filter(LogicalFeature::retired).count();

        return new FeatureUsageDigestDTO(DIGEST_WINDOW_IN_DAYS, from, to, totalCalls, previousCallsByModule.values().stream().mapToLong(Long::longValue).sum(), stillOffered,
                usedFeatures, unusedFeatures, retiredFeatures, overview.recordingSince(), activeModules, quietModules);
    }

    /**
     * Rolls the per-feature report up per module, excluding retired features from every count: a module is not half unused
     * because endpoints it stopped offering are no longer called.
     */
    private static List<FeatureUsageModuleSummaryDTO> summarizePerModule(List<LogicalFeature> features, Map<String, Long> previousCallsByModule) {
        Map<String, List<LogicalFeature>> byModule = features.stream().filter(feature -> !feature.retired())
                .collect(Collectors.groupingBy(LogicalFeature::module, LinkedHashMap::new, Collectors.toCollection(ArrayList::new)));

        return byModule.entrySet().stream().map(entry -> {
            List<LogicalFeature> moduleFeatures = entry.getValue();
            long callCount = moduleFeatures.stream().mapToLong(LogicalFeature::callCount).sum();
            long errorCount = moduleFeatures.stream().mapToLong(LogicalFeature::errorCount).sum();
            long used = moduleFeatures.stream().filter(feature -> feature.callCount() > 0).count();
            return new FeatureUsageModuleSummaryDTO(entry.getKey(), callCount, previousCallsByModule.getOrDefault(entry.getKey(), 0L), errorCount, used, moduleFeatures.size(),
                    moduleFeatures.size() - used);
        }).toList();
    }

    /**
     * Collapses the inventory rows that share a {@code @FeatureUsage} label into one logical feature, which is what the
     * admin page lists and therefore what the digest has to count.
     * <p>
     * The key and the merge deliberately mirror the page: rows are grouped by module and label, falling back to the
     * identifier when a row carries no label, and a feature counts as retired only once every endpoint behind it is
     * gone - while one remains, the feature still exists. Counters are summed over the endpoints this version still
     * offers, so a label that covers both live and removed endpoints reports only what is still reachable. Diverging
     * from the page here would reintroduce the contradiction from the other side.
     *
     * @param entries the inventory rows from the overview
     * @return one entry per logical feature
     */
    private static List<LogicalFeature> groupIntoLogicalFeatures(List<FeatureUsageEntryDTO> entries) {
        Map<String, LogicalFeature> byKey = new LinkedHashMap<>();
        for (FeatureUsageEntryDTO entry : entries) {
            String label = StringUtils.hasText(entry.featureLabel()) ? entry.featureLabel() : entry.identifier();
            // A retired endpoint contributes to whether the feature still exists, but not to its counters. Those two are
            // different questions for a label that covers both live and removed endpoints: the label is still offered
            // while one endpoint remains, yet the removed endpoint's calls must not be counted, because the previous
            // window is queried with the retired rows filtered out individually. Counting them on one side only produced
            // a week-over-week change that never happened, and contradicted the email's claim that retired entries are
            // excluded.
            long callCount = entry.retired() ? 0 : entry.callCount();
            long errorCount = entry.retired() ? 0 : entry.errorCount();
            byKey.merge(entry.module() + "/" + label, new LogicalFeature(entry.module(), callCount, errorCount, entry.retired()),
                    (existing, added) -> new LogicalFeature(existing.module(), existing.callCount() + added.callCount(), existing.errorCount() + added.errorCount(),
                            existing.retired() && added.retired()));
        }
        return List.copyOf(byKey.values());
    }

    /**
     * One feature as the admin page presents it: either several endpoints sharing a label, or a single unlabelled
     * endpoint.
     *
     * @param module     the module the feature belongs to
     * @param callCount  the calls across every endpoint behind it
     * @param errorCount the failed calls across every endpoint behind it
     * @param retired    whether this version no longer offers any of those endpoints
     */
    private record LogicalFeature(String module, long callCount, long errorCount, boolean retired) {
    }
}
