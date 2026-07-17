const { parseFailedTests, fetchFlakinessScores, classifyFailures } = require('./fetch-flakiness.js');

/**
 * Classify an E2E phase's failures into a single `phase_result` string the advisory gate reads:
 * `real` | `broken` | `flaky` (see classifyFailures) or `clean` (no failures in results.xml).
 *
 * Playwright's JUnit reporter omits retries, so every failure in results.xml already survived all
 * retries — a genuine hard failure, no need to re-check. A phase whose test step failed but reports
 * `clean` (e.g. a global-setup crash with no per-test failure) must be treated by the caller as
 * unexplained, not exonerated.
 */
module.exports = async (core) => {
    const resultsFile = process.env.INPUT_RESULTS_FILE;
    const heliosSecret = process.env.HELIOS_REPO_SECRET;

    const failedTests = parseFailedTests(resultsFile);
    if (failedTests.length === 0) {
        core.setOutput('phase_result', 'clean');
        return;
    }

    const flakinessResults = await fetchFlakinessScores(failedTests, heliosSecret);
    const { real, flaky, broken } = classifyFailures(failedTests, flakinessResults);

    let phaseResult = 'flaky';
    if (real.length > 0) {
        phaseResult = 'real';
    } else if (broken.length > 0) {
        phaseResult = 'broken';
    }
    core.setOutput('phase_result', phaseResult);
    core.info(`E2E failures: ${real.length} real, ${broken.length} broken on default branch, ${flaky.length} known-flaky (exonerated)`);
};
