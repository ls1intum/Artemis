package de.tum.cit.aet.artemis.iris.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisDashboardConfigDTO(int maxQueryWindowDays, int staleThresholdMinutes, DigestDTO digest, AlertDTO alert) {

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record DigestDTO(boolean enabled, String cron, List<String> recipients) {
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record AlertDTO(boolean enabled, double noResponseRateThreshold, int checkIntervalMinutes, int cooldownMinutes, int lookbackMinutes, int minimumActiveSessions,
            int minimumUserMessages, List<String> recipients) {
    }
}
