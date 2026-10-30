package de.tum.cit.aet.artemis.iris.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Dashboard metrics for an inclusive-start, exclusive-end UTC window. Zero-valued metrics are serialized.
 *
 * @param totalSessions          number of sessions created in the window
 * @param activeSessions         sessions with user activity
 * @param engagementRate         percentage of sessions with user activity
 * @param totalMessages          number of messages sent in the window
 * @param uniqueUsers            distinct users who sent user messages in the window
 * @param noResponseRate         percentage of eligible user messages without an answer
 * @param noResponseMessageCount eligible user messages without an answer
 * @param noResponseSessionCount sessions containing an unanswered eligible message
 * @param thumbsUpRatio          positive ratings as a percentage of non-intermediate LLM messages
 * @param thumbsDownRatio        negative ratings as a percentage of non-intermediate LLM messages
 * @param thumbsUpAbsoluteRate   sessions with positive ratings as a percentage of all sessions
 * @param thumbsDownAbsoluteRate sessions with negative ratings as a percentage of all sessions
 * @param sessionsWithThumbsUp   sessions with at least one positive rating
 * @param sessionsWithThumbsDown sessions with at least one negative rating
 * @param thumbsUpCount          number of positive ratings
 * @param thumbsDownCount        number of negative ratings
 * @param avgResponseTimeSeconds mean answer latency in seconds
 * @param p50ResponseTimeSeconds median answer latency in seconds
 * @param p95ResponseTimeSeconds 95th percentile answer latency in seconds
 * @param totalTokenCostEur      total token cost in EUR
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisDashboardOverviewDTO(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) long totalSessions,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long activeSessions, @Schema(requiredMode = Schema.RequiredMode.REQUIRED) double engagementRate,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long totalMessages, @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long uniqueUsers,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) double noResponseRate, @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long noResponseMessageCount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long noResponseSessionCount, @Schema(requiredMode = Schema.RequiredMode.REQUIRED) double thumbsUpRatio,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) double thumbsDownRatio, @Schema(requiredMode = Schema.RequiredMode.REQUIRED) double thumbsUpAbsoluteRate,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) double thumbsDownAbsoluteRate, @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long sessionsWithThumbsUp,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long sessionsWithThumbsDown, @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long thumbsUpCount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long thumbsDownCount, @Schema(requiredMode = Schema.RequiredMode.REQUIRED) double avgResponseTimeSeconds,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) double p50ResponseTimeSeconds, @Schema(requiredMode = Schema.RequiredMode.REQUIRED) double p95ResponseTimeSeconds,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) double totalTokenCostEur) {
}
