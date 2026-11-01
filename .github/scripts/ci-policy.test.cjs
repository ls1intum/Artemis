const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const { spawnSync } = require('node:child_process');
const workflow = fs.readFileSync(path.join(__dirname, '../workflows/ci.yml'), 'utf8');
function job(name, source = workflow) {
    const start = source.indexOf(`\n  ${name}:\n`);
    assert.notEqual(start, -1, `Expected job ${name}`);
    const rest = source.slice(start + 1);
    const end = rest.slice(1).search(/^  [a-z][a-z-]+:\n/m);
    return end === -1 ? rest : rest.slice(0, end + 1);
}
function expression(text) {
    return new Function('github', 'needs', `return (${text.replace(/^\$\{\{\s*|\s*\}\}$/g, '').replaceAll('needs.detect-changes', 'needs.detect_changes')});`);
}
function testCondition() {
    const match = job('test').match(/    if: >-\n((?:      [^\n]*\n)+)/);
    assert.ok(match);
    return expression(match[1].trim().replace(/\s+/g, ' '));
}
function gateName() {
    const run = expression(job('all-required-ci-passed').match(/    name: (.+)\n/)[1]);
    return (github, values = needs()) => run(github, values);
}
function context(event, draft, action = 'synchronize') {
    return { event_name: event, event: { action, pull_request: { draft } } };
}
function needs({ current = 'true', deferred = 'false', relevant = 'true', serverInputs = 'false' } = {}) {
    return { detect_changes: { outputs: { current, tests_deferred: deferred, build_relevant: relevant, server_test_inputs: serverInputs } } };
}
test('draft code changes skip the entire expensive test workflow', () => {
    assert.equal(testCondition()(context('pull_request', true), needs({ deferred: 'true' })), false);
});
test('a ready event on the same SHA runs tests and restores the canonical merge gate', () => {
    assert.equal(testCondition()(context('pull_request', false, 'ready_for_review'), needs()), true);
    assert.equal(gateName()(context('pull_request', false, 'ready_for_review')), 'All required CI Passed');
    assert.notEqual(gateName()(context('pull_request', true)), 'All required CI Passed');
});
test('draft completion never reports the required full-test status, even for docs-only changes', () => {
    assert.equal(gateName()(context('pull_request', true)), 'Draft checks passed (tests deferred)');
    assert.equal(testCondition()(context('pull_request', true), needs({ deferred: 'true', relevant: 'false' })), false);
});
for (const event of ['push', 'merge_group', 'workflow_dispatch', 'release']) {
    test(`${event} retains tests and the canonical required gate`, () => {
        assert.equal(testCondition()(context(event, false), needs()), true);
        assert.equal(gateName()(context(event, false)), 'All required CI Passed');
    });
}
test('server disk inputs still run tests on ready PRs when build is irrelevant', () => {
    assert.equal(testCondition()(context('pull_request', false), needs({ relevant: 'false', serverInputs: 'true' })), true);
});
test('irrelevant ready changes remain skipped, and stale heads never allocate test runners', () => {
    assert.equal(testCondition()(context('pull_request', false), needs({ relevant: 'false' })), false);
    assert.equal(testCondition()(context('pull_request', false), needs({ current: 'false' })), false);
});
test('a superseded revision cannot leave a reusable green full-test check on its SHA', () => {
    for (const draft of [true, false]) {
        assert.equal(gateName()(context('pull_request', draft), needs({ current: 'false' })), 'Superseded revision (tests skipped)');
    }
});
test('the current-head check shares change detection and its failure remains required', () => {
    const detect = job('detect-changes');
    assert.match(detect, /require\('\.\/\.github\/scripts\/pr-ready.js'\)/);
    assert.match(detect, /tests_deferred: \$\{\{ steps.ready.outputs.tests_deferred \}\}/);
    assert.doesNotMatch(workflow, /^  pr-ready:/m);
    assert.match(job('all-required-ci-passed'), /      - detect-changes/);
    assert.match(workflow, /types: \[opened, synchronize, reopened, ready_for_review, edited\]/);
});
test('drafts keep test TypeScript compilation on the public quality runner', () => {
    const quality = fs.readFileSync(path.join(__dirname, '../workflows/ci-quality.yml'), 'utf8');
    assert.match(job('quality'), /check_test_types: \$\{\{ github.event_name == 'pull_request' && github.event.pull_request.draft == true \}\}/);
    const step = quality.match(/name: Compile TypeScript Test Files With Typechecking\n([\s\S]*?)(?=\n      -|$)/)[1];
    assert.match(step, /if: inputs.check_test_types/);
    assert.match(step, /run: pnpm exec tsc --project config\/client\/tsconfig.spec.json/);
});
function changeOutputs({ draft, files, event = 'pull_request', force = false }) {
    const script = job('detect-changes')
        .match(/      - id: decide[\s\S]*?        run: \|\n((?:          [^\n]*\n|\n)+)/)[1]
        .replace(/^ {10}/gm, '');
    const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'ci-change-policy-'));
    const output = path.join(directory, 'output');
    try {
        const result = spawnSync('bash', ['-c', script], {
            encoding: 'utf8',
            env: { ...process.env, FORCE: String(force), EVENT_NAME: event, IS_DRAFT: String(draft), CHANGED_FILES: String(files), GITHUB_OUTPUT: output },
        });
        assert.equal(result.status, 0, result.stderr);
        return Object.fromEntries(
            fs
                .readFileSync(output, 'utf8')
                .trim()
                .split('\n')
                .map((line) => line.split('=')),
        );
    } finally {
        fs.rmSync(directory, { recursive: true, force: true });
    }
}
test('the 3000-file fallback still defers E2E for drafts and preserves their test-server image', () => {
    const outputs = changeOutputs({ draft: true, files: 3000 });
    assert.equal(outputs.run_e2e, 'false');
    assert.equal(outputs.build_pr_image, 'true');
    assert.equal(outputs.run_server_tests, 'true');
    assert.equal(outputs.run_client_tests, 'true');
});
test('promoting a 3000-file PR retains complete suite eligibility despite truncated path filters', () => {
    const outputs = changeOutputs({ draft: false, files: 3000 });
    for (const key of ['run_e2e', 'build_pr_image', 'run_server_tests', 'run_client_tests']) assert.equal(outputs[key], 'true');
});
test('manual area filtering can skip Test unless the full-CI override is enabled', () => {
    const filtered = changeOutputs({ draft: false, files: 0, event: 'workflow_dispatch' });
    const forced = changeOutputs({ draft: false, files: 0, event: 'workflow_dispatch', force: true });
    assert.equal(filtered.build_relevant, 'false');
    assert.equal(filtered.server_test_inputs, 'false');
    assert.equal(forced.build_relevant, 'true');
    assert.equal(forced.run_server_tests, 'true');
    assert.equal(forced.run_client_tests, 'true');
    assert.equal(forced.run_e2e, 'true');
});
test('the existing summary records intentional test scope without allocating another job', () => {
    const match = job('ci-summary').match(/      - name: "Test validation scope: (.+)"\n/);
    assert.ok(match, 'Expected run-attempt scope marker in the existing summary');
    const scope = expression(match[1]);
    const values = (deferred, result, affected = '') => ({ ...needs({ deferred }), test: { result, outputs: { affected_tests: affected } } });
    assert.equal(scope({}, values('true', 'skipped')), 'deferred');
    assert.equal(scope({}, values('false', 'success', 'true')), 'affected');
    assert.equal(scope({}, values('false', 'failure', 'true')), 'affected');
    assert.equal(scope({}, values('false', 'success', 'false')), 'full');
    assert.equal(scope({}, values('false', 'skipped')), 'unavailable');
    assert.equal(scope({}, values('false', 'failure')), 'unavailable');
});
test('client execution reuses the assets prepared by strict test compilation on every scope path', () => {
    const tests = fs.readFileSync(path.join(__dirname, '../workflows/ci-test.yml'), 'utf8');
    const client = tests.slice(tests.indexOf('\n  client-tests:\n'));
    const compile = client.indexOf('run: pnpm run compile:tests');
    const execution = client.indexOf('name: TypeScript Test Without Typechecking');
    assert.ok(compile !== -1 && execution > compile, 'Strict compilation must prepare assets before execution');
    const calls = client.split('\n').filter((line) => line.includes('pnpm run test:ci:affected'));
    assert.equal(calls.length, 3, 'Full, affected and fetch-failure paths all need the prepared assets');
    for (const call of calls) assert.match(call, /--prebuilt\b/);
});

