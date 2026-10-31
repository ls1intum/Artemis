const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const { spawnSync } = require('node:child_process');
const workflow = fs.readFileSync(path.join(__dirname, '../workflows/ci.yml'), 'utf8');
function job(name) {
    const start = workflow.indexOf(`\n  ${name}:\n`);
    assert.notEqual(start, -1, `Expected job ${name}`);
    const rest = workflow.slice(start + 1);
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
    assert.match(workflow, /types: \[opened, synchronize, reopened, ready_for_review\]/);
});
test('drafts keep test TypeScript compilation on the public quality runner', () => {
    const quality = fs.readFileSync(path.join(__dirname, '../workflows/ci-quality.yml'), 'utf8');
    assert.match(job('quality'), /check_test_types: \$\{\{ github.event_name == 'pull_request' && github.event.pull_request.draft == true \}\}/);
    const step = quality.match(/name: Compile TypeScript Test Files With Typechecking\n([\s\S]*?)(?=\n      -|$)/)[1];
    assert.match(step, /if: inputs.check_test_types/);
    assert.match(step, /run: pnpm exec tsc --project config\/client\/tsconfig.spec.json/);
});

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
