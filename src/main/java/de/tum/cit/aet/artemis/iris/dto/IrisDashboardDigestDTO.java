package de.tum.cit.aet.artemis.iris.dto;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisDashboardDigestDTO(Instant windowStart, Instant windowEnd, Instant staleBefore, long totalSessions, long activeSessions, double engagementRate,
        long totalMessages, long uniqueUsers, double noResponseRate, long noResponseMessageCount, long noResponseSessionCount, double thumbsUpRatio, double thumbsDownRatio,
        double thumbsUpAbsoluteRate, double thumbsDownAbsoluteRate, long thumbsUpCount, long thumbsDownCount, long sessionsWithThumbsUp, long sessionsWithThumbsDown,
        double avgResponseTimeSeconds, double p50ResponseTimeSeconds, double p95ResponseTimeSeconds, double totalTokenCostEur,
        List<IrisDashboardDigestChatModeDTO> chatModeBreakdown, List<IrisDashboardDigestCourseDTO> topCourses, String dashboardPath) {
}
