/**
 * Computes the combined server + client line-coverage value that the README badge renders.
 *
 * Reads the two coverage reports CI already produces on every `develop` push (see ci-test.yml) and
 * folds them into a single percentage, weighted implicitly by codebase size:
 *
 *     (server.covered + client.covered) / (server.total + client.total)
 *
 * Lines are the only metric both tools emit natively — JaCoCo's report-level `<counter type="LINE">`
 * and Vitest's `total.lines` — so they are what the badge combines. The two are not semantically
 * identical (JaCoCo counts lines of compiled bytecode, Istanbul counts instrumented TS lines), which
 * is why the badge is documented as a rough combined figure rather than an exact one.
 *
 * E2E is deliberately absent: `ci-e2e.yml` runs Playwright black-box against a Docker stack with no
 * JaCoCo agent and no instrumented client build, so it emits no coverage data to fold in.
 *
 * The exported functions are pure so that the guards below can be unit-tested against fixtures; all
 * file IO and process-exit handling lives in the CLI wrapper at the bottom.
 *
 * Usage:
 *   node compute-coverage-badge.mjs --jacoco <aggregated/jacocoTestReport.xml> \
 *                                   --vitest <coverage-summary.json> \
 *                                   --out <coverage.json> \
 *                                   [--previous <coverage.json>] [--sha <commit-sha>]
 *
 * Exit codes: 0 = wrote a new badge, 3 = the value is unchanged (the healthy no-op), 4 = a guard
 * rejected the computed value, 1 = bad invocation. 3 and 4 are split because the collapse guard
 * LATCHES: it compares against the last *published* total, which only advances when something is
 * published, so a legitimate halving of the codebase would refuse forever. CI therefore surfaces 4
 * as a warning, which is the only thing that makes such a freeze visible. See the README.
 */

import fs from 'fs';
import path from 'path';
import { pathToFileURL } from 'url';

/**
 * A new value is rejected when the combined line total falls below this fraction of the previously
 * published total. That is the "a module's report went missing" guard: a partial report shrinks the
 * denominator drastically, while a genuine coverage regression leaves it essentially unchanged. The
 * band is deliberately wide so the badge can never freeze on a real regression.
 *
 * Note the guard latches: it compares against the last *published* total, which only moves when a
 * value is published. A legitimate halving of the codebase (a large module deleted, or the Vitest
 * `include` narrowed) therefore trips it on every subsequent push until someone re-seeds the branch.
 * That is why it exits 4 rather than 3 — CI turns 4 into a warning so the freeze is noticed.
 */
export const MIN_TOTAL_RATIO = 0.5;

/** Shields badge colours, highest threshold first. */
const COLOR_THRESHOLDS = [
    { min: 90, color: 'brightgreen' },
    { min: 80, color: 'green' },
    { min: 70, color: 'yellowgreen' },
    { min: 60, color: 'yellow' },
    { min: 50, color: 'orange' },
    { min: 0, color: 'red' },
];

/**
 * Picks the Shields colour for a coverage percentage.
 * @param {number} pct coverage percentage in [0, 100]
 * @returns {string} a Shields colour name
 */
export function colorFor(pct) {
    return COLOR_THRESHOLDS.find((threshold) => pct >= threshold.min).color;
}

/**
 * Extracts the report-level LINE counter from a JaCoCo XML report.
 *
 * JaCoCo repeats `<counter>` at method, class, package and report level, so summing every match
 * would multiply-count the same lines. The report-level counters are the only ones that follow the
 * final `</package>`, which is what this slices on. A report with no packages carries no totals
 * either and is treated as unparseable.
 *
 * @param {string} xml the full JaCoCo XML report
 * @returns {{covered: number, total: number} | undefined} undefined when no report-level LINE counter exists
 */
