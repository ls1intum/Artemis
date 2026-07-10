package de.tum.cit.aet.artemis.core.config.performance;

import java.time.Instant;
import java.util.List;

/**
 * Data-transfer object returned by the {@code GET /api/core/admin/performance/slow-queries}
 * endpoint. Consumed by the CI analysis script to produce a PR performance report.
 *
 * @param generatedAt    Timestamp at which the report was assembled.
 * @param thresholdMs    The configured slow-query threshold in milliseconds.
 * @param n1Threshold    The configured N+1 detection threshold (occurrence count).
 * @param slowQueryCount Total number of captured slow queries.
 * @param n1SuspectCount Total number of detected N+1 query patterns.
 * @param slowQueries    List of individual queries that exceeded {@code thresholdMs}.
 * @param n1Suspects     List of query templates that were repeated more than {@code n1Threshold}
 *                           times within a single HTTP request.
 */
public record SlowQueryReportDTO(Instant generatedAt, long thresholdMs, int n1Threshold, int slowQueryCount, int n1SuspectCount, List<SlowQueryRecord> slowQueries,
        List<N1Suspect> n1Suspects) {
}
