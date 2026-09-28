package de.tum.cit.aet.artemis.admin.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The number of distinct days one feature was used on.
 * <p>
 * A feature is served by several endpoints, and the per-endpoint counts cannot be combined afterwards: summing them double
 * counts a day two endpoints were both used on, and taking the largest undercounts when they were used on different days.
 * Only the database can answer it exactly, so the distinct count is grouped by feature label.
 *
 * @param featureLabel the feature label, the name of a {@link de.tum.cit.aet.artemis.core.service.featureusage.UserFeature}
 * @param activeDays   days on which any endpoint behind this feature saw an action or a view
 * @param actionDays   days on which any endpoint behind this feature saw an action
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record FeatureUsageActiveDaysDTO(String featureLabel, long activeDays, long actionDays) {
}
