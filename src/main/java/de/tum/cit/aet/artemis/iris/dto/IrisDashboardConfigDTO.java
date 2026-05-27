package de.tum.cit.aet.artemis.iris.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisDashboardConfigDTO(int maxQueryWindowDays, int staleThresholdMinutes, boolean digestEnabled, String digestCron, boolean alertEnabled,
        double alertNoResponseRateThreshold, int alertCheckIntervalMinutes, int alertCooldownMinutes, int alertLookbackMinutes, int alertMinimumEligibleSessions,
        int alertMinimumUserMessages) {
}
