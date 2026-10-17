package de.tum.cit.aet.artemis.core.config.performance;

import java.time.Instant;
import java.util.List;

/**
 * One HTTP request's aggregate query-time profile, recorded for every request regardless of
 * whether any individual query crossed the slow-query threshold. Where {@link SlowQueryRecord}
 * and {@link N1Suspect} answer "was any single statement slow" or "was one query shape repeated
 * too often", this answers a question neither of them can: how much of this endpoint's total
 * latency is database time, and across how many distinct queries -- catching an endpoint that
 * fires many individually-unremarkable queries which together dominate its response time, a
 * pattern invisible to any single-query or single-pattern threshold.
 * <p>
 * Deliberately captured for every request with no threshold of its own (unlike the two record
 * types above): a query-count or duration threshold picked without first seeing the real
 * distribution of values across genuine E2E traffic would be a guess, not a calibrated bar. This
 * list exists to be the evidence a threshold gets calibrated from, not to enforce one yet.
 *
 * @param httpMethod      HTTP verb of the request; may be {@code null} for requests without a
 *                            servlet context.
 * @param httpEndpoint    URI path of the request; may be {@code null}.
 * @param testName        Playwright test name from the {@code X-Playwright-Test-Name} header;
 *                            may be {@code null} when not running under Playwright.
 * @param phase           Value of the {@code X-Playwright-Phase} request header; may be
 *                            {@code null} when not running under Playwright. Unlike
 *                            {@link SlowQueryRecord}, every entry here corresponds to a real HTTP
 *                            request captured in {@link SlowQueryRequestFilter} -- there is no
 *                            "background" case, since a background/async query never passes
 *                            through the servlet filter that produces this record in the first
 *                            place.
 * @param totalDurationMs Wall-clock time for the whole request, start to finish.
 * @param dbTimeMs        Sum of every JDBC statement's execution time recorded during the
 *                            request -- the sum of {@code queries[*].totalDurationMs()}.
 * @param queryCount      Number of JDBC statements executed during the request -- the sum of
 *                            {@code queries[*].count()}.
 * @param queries         Per-normalized-template breakdown of every query executed during the
 *                            request (see {@link QueryCountEntry}).
 * @param capturedAt      Instant at which the request completed.
 */
public record EndpointTimingRecord(String httpMethod, String httpEndpoint, String testName, String phase, long totalDurationMs, long dbTimeMs, int queryCount,
        List<QueryCountEntry> queries, Instant capturedAt) {
}
