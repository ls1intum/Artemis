package de.tum.cit.aet.artemis.admin.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.security.Role;

/**
 * The whole feature usage report for one window.
 * <p>
 * All headline counts are per user-facing feature, the unit the page leads with. The endpoints behind them are reported
 * as well, for the drill-down and for the technical view, but never counted in the headline.
 *
 * @param days                 the length of the window in days
 * @param from                 the first day included
 * @param callerRole           the role the report was filtered to, absent when it covers every caller
 * @param availableFeatures    catalogue features this deployment offers
 * @param usedFeatures         of those, the ones with at least one action or view
 * @param onlyAutomatic        of those, the ones that received automatic or system calls only
 * @param unusedFeatures       of those, the ones without any call, the number a decision starts from
 * @param notAvailable         catalogue features this deployment does not offer, usually because their module is disabled
 * @param noActions            available features that act, were viewed and never acted on
 * @param retiredEndpoints     inventory entries this version no longer offers at all
 * @param actionCount          actions across all features in the window
 * @param viewCount            views across all features in the window
 * @param automaticCount       automatic calls across all features in the window
 * @param systemCount          calls by other systems across all features in the window
 * @param inventoryRefreshedAt the most recent time any node reported its endpoints; an entry whose
 *                                 {@code lastRegisteredAt} is clearly older than this no longer exists
 * @param recordingSince       when this deployment started recording. Without it the report would imply more evidence
 *                                 than it has: "unused over 180 days" reads very differently on an instance that has only
 *                                 been recording for a week.
 * @param features             one entry per catalogue feature, in catalogue order, including the unused and unavailable ones
 * @param endpoints            one entry per inventory row, busiest first, including the retired ones
 * @param roleDistribution     actions and views per caller role over the whole window, never filtered, so it stays
 *                                 comparable
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record FeatureUsageOverviewDTO(int days, LocalDate from, @Nullable Role callerRole, long availableFeatures, long usedFeatures, long onlyAutomatic, long unusedFeatures,
        long notAvailable, long noActions, long retiredEndpoints, long actionCount, long viewCount, long automaticCount, long systemCount, Instant inventoryRefreshedAt,
        @Nullable Instant recordingSince, List<UserFeatureUsageDTO> features, List<FeatureUsageEntryDTO> endpoints, List<FeatureUsageRoleShareDTO> roleDistribution) {
}
