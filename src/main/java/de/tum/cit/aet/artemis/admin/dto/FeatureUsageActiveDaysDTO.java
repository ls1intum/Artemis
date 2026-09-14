package de.tum.cit.aet.artemis.admin.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The number of distinct days a logical feature was used on.
 * <p>
 * A labelled feature is served by several endpoints, and the per-endpoint counts cannot be combined client-side: summing
 * them double counts a day two endpoints were both used on, and taking the largest undercounts when they were used on
 * different days. Only the database can answer it exactly, so the distinct count is grouped here by the same key the
 * table groups its rows by.
 *
 * @param module     the module the feature belongs to
 * @param featureKey the feature label when it has one, otherwise the endpoint identifier. Together with the module this
 *                       is the key the overview table groups by, so a row can look its exact count up directly.
 * @param activeDays days on which any endpoint behind this feature was used at least once
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record FeatureUsageActiveDaysDTO(String module, String featureKey, long activeDays) {
}