function serverPlan(plan, { full = false, exitCode = 0 } = {}) {
    const tests = fs.readFileSync(path.join(__dirname, '../workflows/ci-test.yml'), 'utf8');
    const script = job('server-test-shards', tests)
        .match(/        run: \|\n((?:          [^\n]*\n|\n)+)/)[1]
        .replace(/^ {10}/gm, '');
    const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'ci-server-plan-'));
    const output = path.join(directory, 'output');
    const summary = path.join(directory, 'summary');
    try {
        fs.writeFileSync(path.join(directory, 'git'), '#!/bin/sh\nexit 0\n', { mode: 0o755 });
        fs.writeFileSync(path.join(directory, 'python3'), '#!/bin/sh\nprintf "%s\\n" "$SERVER_PLAN"\nexit "$SERVER_PLAN_EXIT"\n', { mode: 0o755 });
        const result = spawnSync('bash', ['-c', script], {
            cwd: directory,
            encoding: 'utf8',
            env: {
                ...process.env,
                PATH: `${directory}${path.delimiter}${process.env.PATH}`,
                RUN_ALL_TESTS: String(full),
                DEFAULT_BRANCH: 'develop',
                SERVER_PLAN: typeof plan === 'string' ? plan : JSON.stringify(plan),
                SERVER_PLAN_EXIT: String(exitCode),
                GITHUB_OUTPUT: output,
                GITHUB_STEP_SUMMARY: summary,
            },
        });
        const outputs = Object.fromEntries(
            fs
                .readFileSync(output, 'utf8')
                .trim()
                .split('\n')
                .map((line) => {
                    const separator = line.indexOf('=');
                    return [line.slice(0, separator), line.slice(separator + 1)];
                }),
        );
        return { ...result, outputs, summary: fs.readFileSync(summary, 'utf8') };
    } finally {
        fs.rmSync(directory, { recursive: true, force: true });
    }
}
test('the actual server planner carries an exact affected-class plan to one execution shard', () => {
    const classes = ['de.tum.cit.aet.artemis.quiz.QuizExerciseIntegrationTest', 'de.tum.cit.aet.artemis.core.architecture.ArchitectureTest'];
    const result = serverPlan({ mode: 'selected', reason: 'Quiz service consumers', modules: ['quiz'], tests: classes });
    assert.equal(result.status, 0, result.stderr);
    assert.equal(result.outputs.full_suite, 'false');
    assert.deepEqual(result.outputs.tests.split(',').sort(), [...classes].sort());
    assert.equal(JSON.parse(result.outputs.shards).length, 1);
    assert.match(result.summary, /Quiz service consumers/);
    const tests = fs.readFileSync(path.join(__dirname, '../workflows/ci-test.yml'), 'utf8');
    assert.match(job('server-tests', tests), /-Dartemis\.test\.classes="\$SELECTED_TEST_CLASSES"/);
});
for (const [name, plan, options] of [
    ['explicit full validation', { mode: 'selected', modules: ['quiz'], tests: ['de.tum.cit.aet.artemis.quiz.QuizTest'] }, { full: true }],
    ['a selector error', { mode: 'selected', modules: ['quiz'], tests: ['de.tum.cit.aet.artemis.quiz.QuizTest'] }, { exitCode: 1 }],
    ['malformed JSON', '{broken', {}],
    ['an empty class selection', { mode: 'selected', modules: ['quiz'], tests: [] }, {}],
    ['an unsafe class filter', { mode: 'selected', modules: ['quiz'], tests: ['*'] }, {}],
    ['an output-injection class name', { mode: 'selected', modules: ['quiz'], tests: ['de.tum.cit.aet.artemis.quiz.QuizTest\nfull_suite=true'] }, {}],
]) {
    test(`${name} retains all seven server shards`, () => {
        const result = serverPlan(plan, options);
        assert.equal(result.status, 0, result.stderr);
        assert.equal(result.outputs.full_suite, 'true');
        assert.equal(JSON.parse(result.outputs.shards).length, 7);
        assert.ok(!result.outputs.tests);
    });
}

