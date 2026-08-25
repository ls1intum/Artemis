package de.tum.cit.aet.artemis.core.config.performance;

import java.time.Instant;

/**
 * Immutable record representing one captured database query that exceeded the slow-query threshold.
 *
 * @param sql             Normalised SQL text (literals stripped, replaced by {@code ?}).
 * @param executionTimeMs Actual wall-clock execution time in milliseconds.
 * @param joinCount       Number of SQL {@code join} keywords in the raw query text -- a structural
 *                            signal of how many tables this one statement touches, independent of
 *                            how fast or slow the environment it ran in happens to be.
 * @param httpMethod      HTTP verb of the triggering request (e.g. {@code GET}); {@code null} for
 *                            background/async queries.
 * @param httpEndpoint    URI path of the triggering request (e.g. {@code /api/courses/1/exercises});
 *                            {@code null} for background/async queries.
 * @param testName        Value of the {@code X-Playwright-Test-Name} request header injected by the
 *                            Playwright fixture; {@code null} when not running under Playwright.
 * @param phase           Value of the {@code X-Playwright-Phase} request header — {@code "action"} for
 *                            requests the browser page itself issued (real UI interaction), {@code "setup"}
 *                            for {@code page.request}/{@code context.request} traffic (test fixtures); {@code null}
 *                            when not running under Playwright.
 * @param threadName      Name of the thread that executed the query. Always captured (cheap), but only
 *                            meaningful for background/async queries ({@code httpEndpoint == null}): Spring's
 *                            {@code @Scheduled}/{@code @Async} executors are typically named after their
 *                            bean (e.g. {@code quizStatisticsTaskExecutor-1}), which is often enough to
 *                            identify which subsystem triggered the query without any request context at all.
 * @param capturedAt      Instant at which the query was recorded.
 */
public record SlowQueryRecord(String sql, long executionTimeMs, int joinCount, String httpMethod, String httpEndpoint, String testName, String phase, String threadName,
        Instant capturedAt) {
}
