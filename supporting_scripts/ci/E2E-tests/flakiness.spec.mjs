import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { EventEmitter } from 'node:events';
import { mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { execFileSync } from 'node:child_process';
import { parse } from 'yaml';

const require = createRequire(import.meta.url);
const https = require('node:https');
const { fetchFlakinessScores, classifyFailures } = require('../../../.github/scripts/fetch-flakiness.js');
const classifyPhase = require('../../../.github/scripts/classify-failures.js');
const testCase = { className: 'e2e/example.spec.ts', testName: 'submits an exercise', testSuiteName: 'e2e/example.spec.ts' };
const reliable = { ...testCase, flakinessScore: 0, defaultBranchFailureRate: 0, combinedFailureRate: 0 };
const roots = [];

function temporaryRoot() {
    const root = mkdtempSync(join(tmpdir(), 'artemis-e2e-flakiness-'));
    roots.push(root);
    return root;
}

// Model the transport so no test calls Helios or needs a credential.
function respond({ status = 200, body = JSON.stringify([reliable]), interrupt, stall = false } = {}) {
    const request = new EventEmitter();
    const response = new EventEmitter();
    response.statusCode = status;
    response.destroy = vi.fn();
    request.write = vi.fn();
    request.destroy = vi.fn();
    request.end = vi.fn();
    vi.spyOn(https, 'request').mockImplementation((_url, _options, callback) => {
        request.end.mockImplementation(() => {
            queueMicrotask(() => {
                callback(response);
                if (interrupt) response.emit(interrupt, new Error('connection closed'));
                else if (!stall) {
                    response.emit('data', body);
                    response.emit('end');
                }
            });
        });
        return request;
    });
    return { request, response };
}

beforeEach(() => {
    vi.spyOn(console, 'warn').mockImplementation(() => {});
});

afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllEnvs();
    vi.useRealTimers();
    for (const root of roots.splice(0)) rmSync(root, { recursive: true, force: true });
});

describe('optional Helios history', () => {
    it('uses valid history when Helios is available', async () => {
        const { request } = respond();
        expect(await fetchFlakinessScores([testCase], 'fixture-secret')).toEqual([reliable]);
        expect(request.write).toHaveBeenCalledWith(JSON.stringify({ testCases: [testCase] }));
        expect(console.warn).not.toHaveBeenCalled();
    });

    it.each([401, 403, 500, 503])('falls back immediately on HTTP %s without exposing the response body', async (status) => {
        const { response } = respond({ status, body: 'sensitive service response', stall: true });
        expect(await fetchFlakinessScores([testCase], 'fixture-secret')).toEqual([]);
        expect(response.destroy).toHaveBeenCalled();
        expect(console.warn).toHaveBeenCalledWith(expect.stringContaining(`HTTP ${status}`));
        expect(console.warn).not.toHaveBeenCalledWith(expect.stringContaining('sensitive service response'));
        expect(console.warn).not.toHaveBeenCalledWith(expect.stringContaining('fixture-secret'));
        if (status === 401 || status === 403) {
            expect(console.warn).toHaveBeenCalledWith(expect.stringContaining('Rotate HELIOS_REPO_SECRET in Helios and the GitHub repository settings'));
        }
    });

    it('does not contact Helios when no secret is configured', async () => {
        const request = vi.spyOn(https, 'request');
        expect(await fetchFlakinessScores([testCase], '')).toEqual([]);
        expect(request).not.toHaveBeenCalled();
        expect(console.warn).toHaveBeenCalledWith(expect.stringContaining('no repository secret'));
    });

    it('does not need Helios when tests pass', async () => {
        const request = vi.spyOn(https, 'request');
        expect(await fetchFlakinessScores([], '')).toEqual([]);
        expect(request).not.toHaveBeenCalled();
        expect(console.warn).not.toHaveBeenCalled();
    });

    it.each(['not JSON', 'null', '{}'])('falls back on an invalid response: %s', async (body) => {
        respond({ body });
        expect(await fetchFlakinessScores([testCase], 'fixture-secret')).toEqual([]);
        expect(console.warn).toHaveBeenCalled();
    });

    it('keeps valid entries in a partially malformed response', async () => {
        respond({ body: JSON.stringify([null, {}, { ...reliable, flakinessScore: '30' }, reliable]) });
        expect(await fetchFlakinessScores([testCase], 'fixture-secret')).toEqual([reliable]);
    });

    it.each(['error', 'aborted'])('falls back when the response emits %s', async (interrupt) => {
        respond({ interrupt });
        expect(await fetchFlakinessScores([testCase], 'fixture-secret')).toEqual([]);
        expect(console.warn).toHaveBeenCalledWith(expect.stringContaining('response interrupted'));
    });

    it('falls back on a connection error', async () => {
        const { request } = respond({ stall: true });
        const pending = fetchFlakinessScores([testCase], 'fixture-secret');
        request.emit('error', new Error('unreachable'));
        expect(await pending).toEqual([]);
    });

    it('bounds a stalled response by the request deadline', async () => {
        vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] });
        const { request } = respond({ stall: true });
        const pending = fetchFlakinessScores([testCase], 'fixture-secret');
        await vi.advanceTimersByTimeAsync(10000);
        expect(await pending).toEqual([]);
        expect(request.destroy).toHaveBeenCalled();
        expect(console.warn).toHaveBeenCalledWith(expect.stringContaining('request timed out'));
    });
});

