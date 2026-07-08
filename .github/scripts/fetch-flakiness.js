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
    if (!failedTests.length || !heliosSecret) return [];
    try {
        const payload = JSON.stringify({ testCases: failedTests });
        return await new Promise((resolve) => {
            const req = https.request('https://helios.aet.cit.tum.de/api/tests/flakiness-scores', {
                method: 'POST',
                headers: {
                    'Authorization': `Secret ${heliosSecret}`,
                    'Content-Type': 'application/json',
                    'Content-Length': Buffer.byteLength(payload),
                },
                timeout: 10000,
            }, (res) => {
                let data = '';
                res.on('data', (chunk) => (data += chunk));
                res.on('end', () => {
                    if (res.statusCode !== 200) {
                        console.log(`Helios API returned status ${res.statusCode}: ${data}`);
                        return resolve([]);
                    }
                    try {
                        resolve(JSON.parse(data));
                    } catch {
                        resolve([]);
                    }
                });
            });
            req.on('error', (e) => {
                console.log('Helios API error:', e.message);
                resolve([]);
            });
            req.on('timeout', () => {
                req.destroy();
                resolve([]);
            });
            req.write(payload);
            req.end();
        });
    } catch (e) {
        console.log('Error fetching flakiness scores:', e.message);
        return [];
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
 * Everything else — tests Helios does not know, and tests reliable on the default branch — is a real
 * regression. Fail-safe: if Helios returns no data (e.g. unreachable), every failure is real, so the
 * run is never silently exonerated.
 *
 * @param {Array<{testName: string, className: string}>} failedTests
 * @param {Array} flakinessResults - Helios response (empty if unavailable)
 * @param {number} scoreThreshold - flakinessScore above which a test counts as known-flaky
 * @param {number} baseFailureRate - default-branch failure rate at/above which a failure is not a regression
 * @returns {{real: Array, flaky: Array}}
 */
function classifyFailures(failedTests, flakinessResults, scoreThreshold, baseFailureRate) {
    const byKey = new Map();
    for (const r of flakinessResults) {
        byKey.set(`${r.className}#${r.testName}`, r);
    }
    const real = [];
    const flaky = [];
    for (const test of failedTests) {
        const stats = byKey.get(`${test.className}#${test.testName}`);
        const knownFlaky = stats !== undefined && (stats.flakinessScore > scoreThreshold || stats.defaultBranchFailureRate >= baseFailureRate);
        (knownFlaky ? flaky : real).push(test);
    }
    return { real, flaky };
}

module.exports = { parseFailedTests, fetchFlakinessScores, buildFlakinessTable, classifyFailures };
