package de.tum.cit.aet.artemis.iris.dto;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisDashboardAlertDTO(Instant windowStart, Instant windowEnd, double noResponseRate, double threshold, long eligibleSessions, long failedSessions,
        long userMessageCount, List<IrisDashboardAlertChatModeDTO> chatModeBreakdown, String dashboardPath) {
}
