import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import { parse } from 'yaml';

const workflow = parse(readFileSync(new URL('../../../.github/workflows/ci-e2e.yml', import.meta.url), 'utf8'));
const teardown = parse(readFileSync(new URL('../../../.github/actions/e2e-teardown/action.yml', import.meta.url), 'utf8'));

// Evaluate the checked-in expressions with Actions' implicit success() condition.
// A setup failure must still clean up, without reporting a test run that never started.
function evaluate(expression, runner, outcome, { cancelled = false, start = '', failed = false, condition = false } = {}) {
    let source = String(expression)
        .replace(/^\$\{\{\s*|\s*\}\}$/g, '')
        .replace(/steps\.([\w-]+)/g, 'steps["$1"]');
    if (condition && !/\b(?:success|failure|cancelled|always)\(/.test(source)) source = `success() && (${source})`;
    const steps = { [runner]: { outcome }, 'test-timer': { outputs: { start } } };
    return new Function('steps', 'cancelled', 'success', 'contains', 'fromJSON', `return (${source});`)(
        steps,
        () => cancelled,
        () => !failed && !cancelled,
        (values, value) => values.includes(value),
        JSON.parse,
    );
}

const jobs = [
    ['run-e2e-relevant', 'run-tests-phase1'],
    ['run-e2e-remaining', 'run-tests-phase2'],
    ['run-e2e-all-pr', 'run-tests-all-pr'],
    ['run-e2e-all-non-pr', 'run-tests-all-non-pr'],
];

for (const [job, runner] of jobs) {
    describe(`${job} setup-failure reporting`, () => {
        const steps = workflow.jobs[job].steps;
        const cleanup = steps.find((step) => step.id === 'teardown');
        const reports = steps.filter((step) => /^(E2E Test Report|Download all tests results|All Tests Report|Extract failed tests|Classify E2E failures)/.test(step.name ?? ''));
        const timer = steps.find((step) => step.id === 'wall-clock');
        const allowed = (step, outcome, options = {}) => evaluate(step.if ?? 'true', runner, outcome, { ...options, condition: true });

        it.each(['skipped', ''])('cleans up without requiring or reporting results when execution is %j', (outcome) => {
            expect(allowed(cleanup, outcome, { failed: true })).toBe(true);
            expect(evaluate(cleanup.with['require-tests'], runner, outcome)).toBe('warn');
            expect(reports.length).toBeGreaterThanOrEqual(3);
            for (const step of reports) expect(allowed(step, outcome, { failed: true }), step.name).toBe(false);
            if (timer) expect(allowed(timer, outcome, { failed: true })).toBe(false);
        });

        it.each(['success', 'failure'])('requires reports after a %s test step, including on a failed job', (outcome) => {
            expect(evaluate(cleanup.with['require-tests'], runner, outcome)).toBe('error');
            for (const step of reports) expect(allowed(step, outcome, { failed: true }), step.name).toBe(true);
            if (timer) {
                expect(allowed(timer, outcome, { start: '123', failed: true })).toBe(true);
                expect(allowed(timer, outcome, { failed: true })).toBe(false);
            }
        });

        it('respects cancellation for reporting and cleanup', () => {
            for (const step of [cleanup, ...reports, ...(timer ? [timer] : [])]) {
                expect(allowed(step, 'failure', { start: '123', cancelled: true }), step.name).toBe(false);
            }
        });
    });
}

describe('E2E teardown after a failed artifact upload', () => {
    it('still invokes container cleanup', () => {
        const cleanup = teardown.runs.steps.find((step) => step.run?.endsWith('/cleanup.sh'));
        expect(evaluate(cleanup.if ?? 'true', 'tests', 'failure', { failed: true, condition: true })).toBe(true);
        expect(cleanup['continue-on-error']).toBe(true);
    });
});
