package de.tum.cit.aet.artemis.iris.dto;

public record IrisDashboardOverviewDTO(long totalSessions, long activeSessions, double engagementRate, long totalMessages, long uniqueUsers, long userMessageCount,
        long eligibleSessions, double noResponseRate, long noResponseMessageCount, long noResponseSessionCount, double thumbsUpRatio, double thumbsDownRatio,
        double thumbsUpAbsoluteRate, double thumbsDownAbsoluteRate, long sessionsWithThumbsUp, long sessionsWithThumbsDown, double averageResponseTimeSeconds,
        double p50ResponseTimeSeconds, double p95ResponseTimeSeconds, double totalTokenCostEur) {
}
