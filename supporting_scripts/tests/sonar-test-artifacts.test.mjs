import assert from 'node:assert/strict';
import fs from 'node:fs';
import { test } from 'node:test';
import { parse } from 'yaml';

const workflow = parse(fs.readFileSync(new URL('../../.github/workflows/ci-test.yml', import.meta.url), 'utf8'));

function evaluate(expression, context) {
    const source = expression.replace(/^\$\{\{\s*|\s*\}\}$/g, '').replace(/\.([\w]+(?:-[\w]+)+)/g, "['$1']");
    return new Function('needs', 'steps', 'jobs', 'success', 'cancelled', 'always', `return (${source});`)(
        context.needs ?? {},
        context.steps ?? {},
        context.jobs ?? {},
        () => context.success ?? true,
        () => context.cancelled ?? false,
        () => true,
    );
}

function artifacts(job, context) {
    return workflow.jobs[job].steps.filter((step) => step.uses?.startsWith('actions/upload-artifact@') && (!step.if || evaluate(step.if, context))).map((step) => step.with.name);
}

function serverContext(fullSuite, success = true) {
    return { success, needs: { 'server-test-shards': { outputs: { full_suite: String(fullSuite) } }, 'server-tests': { result: success ? 'success' : 'failure' } } };
}

test('a successful selected server run publishes only scope-honest execution data for Sonar', () => {
    const context = serverContext(false);
    assert.ok(artifacts('server-tests', context).includes('Sonar Server Coverage'));
    assert.ok(!artifacts('server-tests', context).some((name) => name.startsWith('Server Coverage Execution Data')));
    assert.ok(!artifacts('server-tests-report', context).includes('Server JaCoCo XML'));
    const selected = workflow.jobs['server-tests'].steps.find((step) => step.with?.name === 'Sonar Server Coverage');
    assert.equal(selected.with.path, 'build/jacoco/test.exec');
    assert.equal(selected.with['if-no-files-found'], 'error');
});

test('a complete server run preserves full reports without publishing selected execution data', () => {
    const context = serverContext(true);
    assert.ok(!artifacts('server-tests', context).includes('Sonar Server Coverage'));
    assert.ok(artifacts('server-tests-report', context).includes('Server JaCoCo XML'));
});

test('a failed selected server run cannot advertise usable Sonar execution data', () => {
    assert.ok(!artifacts('server-tests', serverContext(false, false)).includes('Sonar Server Coverage'));
});

test('selected client LCOV cannot be mistaken for complete-suite coverage artifacts', () => {
    const selected = artifacts('client-tests', { steps: { 'client-tests': { outputs: { mode: 'affected', coverage: 'false', sonar_coverage: 'true' } } } });
    assert.ok(selected.includes('Sonar Client Coverage'));
    assert.ok(!selected.includes('Vitest Coverage Report'));
    assert.ok(!selected.includes('Client Coverage Summaries'));
    const upload = workflow.jobs['client-tests'].steps.find((step) => step.with?.name === 'Sonar Client Coverage');
    assert.equal(upload.with.path, 'build/test-results/vitest/sonar-coverage/**/lcov.info');
    assert.equal(upload.with['if-no-files-found'], 'error');
});

test('full client reports retain their existing complete-suite artifact contract', () => {
    const full = artifacts('client-tests', { steps: { 'client-tests': { outputs: { mode: 'full', coverage: 'true' } } } });
    assert.ok(full.includes('Vitest Coverage Report'));
    assert.ok(full.includes('Client Coverage Summaries'));
    assert.ok(!full.includes('Sonar Client Coverage'));
});

test('failed, skipped, or reportless selected client runs publish no Sonar coverage', () => {
    for (const [mode, available, success] of [
        ['affected', 'true', false],
        ['affected', 'false', true],
        ['none', undefined, true],
    ]) {
        const published = artifacts('client-tests', { success, steps: { 'client-tests': { outputs: { mode, coverage: 'false', sonar_coverage: available } } } });
        assert.ok(!published.includes('Sonar Client Coverage'));
    }
});

test('reusable workflow exposes partial coverage separately without changing affected scope', () => {
    const jobs = {
        'server-test-shards': { outputs: { full_suite: 'false' } },
        'server-tests': { outputs: { sonar_coverage: 'true' } },
        'client-tests': { outputs: { mode: 'affected', sonar_coverage: 'true' } },
    };
    assert.ok(workflow.on.workflow_call.outputs.server_sonar_coverage, 'selected server coverage availability must be exposed');
    assert.ok(workflow.on.workflow_call.outputs.client_sonar_coverage, 'selected client coverage availability must be exposed');
    assert.equal(evaluate(workflow.on.workflow_call.outputs.affected_tests.value, { jobs }), true);
    assert.equal(evaluate(workflow.on.workflow_call.outputs.server_sonar_coverage.value, { jobs }), 'true');
    assert.equal(evaluate(workflow.on.workflow_call.outputs.client_sonar_coverage.value, { jobs }), 'true');
    assert.equal(evaluate(workflow.jobs['server-tests'].outputs.sonar_coverage, { steps: { 'sonar-coverage': { outputs: { 'artifact-id': '123' } } } }), true);
    assert.equal(evaluate(workflow.jobs['server-tests'].outputs.sonar_coverage, { steps: { 'sonar-coverage': { outputs: { 'artifact-id': '' } } } }), false);
});
