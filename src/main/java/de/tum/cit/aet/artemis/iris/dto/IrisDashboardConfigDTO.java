package de.tum.cit.aet.artemis.iris.dto;

import java.util.List;

public record IrisDashboardConfigDTO(int maxQueryWindowDays, int staleThresholdMinutes, Digest digest, Alert alert) {

    public record Digest(boolean enabled, String cron, List<String> recipients) {
    }

    public record Alert(boolean enabled, double noResponseRateThreshold, int checkIntervalMinutes, int cooldownMinutes, int lookbackMinutes, int minimumActiveSessions,
            int minimumUserMessages, List<String> recipients) {
    }
}
