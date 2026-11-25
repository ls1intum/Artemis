package de.tum.cit.aet.artemis.core.config.performance;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * The report returned by {@code GET /api/admin/performance/slow-queries}, consumed by the CI analysis.
 *
 * @param generatedAt     when the report was assembled.
 * @param repeatedQueries query templates that ran more than once within a single HTTP request, as N+1 or duplicate.
 * @param indexes         indexes of every table, by table name (see {@link IndexCatalog}); empty if they could not be read.
 * @param truncated       whether findings were dropped at the size limit, which makes a comparison against this report incomplete.
 */
public record SlowQueryReportDTO(Instant generatedAt, List<RepeatedQueryFinding> repeatedQueries, Map<String, List<TableIndex>> indexes, boolean truncated) {
}