describe('failure classification', () => {
    it('does not call a failure real or flaky without history', () => {
        expect(classifyFailures([testCase], [], 30, 0.01)).toEqual({ real: [], flaky: [], unknown: [testCase] });
    });

    it('recognizes reliable failures', () => {
        expect(classifyFailures([testCase], [reliable], 30, 0.01)).toEqual({ real: [testCase], flaky: [], unknown: [] });
    });

    it.each([{ flakinessScore: 31 }, { defaultBranchFailureRate: 0.01 }])('exonerates a failure only with matching flaky history: %j', (stats) => {
        expect(classifyFailures([testCase], [{ ...reliable, ...stats }], 30, 0.01)).toEqual({ real: [], flaky: [testCase], unknown: [] });
    });

    it('does not exonerate an unknown test alongside a known flake', () => {
        const unknown = { ...testCase, testName: 'another test' };
        expect(classifyFailures([testCase, unknown], [{ ...reliable, flakinessScore: 31 }], 30, 0.01)).toEqual({ real: [], flaky: [testCase], unknown: [unknown] });
    });

    it('classifies a phase as unknown after a 401 and reports its counts', async () => {
        respond({ status: 401 });
        const results = join(temporaryRoot(), 'results.xml');
        writeFileSync(results, '<testsuite name="example"><testcase classname="example" name="fails"><failure>assertion</failure></testcase></testsuite>');
        vi.stubEnv('INPUT_RESULTS_FILE', results);
        vi.stubEnv('HELIOS_REPO_SECRET', 'fixture-secret');
        const core = { setOutput: vi.fn(), info: vi.fn() };
        await classifyPhase(core);
        expect(core.setOutput).toHaveBeenCalledWith('phase_result', 'unknown');
        expect(core.setOutput).toHaveBeenCalledWith('real_count', '0');
        expect(core.setOutput).toHaveBeenCalledWith('unknown_count', '1');
    });

    it('classifies a passing phase as clean without contacting Helios', async () => {
        const request = vi.spyOn(https, 'request');
        const results = join(temporaryRoot(), 'results.xml');
        writeFileSync(results, '<testsuite><testcase classname="example" name="passes"/></testsuite>');
        vi.stubEnv('INPUT_RESULTS_FILE', results);
        const core = { setOutput: vi.fn(), info: vi.fn() };
        await classifyPhase(core);
        expect(core.setOutput).toHaveBeenCalledWith('phase_result', 'clean');
        expect(request).not.toHaveBeenCalled();
    });
});

describe('required E2E gate', () => {
    const workflow = parse(readFileSync(new URL('../../../.github/workflows/ci-e2e.yml', import.meta.url), 'utf8'));
    const gate = workflow.jobs['report-results'].steps.find((step) => step.id === 'overall-status').run;

    function verdict(overrides = {}) {
        const output = join(temporaryRoot(), 'output');
        const env = { PATH: process.env.PATH, GITHUB_OUTPUT: output, DETERMINE_JOB: 'success' };
        for (const phase of ['RELEVANT', 'REMAINING', 'ALL_PR', 'ALL_NON_PR']) {
            env[`${phase}_RESULT`] = '';
            env[`${phase}_CLASS`] = '';
            env[`${phase}_INFRA`] = 'false';
            env[`${phase}_JOB`] = 'skipped';
        }
        env.RELEVANT_JOB = 'success';
        env.RELEVANT_RESULT = 'success';
        env.RELEVANT_CLASS = 'clean';
        Object.assign(env, overrides);
        execFileSync('bash', ['-c', gate], { env });
        return Object.fromEntries(
            readFileSync(output, 'utf8')
                .trim()
                .split('\n')
                .map((line) => line.split(/=(.*)/s).slice(0, 2)),
        );
    }

    it('passes clean tests without requiring history', () => {
        expect(verdict()).toMatchObject({ conclusion: 'success', status: 'success' });
    });

    it('keeps a failure blocking without claiming a confirmed regression when history is unavailable', () => {
        expect(verdict({ RELEVANT_RESULT: 'failure', RELEVANT_CLASS: 'unknown' })).toMatchObject({
            conclusion: 'failure',
            description: 'E2E tests failed after retries (flakiness history unavailable)',
        });
    });

    it.each(['unknown', '', 'clean'])('does not let another flaky phase exonerate an unclassified failure (%s)', (classification) => {
        expect(
            verdict({ RELEVANT_RESULT: 'failure', RELEVANT_CLASS: 'flaky', REMAINING_JOB: 'success', REMAINING_RESULT: 'failure', REMAINING_CLASS: classification }).conclusion,
        ).toBe('failure');
    });

    it.each(['true', ''])('blocks known-flaky tests when infrastructure completion is %s', (infrastructure) => {
        expect(verdict({ RELEVANT_RESULT: 'failure', RELEVANT_CLASS: 'flaky', RELEVANT_INFRA: infrastructure })).toMatchObject({
            conclusion: 'failure',
            description: 'E2E infrastructure failure',
        });
    });

    it('still exonerates failures known to be flaky', () => {
        expect(verdict({ RELEVANT_RESULT: 'failure', RELEVANT_CLASS: 'flaky' }).conclusion).toBe('neutral');
    });

    it('still blocks failures with reliable history', () => {
        expect(verdict({ RELEVANT_RESULT: 'failure', RELEVANT_CLASS: 'real' }).description).toBe('E2E: real (non-flaky) test failure');
    });

    it.each(['failure', 'cancelled'])('keeps infrastructure %s blocking even with known-flaky tests', (result) => {
        expect(verdict({ RELEVANT_RESULT: 'failure', RELEVANT_CLASS: 'flaky', RELEVANT_JOB: result })).toMatchObject({
            conclusion: 'failure',
            description: 'E2E infrastructure failure',
        });
    });
});
