const { parseFailedTests, fetchFlakinessScores, classifyFailures } = require('./fetch-flakiness.js');

// Helios' own thresholds (TestCaseStatisticsService), reused so exoneration matches the org's single
// flaky-test authority rather than numbers invented for CI:
//   LOW_FLAKINESS_THRESHOLD (30) - flakinessScore above which a test is flaky
//   MIN_FLAKY_RATE (0.01 = 1%)    - default-branch failure rate above which a failure is base-branch
//                                   noise, not a regression introduced by this change
const KNOWN_FLAKY_THRESHOLD = 30;
const MIN_BASE_FAILURE_RATE = 0.01;

/**
 * Classify an E2E phase's failures for the required gate. Helios is optional enrichment:
 * without history, the ordinary Playwright verdict applies.
 *
 * Playwright's JUnit reporter omits retries, so every failure in results.xml already survived all
 * retries — a genuine hard failure. The only open question per failure is "known-flaky or real",
 * which Helios' history answers when available.
 *
 * Sets `phase_result` to a POSITIVE classification, never a "couldn't find a real failure" guess:
 *   real  - at least one non-flaky test failed (a real regression)
 *   flaky - failures, and every one is known-flaky (safe to exonerate)
 *   unknown - failures without history; keep the Playwright failure verdict
 *   clean - no failure elements in results.xml
 * The caller must treat a phase whose test step failed but that reports `clean` (e.g. a global-setup
 * crash that emits no per-test failure) as unexplained, not exonerated. Also sets the per-category counts.
 */
module.exports = async (core) => {
    const resultsFile = process.env.INPUT_RESULTS_FILE;
    const heliosSecret = process.env.HELIOS_REPO_SECRET;

    const failedTests = parseFailedTests(resultsFile);
    if (failedTests.length === 0) {
        core.setOutput('phase_result', 'clean');
        core.setOutput('real_count', '0');
        core.setOutput('flaky_count', '0');
        core.setOutput('unknown_count', '0');
        return;
    }

    const flakinessResults = await fetchFlakinessScores(failedTests, heliosSecret);
    const { real, flaky, unknown } = classifyFailures(failedTests, flakinessResults, KNOWN_FLAKY_THRESHOLD, MIN_BASE_FAILURE_RATE);

    core.setOutput('phase_result', real.length > 0 ? 'real' : unknown.length > 0 ? 'unknown' : 'flaky');
    core.setOutput('real_count', String(real.length));
    core.setOutput('flaky_count', String(flaky.length));
    core.setOutput('unknown_count', String(unknown.length));
    core.info(`E2E failures: ${real.length} real, ${flaky.length} known-flaky (exonerated), ${unknown.length} without history (Playwright verdict)`);
};
