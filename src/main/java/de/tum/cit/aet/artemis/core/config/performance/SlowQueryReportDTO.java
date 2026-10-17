package de.tum.cit.aet.artemis.core.config.performance;

import java.time.Instant;
import java.util.List;

/**
 * Data-transfer object returned by the {@code GET /api/admin/performance/slow-queries}
 * endpoint. Consumed by the CI analysis script to produce a PR performance report.
 *
 * @param generatedAt        Timestamp at which the report was assembled.
 * @param thresholdMs        The configured slow-query threshold in milliseconds.
 * @param slowQueryCount     Total number of captured slow queries.
 * @param repeatedQueryCount Total number of repeated-query findings.
 * @param slowQueries        List of individual queries that exceeded {@code thresholdMs}.
 * @param repeatedQueries    Query templates that ran more than once within a single HTTP request,
 *                               as N+1 or duplicate (see {@link RepeatedQueryFinding}).
 * @param endpointTimings    One entry per HTTP request captured during the run, unfiltered (see
 *                               {@link EndpointTimingRecord}).
 */
public record SlowQueryReportDTO(Instant generatedAt, long thresholdMs, int slowQueryCount, int repeatedQueryCount, List<SlowQueryRecord> slowQueries,
        List<RepeatedQueryFinding> repeatedQueries, List<EndpointTimingRecord> endpointTimings) {
}
