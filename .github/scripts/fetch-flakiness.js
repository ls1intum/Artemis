const fs = require('fs');
const https = require('https');

/**
 * Parse JUnit XML to extract unique failed test cases.
 * @param {string} resultsFile - Path to the JUnit XML results file
 * @returns {Array<{testName: string, className: string, testSuiteName: string}>}
 */
function parseFailedTests(resultsFile) {
    const failedTests = [];
    const seen = new Set();
    try {
        if (!fs.existsSync(resultsFile)) return failedTests;
        const xml = fs.readFileSync(resultsFile, 'utf8');
        const blocks = xml.split('</testcase>');
        let lastSuiteName = '';
        for (const block of blocks) {
            // Track the last <testsuite name="..."> seen so far (carries across blocks).
            // The first testcase in a suite sees the opening tag; later ones inherit it.
            for (const m of block.matchAll(/<testsuite\s[^>]*>/g)) {
                const nm = m[0].match(/\bname="([^"]*)"/);
                if (nm) lastSuiteName = nm[1];
            }
            if (!block.includes('<failure') && !block.includes('<error')) continue;
            const testcaseMatch = block.match(/<testcase\s[^>]*>/);
            if (!testcaseMatch) continue;
            const tag = testcaseMatch[0];
            const nameMatch = tag.match(/\bname="([^"]*)"/);
            const classMatch = tag.match(/\bclassname="([^"]*)"/);
            if (nameMatch && classMatch) {
                const key = `${classMatch[1]}#${nameMatch[1]}`;
                if (!seen.has(key)) {
                    seen.add(key);
                    // Helios requires testSuiteName. In Playwright's JUnit output the parent
                    // <testsuite name> matches classname (both are the spec file path), so
                    // lastSuiteName and classMatch[1] are equivalent — but we prefer the
                    // explicit testsuite name in case classname ever diverges.
                    const testSuiteName = lastSuiteName || classMatch[1];
                    failedTests.push({ testName: nameMatch[1], className: classMatch[1], testSuiteName });
                }
            }
        }
    } catch (e) {
        console.log('Error parsing JUnit XML:', e.message);
    }
    return failedTests;
}

/**
 * Fetch flakiness scores from the Helios API.
 * @param {Array<{testName: string, className: string, testSuiteName: string}>} failedTests
 * @param {string} heliosSecret - The Helios repo secret
 * @returns {Promise<Array>}
 */
async function fetchFlakinessScores(failedTests, heliosSecret) {
    if (!failedTests.length) return [];
    const unavailable = (reason) => {
        console.warn(`::warning::Helios flakiness history unavailable (${reason}). Continuing with the Playwright result; failures without history remain blocking.`);
        return [];
    };
    if (!heliosSecret) return unavailable('no repository secret configured');
    try {
        const payload = JSON.stringify({ testCases: failedTests });
        return await new Promise((resolve) => {
            let settled = false;
            let deadline;
            const finish = (results) => {
                if (settled) return;
                settled = true;
                clearTimeout(deadline);
                resolve(results);
            };
            const fail = (reason) => {
                if (!settled) finish(unavailable(reason));
            };
            const req = https.request(
                'https://helios.aet.cit.tum.de/api/tests/flakiness-scores',
                {
                    method: 'POST',
                    headers: {
                        Authorization: `Secret ${heliosSecret}`,
                        'Content-Type': 'application/json',
                        'Content-Length': Buffer.byteLength(payload),
                    },
                },
                (res) => {
                    res.on('error', () => fail('response interrupted'));
                    res.on('aborted', () => fail('response interrupted'));
                    if (res.statusCode !== 200) {
                        fail(`HTTP ${res.statusCode}`);
                        res.destroy();
                        return;
                    }
                    let data = '';
                    res.on('data', (chunk) => (data += chunk));
                    res.on('end', () => {
                        try {
                            const results = JSON.parse(data);
                            if (!Array.isArray(results)) {
                                fail('invalid response');
                                return;
                            }
                            const valid = results.filter(
                                (r) =>
                                    r &&
                                    typeof r.className === 'string' &&
                                    typeof r.testName === 'string' &&
                                    Number.isFinite(r.flakinessScore) &&
                                    r.flakinessScore >= 0 &&
                                    r.flakinessScore <= 100 &&
                                    Number.isFinite(r.defaultBranchFailureRate) &&
                                    r.defaultBranchFailureRate >= 0 &&
                                    r.defaultBranchFailureRate <= 1 &&
                                    Number.isFinite(r.combinedFailureRate) &&
                                    r.combinedFailureRate >= 0 &&
                                    r.combinedFailureRate <= 1,
                            );
                            if (valid.length !== results.length) {
                                console.warn('::warning::Ignoring invalid Helios history entries. Failures without valid history remain blocking.');
                            }
                            finish(valid);
                        } catch {
                            fail('invalid JSON response');
                        }
                    });
                },
            );
            req.on('error', () => fail('request failed'));
            // Bound the complete request, including a response that stalls or never finishes.
            deadline = setTimeout(() => {
                fail('request timed out');
                req.destroy();
            }, 10000);
            try {
                req.write(payload);
                req.end();
            } catch {
                fail('request failed');
                req.destroy();
            }
        });
    } catch {
        return unavailable('request failed');
    }
}