export function parseJacocoLines(xml) {
    if (typeof xml !== 'string') {
        return undefined;
    }
    const lastPackageEnd = xml.lastIndexOf('</package>');
    if (lastPackageEnd === -1) {
        return undefined;
    }
    const tail = xml.slice(lastPackageEnd + '</package>'.length);
    // Everything after the final `</package>` must be report-level counters and nothing else.
    // Asserting that shape is what makes a mis-anchored parse fail loudly instead of quietly:
    // in a truncated report `lastIndexOf` lands on an EARLIER package's close, and the next LINE
    // counter then belongs to a `<sourcefile>` — a small but entirely plausible pair of numbers
    // that would sail past the guards below and publish a wrong badge. The same assertion rejects
    // a `<group>`-structured aggregate, where the tail would carry a `</group>` and the counter
    // read would be one group's total rather than the report's.
    if (!/^\s*(?:<counter\b[^>]*\/>\s*)*<\/report>\s*$/.test(tail)) {
        return undefined;
    }
    // Attributes are matched individually rather than as a fixed `missed`/`covered` pair, so a
    // JaCoCo version that reorders them does not silently stop matching.
    const lineCounter = tail.match(/<counter\b[^>]*\btype="LINE"[^>]*\/>/);
    if (!lineCounter) {
        return undefined;
    }
    const missed = lineCounter[0].match(/\bmissed="(\d+)"/);
    const covered = lineCounter[0].match(/\bcovered="(\d+)"/);
    if (!missed || !covered) {
        return undefined;
    }
    const missedCount = Number(missed[1]);
    const coveredCount = Number(covered[1]);
    return { covered: coveredCount, total: coveredCount + missedCount };
}

/**
 * Extracts the aggregate line counts from an Istanbul/Vitest `coverage-summary.json`.
 * @param {unknown} summary the parsed coverage summary
 * @returns {{covered: number, total: number} | undefined} undefined when the totals are absent or non-numeric
 */
export function parseVitestLines(summary) {
    const lines = summary?.total?.lines;
    if (!lines) {
        return undefined;
    }
    const { covered, total } = lines;
    // Istanbul never emits counts like these, but this function feeds a number that gets published
    // unattended, so a corrupt summary must not become a >100% badge. `total === 0` is deliberately
    // NOT rejected here: computeBadge reports it more precisely as "measured zero lines".
    if (!Number.isSafeInteger(covered) || !Number.isSafeInteger(total) || covered < 0 || total < 0 || covered > total) {
        return undefined;
    }
    return { covered, total };
}

/**
 * Decides whether to publish a new badge value, applying the stability guards.
 *
 * Every rejection keeps the previously published value, which is what makes the badge stable across
 * a flaky run: the caller simply does not commit, and Shields keeps serving the last good file.
 *
 * @param {object} options
 * @param {string} [options.jacocoXml] the aggregated JaCoCo XML report, absent when the artifact is missing
 * @param {unknown} [options.vitestSummary] the parsed Vitest coverage summary, absent when the artifact is missing
 * @param {object} [options.previous] the currently published coverage.json, absent on the very first run
 * @param {string} [options.sha] the commit the value was computed from
 * @param {string} [options.now] ISO timestamp to stamp the value with
 * @returns {{status: 'publish', badge: object} | {status: 'skip', kind: 'unchanged' | 'guard', reason: string}}
 */
