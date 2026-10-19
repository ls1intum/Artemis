package de.tum.cit.aet.artemis.iris.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisDashboardOverviewDTO(long totalSessions, long activeSessions, double engagementRate, long totalMessages, long uniqueUsers, double noResponseRate,
        long noResponseMessageCount, long noResponseSessionCount, double thumbsUpRatio, double thumbsDownRatio, double thumbsUpAbsoluteRate, double thumbsDownAbsoluteRate,
        long sessionsWithThumbsUp, long sessionsWithThumbsDown, long thumbsUpCount, long thumbsDownCount, double avgResponseTimeSeconds, double p50ResponseTimeSeconds,
        double p95ResponseTimeSeconds, double totalTokenCostEur) {
}
