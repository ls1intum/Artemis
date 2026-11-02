import assert from 'node:assert/strict';
import { afterEach, test } from 'vitest';
import { parse } from 'yaml';
import { cpSync, existsSync, mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { spawnSync } from 'node:child_process';
import { tmpdir } from 'node:os';
import { createRequire } from 'node:module';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const require = createRequire(import.meta.url);
const repository = resolve(dirname(fileURLToPath(import.meta.url)), '../../..');
const roots = [];
afterEach(() => {
    for (const root of roots.splice(0)) rmSync(root, { recursive: true, force: true });
});

function sandbox() {
    const root = mkdtempSync(join(tmpdir(), 'artemis-e2e-runner-'));
    roots.push(root);
    mkdirSync(join(root, 'bin'));
    return root;
}

const passed = { selected: 1, completed: 1, status: 'passed', errors: 0, unexpected: 0 };

function runTests(execution, { selected = 1, multinodeSelected = selected, collectionExit = 0, multinodeCollectionExit = collectionExit, exit = 0, cluster = '' } = {}) {
    const root = sandbox();
    for (const file of ['run-tests.sh', 'completion-reporter.cjs']) {
        const source = join(repository, 'src/test/playwright', file);
        if (existsSync(source)) cpSync(source, join(root, file));
    }
    // Only the external Playwright/coverage commands are replaced. The shell runner and completion verifier are real.
    writeFileSync(
        join(root, 'bin/pnpm'),
        `#!/usr/bin/env node
const fs = require('node:fs');
const args = process.argv.slice(2);
if (args.slice(0, 3).join(' ') !== 'exec playwright test') process.exit(0);
const collection = args.includes('--list');
const selected = process.env.PLAYWRIGHT_TEST_TYPE === 'multinode' ? process.env.MULTINODE_SELECTED : process.env.SELECTED;
fs.appendFileSync('calls', collection ? 'collect\\n' : 'execute\\n');
if (collection) {
  if (process.env.PLAYWRIGHT_COMPLETION_FILE) fs.writeFileSync(process.env.PLAYWRIGHT_COMPLETION_FILE, JSON.stringify({selected: Number(selected), completed: 0, status: 'passed', errors: 0, unexpected: 0}));
  process.exit(Number(process.env.PLAYWRIGHT_TEST_TYPE === 'multinode' ? process.env.MULTINODE_COLLECTION_EXIT : process.env.COLLECTION_EXIT));
}
fs.writeFileSync('test-reports/results-' + process.env.PLAYWRIGHT_TEST_TYPE + '.xml', '<testsuite tests="1" failures="0"><testcase name="passes"/></testsuite>');
if (process.env.EXECUTION !== 'null' && process.env.PLAYWRIGHT_COMPLETION_FILE) fs.writeFileSync(process.env.PLAYWRIGHT_COMPLETION_FILE, process.env.EXECUTION);
process.exit(Number(process.env.EXECUTION_EXIT));
`,
        { mode: 0o755 },
    );
    const result = spawnSync('bash', ['run-tests.sh', 'e2e/Example.spec.ts'], {
        cwd: root,
        encoding: 'utf8',
        env: {
            ...process.env,
            PATH: `${join(root, 'bin')}:${process.env.PATH}`,
            SELECTED: String(selected),
            MULTINODE_SELECTED: String(multinodeSelected),
            COLLECTION_EXIT: String(collectionExit),
            MULTINODE_COLLECTION_EXIT: String(multinodeCollectionExit),
            EXECUTION: JSON.stringify(execution),
            EXECUTION_EXIT: String(exit),
            EXPECTED_CLUSTER_NODE_COUNT: cluster,
            PLAYWRIGHT_REPORT_SERVER_URL: '',
            PLAYWRIGHT_TEST_PATHS: '',
        },
    });
    return { ...result, root };
}

for (const [name, execution, options] of [
    ['global setup fails before a testcase starts', { ...passed, selected: 0, completed: 0, status: 'failed', errors: 1 }, { exit: 1 }],
    ['a worker crashes after one passing testcase', { ...passed, selected: 2, completed: 1, status: 'failed', errors: 1 }, { selected: 2, exit: 1 }],
    ['a fatal error happens after every testcase passes', { ...passed, status: 'failed', errors: 1 }, { exit: 1 }],
    ['a nonzero process has an otherwise passing report', passed, { exit: 1 }],
    ['a process exits zero without completion evidence', null, {}],
    ['execution silently omits a selected testcase', passed, { selected: 2 }],
    ['collection fails before execution', null, { collectionExit: 1 }],
]) {
    test(`fails closed when ${name}`, () => {
        const result = runTests(execution, options);
        assert.equal(result.status, 1, result.stdout + result.stderr);
        assert.equal(existsSync(join(result.root, 'test-reports/.reporter-failed')), false);
    });
}

test('accepts a complete successful execution', () => {
    const result = runTests(passed);
    assert.equal(result.status, 0, result.stdout + result.stderr);
});

test('fails when every selected project is empty', () => {
    const result = runTests(null, { selected: 0, cluster: '3' });
    assert.equal(result.status, 1, result.stdout + result.stderr);
    assert.equal(readFileSync(join(result.root, 'calls'), 'utf8'), 'collect\ncollect\n');
});

test('allows an empty parallel project when the multi-node project has selected tests', () => {
    const result = runTests(passed, { selected: 0, multinodeSelected: 1, cluster: '3' });
    assert.equal(result.status, 0, result.stdout + result.stderr);
    assert.equal(readFileSync(join(result.root, 'calls'), 'utf8'), 'collect\ncollect\nexecute\n');
});

function executeContainer({ exit = 0, pullExit = 0, reporter = false, evidence = '' } = {}) {
    const root = sandbox();
    mkdirSync(join(root, 'deployment/docker'), { recursive: true });
    mkdirSync(join(root, 'supporting_scripts/ci/E2E-tests'), { recursive: true });
    mkdirSync(join(root, 'src/test/playwright/test-reports'), { recursive: true });
    writeFileSync(join(root, '.successful'), 'stale');
    writeFileSync(join(root, 'evidence'), evidence);
    cpSync(join(repository, 'supporting_scripts/ci/E2E-tests/execute.sh'), join(root, 'execute.sh'));
    writeFileSync(join(root, 'supporting_scripts/ci/E2E-tests/verify-image-revision.sh'), '#!/bin/sh\nexit 0\n');
    writeFileSync(
        join(root, 'bin/docker'),
        `#!/bin/sh
case " $* " in
  *" pull "*) exit ${pullExit} ;;
esac
${reporter ? 'touch ../../src/test/playwright/test-reports/.reporter-failed' : ''}
cp ../../evidence ../../src/test/playwright/test-reports/runner-status.env
exit ${exit}
`,
        { mode: 0o755 },
    );
    const result = spawnSync('sh', ['execute.sh', 'postgres-localci', 'playwright'], {
        cwd: root,
        env: { ...process.env, PATH: `${join(root, 'bin')}:${process.env.PATH}`, ARTEMIS_E2E_JWT_SECRET: 'fixture', GITHUB_OUTPUT: join(root, 'output') },
        encoding: 'utf8',
    });
    return { ...result, root };
}

test('preserves a container failure when an optional reporter also fails', () => {
    const result = executeContainer({ exit: 12, reporter: true });
    assert.equal(result.status, 12, result.stdout + result.stderr);
    assert.equal(existsSync(join(result.root, '.successful')), false);
    assert.match(readFileSync(join(result.root, 'output'), 'utf8'), /reporter_failed=true/);
});

test('does not retain success evidence after an unsuccessful container run', () => {
    const result = executeContainer({ exit: 12 });
    assert.equal(result.status, 12, result.stdout + result.stderr);
    assert.equal(existsSync(join(result.root, '.successful')), false);
});

test('stops when pulling the test images fails', () => {
    const result = executeContainer({ pullExit: 7 });
    assert.equal(result.status, 7, result.stdout + result.stderr);
    assert.equal(existsSync(join(result.root, '.successful')), false);
    assert.match(readFileSync(join(result.root, 'output'), 'utf8'), /infrastructure_failed=true/);
});

test('completion reporter counts final outcomes once across retries and records global errors', async () => {
    const root = sandbox();
    const record = join(root, 'completion.json');
    const result = spawnSync(
        process.execPath,
        [
            '-e',
            `
        const Reporter = require(process.argv[1]);
        const reporter = new Reporter();
        const test = { id: 'one', outcome: () => 'flaky' };
        reporter.onBegin({}, { allTests: () => [test] });
        reporter.onTestEnd(test, { status: 'failed' });
        reporter.onTestEnd(test, { status: 'passed' });
        reporter.onError({ message: 'worker interrupted' });
        reporter.onEnd({ status: 'failed' });
    `,
            join(repository, 'src/test/playwright/completion-reporter.cjs'),
        ],
        {
            encoding: 'utf8',
            env: { ...process.env, PLAYWRIGHT_COMPLETION_FILE: record },
        },
    );
    assert.equal(result.status, 0, result.stderr);
    assert.deepEqual(JSON.parse(readFileSync(record, 'utf8')), { selected: 1, completed: 1, status: 'failed', errors: 1, unexpected: 0 });
});

test('optional reporter errors are advisory without altering test outcomes', () => {
    const root = sandbox();
    const source = join(repository, 'src/test/playwright/optional-reporter.cjs');
    assert.equal(existsSync(source), true, 'optional reporting must explicitly isolate its own failures');
    cpSync(source, join(root, 'optional-reporter.cjs'));
    mkdirSync(join(root, 'node_modules/monocart-reporter'), { recursive: true });
    writeFileSync(
        join(root, 'node_modules/monocart-reporter/index.js'),
        `module.exports = class {
        onBegin() { throw new Error('report initialization failed'); }
        async onEnd() { throw new Error('report export failed'); }
    };`,
    );
    const marker = join(root, 'reporter-failed');
    const result = spawnSync(
        process.execPath,
        [
            '-e',
            `
        const Reporter = require('./optional-reporter.cjs');
        const reporter = new Reporter({});
        reporter.onBegin({}, {});
        reporter.onEnd({status: 'passed'}).then(result => { if (result !== undefined) process.exit(2); });
    `,
        ],
        { cwd: root, encoding: 'utf8', env: { ...process.env, PLAYWRIGHT_OPTIONAL_REPORTER_MARKER: marker } },
    );
    assert.equal(result.status, 0, result.stderr);
    assert.match(readFileSync(marker, 'utf8'), /onBegin/);
});

function realPlaywright(spec, { setup = '', list = false, optional = false } = {}) {
    const root = sandbox();
    const record = join(root, 'completion.json');
    const marker = join(root, 'reporter-failed');
    const playwright = dirname(require.resolve('@playwright/test'));
    const reporter = join(repository, 'src/test/playwright/completion-reporter.cjs');
    writeFileSync(join(root, 'example.spec.cjs'), `const {test, expect} = require(${JSON.stringify(playwright)});\n${spec}`);
    if (setup) writeFileSync(join(root, 'setup.cjs'), `module.exports = async () => { ${setup} };`);
    if (optional) {
        // Exercise Playwright's real reporter dispatch with only the external coverage implementation replaced.
        cpSync(join(repository, 'src/test/playwright/optional-reporter.cjs'), join(root, 'optional-reporter.cjs'));
        mkdirSync(join(root, 'node_modules/monocart-reporter'), { recursive: true });
        writeFileSync(join(root, 'node_modules/monocart-reporter/index.js'), `module.exports = class { async onEnd() { throw new Error('coverage export failed'); } };`);
    }
    writeFileSync(
        join(root, 'playwright.config.cjs'),
        `module.exports = {
        testDir: ${JSON.stringify(root)}, testMatch: '**/*.spec.cjs', workers: 1, retries: 1,
        reporter: [[${JSON.stringify(reporter)}]${optional ? ", ['./optional-reporter.cjs']" : ''}],
        ${setup ? "globalSetup: './setup.cjs'," : ''}
    };`,
    );
    const result = spawnSync(
        process.execPath,
        [join(playwright, 'cli.js'), 'test', '--config', join(root, 'playwright.config.cjs'), ...(list ? ['--list', '--pass-with-no-tests'] : [])],
        {
            cwd: root,
            encoding: 'utf8',
            env: { ...process.env, PLAYWRIGHT_COMPLETION_FILE: record, PLAYWRIGHT_OPTIONAL_REPORTER_MARKER: marker },
        },
    );
    return { ...result, record: existsSync(record) ? JSON.parse(readFileSync(record, 'utf8')) : null, marker: existsSync(marker) };
}

test('real Playwright collection records zero selected tests without running global setup', () => {
    const result = realPlaywright('', { list: true, setup: "throw new Error('setup must not run during collection')" });
    assert.equal(result.status, 0, result.stdout + result.stderr);
    assert.deepEqual(result.record, { selected: 0, completed: 0, status: 'passed', errors: 0, unexpected: 0 });
});

test('real Playwright records passed, skipped, expected-failure and retried tests as complete', () => {
    const result = realPlaywright(`
        test('passes', () => expect(1).toBe(1));
        test.skip('skipped', () => {});
        test('expected failure', () => { test.fail(); expect(1).toBe(2); });
        test('retried', ({}, info) => { expect(info.retry).toBe(1); });
    `);
    assert.equal(result.status, 0, result.stdout + result.stderr);
    assert.deepEqual(result.record, { selected: 4, completed: 4, status: 'passed', errors: 0, unexpected: 0 });
});

test('real Playwright global setup failure cannot produce successful completion evidence', () => {
    const result = realPlaywright("test('never starts', () => {});", { setup: "throw new Error('setup crashed')" });
    assert.equal(result.status, 1, result.stdout + result.stderr);
    assert.equal(result.record.status, 'failed');
    assert.equal(result.record.completed, 0);
    assert.equal(result.record.errors, 1);
});

test('real Playwright completed assertion failures remain distinct from infrastructure failures', () => {
    const result = realPlaywright("test('assertion fails', () => expect(1).toBe(2));");
    assert.equal(result.status, 1, result.stdout + result.stderr);
    assert.deepEqual(result.record, { selected: 1, completed: 1, status: 'failed', errors: 0, unexpected: 1 });
});

test('real Playwright optional reporter rejection leaves a complete passing execution advisory', () => {
    const result = realPlaywright("test('passes', () => {});", { optional: true });
    assert.equal(result.status, 0, result.stdout + result.stderr);
    assert.deepEqual(result.record, { selected: 1, completed: 1, status: 'passed', errors: 0, unexpected: 0 });
    assert.equal(result.marker, true);
});

test('real Playwright worker process failure remains a failed execution after a passing testcase', () => {
    const result = realPlaywright(`
        test('passes', () => {});
        test('worker terminates', () => process.exit(7));
    `);
    assert.equal(result.status, 1, result.stdout + result.stderr);
    assert.equal(result.record.status, 'failed');
    assert.equal(result.record.unexpected, 1);
    assert.ok(result.record.errors > 0, 'worker termination must remain infrastructure failure even when represented as a test failure');
});

function overallVerdict(infrastructureFailed) {
    const workflow = parse(readFileSync(join(repository, '.github/workflows/ci-e2e.yml'), 'utf8'));
    const gate = workflow.jobs['report-results'].steps.find((step) => step.id === 'overall-status').run;
    const root = sandbox();
    const output = join(root, 'output');
    const env = {
        PATH: process.env.PATH,
        GITHUB_OUTPUT: output,
        DETERMINE_JOB: 'success',
        IS_PR: 'true',
        RUN_FULL_SUITE: 'false',
        RUN_ALL_TESTS: 'false',
        RELEVANT_TESTS: 'e2e/Login.spec.ts',
        REMAINING_TESTS: 'e2e/exam/',
    };
    for (const phase of ['RELEVANT', 'REMAINING', 'ALL_PR', 'ALL_NON_PR']) {
        env[`${phase}_RESULT`] = '';
        env[`${phase}_CLASS`] = '';
        env[`${phase}_INFRA`] = '';
        env[`${phase}_JOB`] = 'skipped';
    }
    Object.assign(env, { RELEVANT_RESULT: 'failure', RELEVANT_CLASS: 'flaky', RELEVANT_JOB: 'success', RELEVANT_INFRA: infrastructureFailed });
    const result = spawnSync('bash', ['-c', gate], { env, encoding: 'utf8' });
    assert.equal(result.status, 0, result.stderr);
    return readFileSync(output, 'utf8');
}

for (const laterCrash of [true, false]) {
    test(`known flaky assertion failures ${laterCrash ? 'cannot hide a later collection crash' : 'remain eligible for exoneration after complete execution'}`, () => {
        const runner = runTests({ ...passed, status: 'failed', unexpected: 1 }, { exit: 1, cluster: laterCrash ? '3' : '', multinodeCollectionExit: 1 });
        assert.equal(runner.status, 1, runner.stdout + runner.stderr);
        const evidence = readFileSync(join(runner.root, 'test-reports/runner-status.env'), 'utf8');
        const container = executeContainer({ exit: 1, evidence });
        assert.equal(container.status, 1, container.stdout + container.stderr);
        const infrastructureFailed = readFileSync(join(container.root, 'output'), 'utf8').match(/^infrastructure_failed=(.*)$/m)?.[1];
        assert.equal(infrastructureFailed, String(laterCrash));
        assert.match(overallVerdict(infrastructureFailed), laterCrash ? /conclusion=failure/ : /conclusion=neutral/);
    });
}

test('missing runner evidence cannot make a successful container trustworthy', () => {
    const result = executeContainer();
    assert.equal(result.status, 1, result.stdout + result.stderr);
    assert.match(readFileSync(join(result.root, 'output'), 'utf8'), /infrastructure_failed=true/);
});
