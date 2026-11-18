package de.tum.cit.aet.artemis.admin.dto;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.FeatureInteraction;

/**
 * One day of calls of one interaction, for the trend chart.
 * <p>
 * Days with no calls are absent rather than zero. The client fills the gaps, so the payload stays proportional to actual
 * usage instead of to the length of the window.
 *
 * @param usageDay    the UTC day
 * @param interaction how the calls count
 * @param callCount   calls of that interaction on that day
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record FeatureUsageTrendPointDTO(LocalDate usageDay, FeatureInteraction interaction, long callCount) {
}
