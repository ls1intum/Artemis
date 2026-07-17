import { describe, it, expect, vi, afterEach } from 'vitest';
import { createRequire } from 'node:module';
import { EventEmitter } from 'node:events';
import { writeFileSync, mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import https from 'node:https';

// fetch-flakiness.js is CommonJS (loaded by actions/github-script); createRequire imports it cleanly.
const require = createRequire(import.meta.url);
const { parseFailedTests, fetchFlakinessScores, buildFlakinessTable, classifyFailures } = require('./fetch-flakiness.js');

const test = (name) => ({ testName: name, className: 'spec.ts' });
const helios = (name, flakinessScore, defaultBranchFailureRate, combinedFailureRate = 0) => ({
    testName: name,
    className: 'spec.ts',
    flakinessScore,
    defaultBranchFailureRate,
    combinedFailureRate,
});

// Realistic Playwright JUnit output: <testsuite name> and <testcase classname> are both the spec path;
// only failed/errored cases carry a <failure>/<error> child.
const junit = (cases, suite = 'e2e/Exam.spec.ts') =>
    `<?xml version="1.0" encoding="UTF-8"?>\n<testsuites tests="${cases.length}">\n<testsuite name="${suite}" tests="${cases.length}">\n` +
    cases
        .map((c) => {
            const child = c.fail ? `<failure message="expect(received)" type="FAILURE">stack</failure>` : c.err ? `<error message="crash"/>` : '';
            return `<testcase name="${c.name}" classname="${suite}" time="1.0">${child}</testcase>`;
        })
        .join('\n') +
    `\n</testsuite>\n</testsuites>\n`;

const writeXml = (xml) => {
    const dir = mkdtempSync(join(tmpdir(), 'flaky-'));
    const path = join(dir, 'results.xml');
    writeFileSync(path, xml);
    return { path, cleanup: () => rmSync(dir, { recursive: true, force: true }) };
};

// Simulate https.request(url, options, cb): drive the response/error/timeout paths the code handles.
const stubHttps = ({ statusCode = 200, body = '', event } = {}) =>
    vi.spyOn(https, 'request').mockImplementation((url, options, cb) => {
        const req = new EventEmitter();
        req.write = () => {};
        req.destroy = () => {};
        req.end = () => {
            if (event === 'error') return void req.emit('error', new Error('ECONNRESET'));
            if (event === 'timeout') return void req.emit('timeout');
            const res = new EventEmitter();
            res.statusCode = statusCode;
            cb(res);
            if (body) res.emit('data', body);
            res.emit('end');
        };
        return req;
    });

afterEach(() => vi.restoreAllMocks());

describe('parseFailedTests', () => {
    it('extracts only failed and errored cases, never the passing ones', () => {
        const { path, cleanup } = writeXml(junit([{ name: 'passes' }, { name: 'fails', fail: true }, { name: 'crashes', err: true }]));
        try {
            const failed = parseFailedTests(path);
            expect(failed.map((f) => f.testName).sort()).toEqual(['crashes', 'fails']);
        } finally {
            cleanup();
        }
    });

    it('carries the suite path into testSuiteName and className (Helios needs both)', () => {
        const { path, cleanup } = writeXml(junit([{ name: 'fails', fail: true }], 'e2e/Course.spec.ts'));
        try {
            expect(parseFailedTests(path)[0]).toEqual({ testName: 'fails', className: 'e2e/Course.spec.ts', testSuiteName: 'e2e/Course.spec.ts' });
        } finally {
            cleanup();
        }
    });

    it('deduplicates a test that failed on multiple retries into one entry', () => {
        // Playwright emits a testcase per retry; the same class#name must collapse to one.
        const xml = junit([{ name: 'flaky', fail: true }]).replace(
            '</testsuite>',
            `<testcase name="flaky" classname="e2e/Exam.spec.ts" time="1.0"><failure message="again"/></testcase>\n</testsuite>`,
        );
        const { path, cleanup } = writeXml(xml);
        try {
            expect(parseFailedTests(path)).toHaveLength(1);
        } finally {
            cleanup();
        }
    });

    it('returns [] for a passing suite, a missing file, and unreadable input (never throws)', () => {
        const pass = writeXml(junit([{ name: 'a' }, { name: 'b' }]));
        try {
            expect(parseFailedTests(pass.path)).toEqual([]);
        } finally {
            pass.cleanup();
        }
        expect(parseFailedTests('/no/such/results.xml')).toEqual([]);
    });
});

describe('fetchFlakinessScores', () => {
    it('returns [] without touching the network when there are no failures or no secret', async () => {
        const spy = stubHttps({ body: '[]' });
        expect(await fetchFlakinessScores([], 'secret')).toEqual([]);
        expect(await fetchFlakinessScores([test('t')], '')).toEqual([]);
        expect(spy).not.toHaveBeenCalled();
    });

    it('returns the parsed array on a 200 with a JSON array body', async () => {
        stubHttps({ statusCode: 200, body: JSON.stringify([helios('t', 40, 0.3)]) });
        const res = await fetchFlakinessScores([test('t')], 'secret');
        expect(res).toHaveLength(1);
        expect(res[0].flakinessScore).toBe(40);
    });

    it('degrades a 200 with a non-array body ({} or null) to [] (the array guard)', async () => {
        stubHttps({ statusCode: 200, body: '{}' });
        expect(await fetchFlakinessScores([test('t')], 'secret')).toEqual([]);
        stubHttps({ statusCode: 200, body: 'null' });
        expect(await fetchFlakinessScores([test('t')], 'secret')).toEqual([]);
    });

    it('degrades malformed JSON, a non-200, a socket error, and a timeout all to []', async () => {
        stubHttps({ statusCode: 200, body: '{not json' });
        expect(await fetchFlakinessScores([test('t')], 'secret')).toEqual([]);
        stubHttps({ statusCode: 503, body: 'upstream down' });
        expect(await fetchFlakinessScores([test('t')], 'secret')).toEqual([]);
        stubHttps({ event: 'error' });
        expect(await fetchFlakinessScores([test('t')], 'secret')).toEqual([]);
        stubHttps({ event: 'timeout' });
        expect(await fetchFlakinessScores([test('t')], 'secret')).toEqual([]);
    });
});

describe('buildFlakinessTable', () => {
    it('returns an empty string for empty or non-array input', () => {
        expect(buildFlakinessTable([])).toBe('');
        expect(buildFlakinessTable(undefined)).toBe('');
        expect(buildFlakinessTable({})).toBe('');
    });

    it('renders one row per result with rates as percentages', () => {
        const table = buildFlakinessTable([helios('t', 42, 0.1, 0.2)]);
        expect(table).toContain('`spec.ts#t`');
        expect(table).toContain('**42%**');
        expect(table).toContain('10.0%');
        expect(table).toContain('20.0%');
    });

    it('escapes markdown-significant characters in test identifiers', () => {
        const table = buildFlakinessTable([{ className: 'a|b', testName: 'c`d', flakinessScore: 1, defaultBranchFailureRate: 0, combinedFailureRate: 0 }]);
        expect(table).toContain('a\\|b');
        expect(table).toContain('c\\`d');
    });
});

// classifyFailures reads Helios' thresholds from module scope: broken at dfr >= 0.5, flaky at
// score > 30 or dfr >= 0.01, real otherwise. Pins the boundaries the required E2E merge gate depends on.
describe('classifyFailures', () => {
    it('marks a failure Helios has never seen as real (fail-safe against missing history)', () => {
        expect(classifyFailures([test('t')], [])).toEqual({ real: [test('t')], flaky: [], broken: [] });
    });

    it('exonerates nothing when the Helios response is empty (outage => every failure is real)', () => {
        const failed = [test('a'), test('b')];
        expect(classifyFailures(failed, []).real).toHaveLength(2);
    });

    it('classifies a test broken at exactly the 50% ceiling as broken, not flaky (>= boundary)', () => {
        const { broken, flaky } = classifyFailures([test('t')], [helios('t', 0, 0.5)]);
        expect(broken).toHaveLength(1);
        expect(flaky).toHaveLength(0);
    });

    it('classifies a test just under the ceiling (49.9%) as flaky, not broken', () => {
        const { broken, flaky } = classifyFailures([test('t')], [helios('t', 2, 0.499)]);
        expect(flaky).toHaveLength(1);
        expect(broken).toHaveLength(0);
    });

    it('prefers broken over flaky when a test is both broken and highly flaky-scored', () => {
        const { broken, flaky } = classifyFailures([test('t')], [helios('t', 90, 0.6)]);
        expect(broken).toHaveLength(1);
        expect(flaky).toHaveLength(0);
    });

    it('exonerates a classic flake (high score, low failure rate) as flaky', () => {
        expect(classifyFailures([test('t')], [helios('t', 90, 0.05)]).flaky).toHaveLength(1);
    });

    it('exonerates a test that also fails on the default branch above the noise floor as flaky', () => {
        expect(classifyFailures([test('t')], [helios('t', 0, 0.02)]).flaky).toHaveLength(1);
    });

    it('treats a test exactly at the 1% noise floor as flaky (>= boundary), below it as real', () => {
        expect(classifyFailures([test('t')], [helios('t', 0, 0.01)]).flaky).toHaveLength(1);
        expect(classifyFailures([test('t')], [helios('t', 0, 0.009)]).real).toHaveLength(1);
    });

    it('marks a test reliable on the default branch and not flaky-scored as a real regression', () => {
        expect(classifyFailures([test('t')], [helios('t', 4, 0)]).real).toHaveLength(1);
    });

    it('routes a mixed batch into all three buckets at once', () => {
        const failed = [test('regression'), test('flake'), test('broken'), test('unknown')];
        const results = [helios('regression', 5, 0), helios('flake', 80, 0.03), helios('broken', 0, 0.9)];
        const { real, flaky, broken } = classifyFailures(failed, results);
        expect(real.map((t) => t.testName).sort()).toEqual(['regression', 'unknown']);
        expect(flaky.map((t) => t.testName)).toEqual(['flake']);
        expect(broken.map((t) => t.testName)).toEqual(['broken']);
    });
});

// classify-failures.js is the actions/github-script entry point: it reads results.xml, asks Helios,
// and reduces the three buckets to the single `phase_result` string the gate branches on. Stub only
// the Helios network call (direct cache assignment, so afterEach's mock-restore leaves it intact);
// parseFailedTests runs for real against a temp results.xml.
const fetchCachePath = require.resolve('./fetch-flakiness.js');
require('./fetch-flakiness.js');
let heliosStub = [];
require.cache[fetchCachePath].exports.fetchFlakinessScores = async () => heliosStub;
const classifyPhase = require('./classify-failures.js');

const runPhase = async (xml, stub) => {
    heliosStub = stub;
    const { path, cleanup } = writeXml(xml);
    const outputs = {};
    const core = { setOutput: (k, v) => (outputs[k] = v), info: () => {} };
    process.env.INPUT_RESULTS_FILE = path;
    try {
        await classifyPhase(core);
    } finally {
        cleanup();
        delete process.env.INPUT_RESULTS_FILE;
    }
    return outputs.phase_result;
};

describe('classify-failures (phase_result selection)', () => {
    it('reports `clean` when the phase has no failures', async () => {
        expect(await runPhase(junit([{ name: 'a' }, { name: 'b' }]), [])).toBe('clean');
    });

    it('reports `real` when any failure is a genuine regression, outranking broken and flaky', async () => {
        const xml = junit([{ name: 'reg', fail: true }, { name: 'brk', fail: true }], 'e2e/Exam.spec.ts');
        const stub = [helios('reg', 0, 0), helios('brk', 0, 0.9)].map((h) => ({ ...h, className: 'e2e/Exam.spec.ts' }));
        expect(await runPhase(xml, stub)).toBe('real');
    });

    it('reports `broken` when there is no regression but a broken test failed', async () => {
        const xml = junit([{ name: 'brk', fail: true }], 'e2e/Exam.spec.ts');
        expect(await runPhase(xml, [{ ...helios('brk', 0, 0.9), className: 'e2e/Exam.spec.ts' }])).toBe('broken');
    });

    it('reports `flaky` when every failure is known-flaky', async () => {
        const xml = junit([{ name: 'flk', fail: true }], 'e2e/Exam.spec.ts');
        expect(await runPhase(xml, [{ ...helios('flk', 80, 0.03), className: 'e2e/Exam.spec.ts' }])).toBe('flaky');
    });

    it('reports `real` on a failure Helios does not recognise (fail-safe)', async () => {
        expect(await runPhase(junit([{ name: 'unknown', fail: true }]), [])).toBe('real');
    });
});
