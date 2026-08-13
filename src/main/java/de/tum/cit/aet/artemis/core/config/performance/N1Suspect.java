package de.tum.cit.aet.artemis.core.config.performance;

/**
 * Represents a query template that was executed more than {@link SlowQueryProperties#getN1DetectionThreshold()}
 * times within a single HTTP request — a classic indicator of an N+1 query problem.
 *
 * @param normalizedSql The normalised SQL template (literals replaced by {@code ?}).
 * @param occurrences   How many times this template was executed within the flagged request.
 * @param httpMethod    HTTP verb of the triggering request; {@code null} for async contexts.
 * @param httpEndpoint  URI path of the triggering request; {@code null} for async contexts.
 * @param testName      Playwright test name that triggered this pattern; {@code null} when not
 *                          running under Playwright.
 * @param phase         Value of the {@code X-Playwright-Phase} request header — {@code "action"} for
 *                          requests the browser page itself issued (real UI interaction), {@code "setup"}
 *                          for {@code page.request}/{@code context.request} traffic (test fixtures); {@code null}
 *                          when not running under Playwright.
 */
public record N1Suspect(String normalizedSql, int occurrences, String httpMethod, String httpEndpoint, String testName, String phase) {
}