export function computeBadge({ jacocoXml, vitestSummary, previous, sha, now } = {}) {
    const server = parseJacocoLines(jacocoXml);
    if (!server) {
        return { status: 'skip', kind: 'guard', reason: 'the server JaCoCo report is missing, truncated, or has no report-level LINE counter' };
    }
    const client = parseVitestLines(vitestSummary);
    if (!client) {
        return { status: 'skip', kind: 'guard', reason: 'the client Vitest summary is missing or has no total.lines counts' };
    }
    // A report that parsed but measured nothing means the run produced no usable data, not that the
    // codebase is untested.
    if (server.total === 0 || client.total === 0) {
        return { status: 'skip', kind: 'guard', reason: 'a coverage report measured zero lines' };
    }
    if (server.covered === 0 || client.covered === 0) {
        return { status: 'skip', kind: 'guard', reason: 'a coverage report covered zero lines' };
    }

    const combined = { covered: server.covered + client.covered, total: server.total + client.total };
    const previousTotal = previous?.combined?.total;
    if (Number.isFinite(previousTotal) && combined.total < previousTotal * MIN_TOTAL_RATIO) {
        return {
            status: 'skip',
            kind: 'guard',
            reason: `the combined line total collapsed from ${previousTotal} to ${combined.total}, which indicates a partial report rather than a coverage change`,
        };
    }

    const pct = (combined.covered / combined.total) * 100;
    const message = `${pct.toFixed(1)}%`;
    const color = colorFor(pct);
    // Nothing visible changed, so committing would only add noise to the badges branch.
    if (previous?.message === message && previous?.color === color) {
        return { status: 'skip', kind: 'unchanged', reason: `the value is unchanged at ${message}` };
    }

    return {
        status: 'publish',
        badge: {
            schemaVersion: 1,
            label: 'coverage',
            message,
            color,
            exact: Math.round(pct * 100) / 100,
            server,
            client,
            combined,
            sha,
            updatedAt: now,
        },
    };
}

/**
 * Reads a file, returning undefined rather than throwing when it is absent or unreadable — a missing
 * artifact is an expected outcome the guards handle, not an error.
 * @param {string | undefined} filePath
 * @returns {string | undefined}
 */
function readFileIfPresent(filePath) {
    if (!filePath || !fs.existsSync(filePath)) {
        return undefined;
    }
    try {
        return fs.readFileSync(filePath, 'utf-8');
    } catch {
        return undefined;
    }
}

/**
 * Parses JSON, returning undefined rather than throwing on malformed input.
 * @param {string | undefined} raw
 * @returns {unknown}
 */
function parseJsonIfValid(raw) {
    if (raw === undefined) {
        return undefined;
    }
    try {
        return JSON.parse(raw);
    } catch {
        return undefined;
    }
}

/**
 * Minimal `--flag value` parser; unknown flags are ignored so the workflow can pass extras.
 * @param {string[]} argv
 * @returns {Record<string, string>}
 */
function parseArgs(argv) {
    const args = {};
    for (let i = 0; i < argv.length; i += 1) {
        if (argv[i].startsWith('--')) {
            args[argv[i].slice(2)] = argv[i + 1];
            i += 1;
        }
    }
    return args;
}

function main() {
    const args = parseArgs(process.argv.slice(2));
    if (!args.jacoco || !args.vitest || !args.out) {
        console.error('Usage: compute-coverage-badge.mjs --jacoco <report.xml> --vitest <summary.json> --out <coverage.json> [--previous <coverage.json>] [--sha <sha>]');
        process.exit(1);
    }

    // `--previous` and `--out` are deliberately the same file in CI. That is safe because every read
    // happens while evaluating these arguments, strictly before the write below.
    const result = computeBadge({
        jacocoXml: readFileIfPresent(args.jacoco),
        vitestSummary: parseJsonIfValid(readFileIfPresent(args.vitest)),
        previous: parseJsonIfValid(readFileIfPresent(args.previous)),
        sha: args.sha,
        now: new Date().toISOString(),
    });

    if (result.status === 'skip') {
        console.log(`Keeping the previously published coverage badge: ${result.reason}.`);
        process.exit(result.kind === 'unchanged' ? 3 : 4);
    }

    fs.mkdirSync(path.dirname(path.resolve(args.out)), { recursive: true });
    fs.writeFileSync(args.out, `${JSON.stringify(result.badge, undefined, 2)}\n`);
    const { message, server, client } = result.badge;
    console.log(`Coverage badge: ${message} (server ${server.covered}/${server.total} lines, client ${client.covered}/${client.total} lines).`);
    process.exit(0);
}

// Only run the CLI when executed directly, so the spec can import the pure functions.
if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
    main();
}
