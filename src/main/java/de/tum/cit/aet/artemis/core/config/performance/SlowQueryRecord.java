package de.tum.cit.aet.artemis.core.config.performance;

import java.time.Instant;

/**
 * Immutable record representing one captured database query that exceeded the slow-query threshold.
 *
 * @param sql             Normalised SQL text (literals stripped, replaced by {@code ?}).
 * @param executionTimeMs Actual wall-clock execution time in milliseconds.
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
 * @param capturedAt      Instant at which the query was recorded.
 */
public record SlowQueryRecord(String sql, long executionTimeMs, String httpMethod, String httpEndpoint, String testName, String phase, Instant capturedAt) {
}
