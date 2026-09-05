package de.tum.cit.aet.artemis.admin.dto;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * One day of one feature's usage, for the trend chart.
 * <p>
 * Days with no usage are absent rather than zero. The client fills the gaps, so the payload stays proportional to actual
 * usage instead of to the length of the window.
 *
 * @param usageDay  the UTC day
 * @param callCount calls on that day
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record FeatureUsageTrendPointDTO(LocalDate usageDay, long callCount) {
}
