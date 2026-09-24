package de.tum.cit.aet.artemis.admin.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.admin.dto.FeatureUsageAreaSummaryDTO;
import de.tum.cit.aet.artemis.admin.dto.FeatureUsageDigestDTO;
import de.tum.cit.aet.artemis.admin.dto.FeatureUsageLabelCallsDTO;
import de.tum.cit.aet.artemis.admin.dto.FeatureUsageOverviewDTO;
import de.tum.cit.aet.artemis.admin.dto.FeatureUsageStatus;
import de.tum.cit.aet.artemis.admin.dto.UserFeatureUsageDTO;
import de.tum.cit.aet.artemis.admin.repository.FeatureUsageStatisticsRepository;
import de.tum.cit.aet.artemis.core.domain.FeatureInteraction;
import de.tum.cit.aet.artemis.core.service.featureusage.ProductArea;
import de.tum.cit.aet.artemis.core.service.featureusage.UserFeature;

/**
 * Aggregates the weekly feature usage digest.
 * <p>
 * Built on the same report the admin page shows, so the email and the page can never disagree, and rolled up per product
 * area because a per-feature list is unreadable in an email and would defeat the point: the digest is there to make
 * someone open the page, not to replace it. Use means actions and views, as on the page.
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
     * @return the digest, with the busiest areas first
     */
    public FeatureUsageDigestDTO buildWeeklyDigest() {
        FeatureUsageOverviewDTO overview = featureUsageQueryService.getOverview(DIGEST_WINDOW_IN_DAYS, null);
        LocalDate to = LocalDate.now(ZoneOffset.UTC);
        LocalDate from = overview.from();
        // The equally long window immediately before this one, so the comparison is like for like. Both windows count the
        // same kind of calls, actions and views, against the same catalogue.
        Map<ProductArea, Long> previousUseByArea = previousUseByArea(
                featureUsageStatisticsRepository.findFeatureCallsBetween(from.minusDays(DIGEST_WINDOW_IN_DAYS), from.minusDays(1)));

        List<FeatureUsageAreaSummaryDTO> summaries = Arrays.stream(ProductArea.values()).map(area -> summarize(area, overview.features(), previousUseByArea))
                .filter(summary -> summary.availableFeatures() > 0).toList();
        List<FeatureUsageAreaSummaryDTO> activeAreas = summaries.stream().filter(summary -> summary.useCount() > 0)
                .sorted(Comparator.comparingLong(FeatureUsageAreaSummaryDTO::useCount).reversed()).toList();
        // Only the names: what matters about an area nobody used is that it is on the list, not its row of zeros.
        List<ProductArea> quietAreas = summaries.stream().filter(summary -> summary.useCount() == 0).map(FeatureUsageAreaSummaryDTO::area).toList();

        long useCount = summaries.stream().mapToLong(FeatureUsageAreaSummaryDTO::useCount).sum();
        long previousUseCount = previousUseByArea.values().stream().mapToLong(Long::longValue).sum();
        return new FeatureUsageDigestDTO(DIGEST_WINDOW_IN_DAYS, from, to, useCount, previousUseCount, overview.availableFeatures(), overview.usedFeatures(),
                overview.onlyAutomatic() + overview.unusedFeatures(), overview.onlyAutomatic(), overview.retiredEndpoints(), overview.recordingSince(), activeAreas, quietAreas);
    }

    private static FeatureUsageAreaSummaryDTO summarize(ProductArea area, List<UserFeatureUsageDTO> features, Map<ProductArea, Long> previousUseByArea) {
        List<UserFeatureUsageDTO> available = features.stream().filter(feature -> feature.area() == area && feature.status() != FeatureUsageStatus.NOT_AVAILABLE).toList();
        long useCount = available.stream().mapToLong(feature -> feature.actionCount() + feature.viewCount()).sum();
        return new FeatureUsageAreaSummaryDTO(area, useCount, previousUseByArea.getOrDefault(area, 0L), available.stream().mapToLong(UserFeatureUsageDTO::actionCount).sum(),
                available.stream().mapToLong(UserFeatureUsageDTO::errorCount).sum(), available.stream().filter(feature -> feature.status() == FeatureUsageStatus.USED).count(),
                available.size(), available.stream().filter(feature -> feature.status() == FeatureUsageStatus.ONLY_AUTOMATIC).count());
    }

    /**
     * Rolls the previous window's calls up into actions and views per area. Calls of rows that belong to no catalogue
     * feature are dropped, like on the page, so the comparison covers the same set on both sides.
     */
    private static Map<ProductArea, Long> previousUseByArea(List<FeatureUsageLabelCallsDTO> calls) {
        Map<ProductArea, Long> useByArea = new EnumMap<>(ProductArea.class);
        for (FeatureUsageLabelCallsDTO call : calls) {
            UserFeature feature = FeatureUsageQueryService.resolveFeature(call.featureLabel());
            if (feature != null && (call.interaction() == FeatureInteraction.ACTION || call.interaction() == FeatureInteraction.VIEW)) {
                useByArea.merge(feature.getArea(), call.callCount(), Long::sum);
            }
        }
        return useByArea;
    }
}