/**
 * Build a markdown flakiness table from Helios API results.
 * @param {Array} flakinessResults
 * @returns {string} Markdown table or empty string
 */
function buildFlakinessTable(flakinessResults) {
    if (!Array.isArray(flakinessResults) || !flakinessResults.length) return '';
    const escapeMd = (s = '') => String(s).replaceAll('|', '\\|').replaceAll('`', '\\`');
    let table = '\n#### Flakiness Scores for Failed Tests\n\n';
    table += '| Test | Flakiness Score | Default Branch Failure Rate | Combined Failure Rate |\n';
    table += '|------|:---:|:---:|:---:|\n';
    for (const r of flakinessResults) {
        const dfr = (r.defaultBranchFailureRate * 100).toFixed(1);
        const cfr = (r.combinedFailureRate * 100).toFixed(1);
        const testId = `${escapeMd(r.className)}#${escapeMd(r.testName)}`;
        table += `| \`${testId}\` | **${r.flakinessScore}%** | ${dfr}% | ${cfr}% |\n`;
    }
    return table;
}

/**
 * Split failed tests into real regressions vs known-flaky, using Helios history.
 *
 * A failure is exonerated (treated as known-flaky, not a regression introduced by this change) when
 * EITHER signal from Helios says the test is unreliable independent of the change:
 *   - flakinessScore > scoreThreshold (Helios' own LOW_FLAKINESS_THRESHOLD, 30): it flakes; or
 *   - defaultBranchFailureRate >= baseFailureRate (Helios' own MIN_FLAKY_RATE, 0.01 = 1%): it also
 *     fails on the base branch, so the failure is not attributable to this change — the base-branch
 *     signal Datadog and Chromium's commit-queue use to distinguish a real regression from noise.
 * A failure with reliable history is classified as real. Missing history (including a Helios
 * outage or rejected credentials) is unknown: use the ordinary Playwright failure verdict without
 * claiming that history proved a regression. Unknown failures remain blocking.
 *
 * @param {Array<{testName: string, className: string}>} failedTests
 * @param {Array} flakinessResults - Helios response (empty if unavailable)
 * @param {number} scoreThreshold - flakinessScore above which a test counts as known-flaky
 * @param {number} baseFailureRate - default-branch failure rate at/above which a failure is not a regression
 * @returns {{real: Array, flaky: Array, unknown: Array}}
 */
function classifyFailures(failedTests, flakinessResults, scoreThreshold, baseFailureRate) {
    const byKey = new Map();
    for (const r of flakinessResults) {
        byKey.set(`${r.className}#${r.testName}`, r);
    }
    const real = [];
    const flaky = [];
    const unknown = [];
    for (const test of failedTests) {
        const stats = byKey.get(`${test.className}#${test.testName}`);
        if (stats === undefined) {
            unknown.push(test);
            continue;
        }
        const knownFlaky = stats.flakinessScore > scoreThreshold || stats.defaultBranchFailureRate >= baseFailureRate;
        (knownFlaky ? flaky : real).push(test);
    }
    return { real, flaky, unknown };
}

module.exports = { parseFailedTests, fetchFlakinessScores, buildFlakinessTable, classifyFailures };
