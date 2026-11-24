package de.tum.cit.aet.artemis.core.config.performance;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Data-transfer object returned by the {@code GET /api/admin/performance/slow-queries}
 * endpoint. Consumed by the CI analysis script to produce a PR performance report.
 *
 * @param generatedAt        Timestamp at which the report was assembled.
 * @param repeatedQueryCount Total number of repeated-query findings.
 * @param repeatedQueries    Query templates that ran more than once within a single HTTP request,
 *                               as N+1 or duplicate (see {@link RepeatedQueryFinding}).
 * @param indexes            Indexes of every table, by table name (see {@link IndexCatalog}); empty if
 *                               they could not be read.
 * @param truncated          True if repeated-query findings were dropped because the list reached its size limit, so the
 *                               report does not show every finding of the run; a comparison against it is then incomplete.
 */
public record SlowQueryReportDTO(Instant generatedAt, int repeatedQueryCount, List<RepeatedQueryFinding> repeatedQueries, Map<String, List<TableIndex>> indexes,
        boolean truncated) {
}
