package de.tum.cit.aet.artemis.admin.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

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
        // the equally long window immediately before this one, so the comparison is like for like
        Map<String, Long> previousCallsByModule = featureUsageStatisticsRepository.findModuleCallsBetween(from.minusDays(DIGEST_WINDOW_IN_DAYS), from.minusDays(1)).stream()
                .collect(Collectors.toMap(FeatureUsageModuleCallsDTO::module, FeatureUsageModuleCallsDTO::callCount));

        List<FeatureUsageModuleSummaryDTO> summaries = summarizePerModule(overview.features(), previousCallsByModule);
        List<FeatureUsageModuleSummaryDTO> activeModules = summaries.stream().filter(summary -> summary.callCount() > 0)
                .sorted(Comparator.comparingLong(FeatureUsageModuleSummaryDTO::callCount).reversed()).toList();
        // Only the names: what matters about a module nobody touched is that it is on the list, not its row of zeros.
        List<String> quietModules = summaries.stream().filter(summary -> summary.callCount() == 0).map(FeatureUsageModuleSummaryDTO::module).sorted().toList();

        long usedFeatures = overview.features().stream().filter(feature -> !feature.retired() && feature.callCount() > 0).count();
        long stillOffered = overview.features().stream().filter(feature -> !feature.retired()).count();

        return new FeatureUsageDigestDTO(DIGEST_WINDOW_IN_DAYS, from, to, overview.totalCalls(), previousCallsByModule.values().stream().mapToLong(Long::longValue).sum(),
                stillOffered, usedFeatures, overview.unusedFeatures(), overview.retiredFeatures(), overview.recordingSince(), activeModules, quietModules);
    }

    /**
     * Rolls the per-feature report up per module, excluding retired features from every count: a module is not half unused
     * because endpoints it stopped offering are no longer called.
     */
    private static List<FeatureUsageModuleSummaryDTO> summarizePerModule(List<FeatureUsageEntryDTO> features, Map<String, Long> previousCallsByModule) {
        Map<String, List<FeatureUsageEntryDTO>> byModule = features.stream().filter(feature -> !feature.retired())
                .collect(Collectors.groupingBy(FeatureUsageEntryDTO::module, LinkedHashMap::new, Collectors.toCollection(ArrayList::new)));

        return byModule.entrySet().stream().map(entry -> {
            List<FeatureUsageEntryDTO> moduleFeatures = entry.getValue();
            long callCount = moduleFeatures.stream().mapToLong(FeatureUsageEntryDTO::callCount).sum();
            long errorCount = moduleFeatures.stream().mapToLong(FeatureUsageEntryDTO::errorCount).sum();
            long used = moduleFeatures.stream().filter(feature -> feature.callCount() > 0).count();
            return new FeatureUsageModuleSummaryDTO(entry.getKey(), callCount, previousCallsByModule.getOrDefault(entry.getKey(), 0L), errorCount, used, moduleFeatures.size(),
                    moduleFeatures.size() - used);
        }).toList();
    }
}