function runGate(results, deferred) {
    const script = job('all-required-ci-passed')
        .match(/        run: \|\n((?:          [^\n]*\n|\n)+)/)[1]
        .replace(/^ {10}/gm, '');
    const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'ci-policy-gate-'));
    const summary = path.join(directory, 'summary');
    try {
        const result = spawnSync('bash', ['-c', script], {
            encoding: 'utf8',
            env: { ...process.env, NEEDS: JSON.stringify(results), TESTS_DEFERRED: deferred, GITHUB_STEP_SUMMARY: summary },
        });
        return { status: result.status, stdout: result.stdout, summary: fs.existsSync(summary) ? fs.readFileSync(summary, 'utf8') : '' };
    } finally {
        fs.rmSync(directory, { recursive: true, force: true });
    }
}
test('the actual draft gate shell reports its limited scope explicitly', () => {
    const result = runGate({ 'detect-changes': { result: 'success' }, test: { result: 'skipped' }, quality: { result: 'success' } }, 'true');
    assert.equal(result.status, 0);
    assert.match(result.stdout, /Draft checks passed/);
    assert.match(result.summary, /required full-test status is not published/);
});
for (const failure of ['failure', 'cancelled']) {
    test(`a ${failure} producer cannot be hidden by cascading skipped tests`, () => {
        const result = runGate({ 'detect-changes': { result: failure }, test: { result: 'skipped' }, quality: { result: 'skipped' } }, 'false');
        assert.equal(result.status, 1);
        assert.match(result.summary, /detect-changes/);
    });
}
