package de.tum.cit.aet.artemis.admin.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.security.Role;

/**
 * The whole feature usage report for one window.
 *
 * @param days                 the length of the window in days
 * @param from                 the first day included
 * @param callerRole           the role the report was filtered to, absent when it covers every caller
 * @param trackedFeatures      how many features exist in the inventory
 * @param unusedFeatures       how many features still offered by this version saw no use in the window, the number the
 *                                 page is really about; features that no longer exist are not counted here
 * @param retiredFeatures      how many inventory entries this version no longer offers at all
 * @param totalCalls           calls across all features in the window
 * @param inventoryRefreshedAt the most recent time any node reported its endpoints; an entry whose
 *                                 {@code lastRegisteredAt} is clearly older than this no longer exists
 * @param recordingSince       when this deployment started recording. Without it the report would imply more evidence
 *                                 than it has: "unused over 180 days" reads very differently on an instance that has only
 *                                 been recording for a week.
 * @param features             one entry per feature, including the unused ones
 * @param roleDistribution     calls per caller role over the whole window, never filtered, so it stays comparable
 * @param activeDaysPerFeature the exact distinct-day count per logical feature. The table groups endpoints into one row
 *                                 per label, and neither summing nor maxing the per-endpoint counts gives the right
 *                                 answer, so the grouped count is computed in the database and looked up by row key.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record FeatureUsageOverviewDTO(int days, LocalDate from, @Nullable Role callerRole, long trackedFeatures, long unusedFeatures, long retiredFeatures, long totalCalls,
        Instant inventoryRefreshedAt, @Nullable Instant recordingSince, List<FeatureUsageEntryDTO> features, List<FeatureUsageRoleShareDTO> roleDistribution,
        List<FeatureUsageActiveDaysDTO> activeDaysPerFeature) {
}
