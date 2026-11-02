package de.tum.cit.aet.artemis.iris.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Read-only dashboard configuration. Numeric and boolean values are serialized even when zero or false.
 *
 * @param maxQueryWindowDays           maximum accepted query window in days
 * @param staleThresholdMinutes        age at which unanswered messages become eligible
 * @param digestEnabled                whether digest delivery is enabled
 * @param digestCron                   digest schedule, omitted when empty
 * @param alertEnabled                 whether alert delivery is enabled
 * @param alertNoResponseRateThreshold no-response percentage that triggers an alert
 * @param alertCheckIntervalMinutes    interval between alert checks
 * @param alertCooldownMinutes         minimum interval between alerts
 * @param alertLookbackMinutes         window used for alert calculations
 * @param alertMinimumEligibleSessions minimum eligible sessions required for alerts
 * @param alertMinimumUserMessages     minimum user messages required for alerts
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisDashboardConfigDTO(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) int maxQueryWindowDays,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int staleThresholdMinutes, @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean digestEnabled,
        String digestCron, @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean alertEnabled,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) double alertNoResponseRateThreshold,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int alertCheckIntervalMinutes, @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int alertCooldownMinutes,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int alertLookbackMinutes, @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int alertMinimumEligibleSessions,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int alertMinimumUserMessages) {
}
