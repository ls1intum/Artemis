import { afterEach, describe, it } from 'node:test';
import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { copyFileSync, mkdirSync, mkdtempSync, readFileSync, renameSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';

const root = new URL('../../../', import.meta.url);
const workflow = readFileSync(new URL('.github/workflows/ci-e2e.yml', root), 'utf8');
const temporaryRoots = [];
afterEach(() => temporaryRoots.splice(0).forEach((directory) => rmSync(directory, { recursive: true, force: true })));
function temporaryRoot() {
    const directory = mkdtempSync(join(tmpdir(), 'artemis-e2e-selection-'));
    temporaryRoots.push(directory);
    return directory;
}
function put(directory, file, content = 'fixture\n') {
    mkdirSync(dirname(join(directory, file)), { recursive: true });
    writeFileSync(join(directory, file), content);
}
function repository() {
    const directory = temporaryRoot();
    for (const file of ['determine-relevant-tests.sh', 'e2e-test-mapping.json']) {
        const target = `supporting_scripts/ci/E2E-tests/${file}`;
        mkdirSync(dirname(join(directory, target)), { recursive: true });
        copyFileSync(new URL(target, root), join(directory, target));
    }
    for (const file of ['Login.spec.ts', 'Logout.spec.ts', 'SystemHealth.spec.ts', 'exercise/quiz-exercise/Quiz.spec.ts', 'exercise/text/Text.spec.ts']) {
        put(directory, `src/test/playwright/e2e/${file}`);
    }
    const git = (...args) => execFileSync('git', args, { cwd: directory, encoding: 'utf8', env: { ...process.env, GIT_CONFIG_NOSYSTEM: '1' } }).trim();
    git('init', '--quiet');
    git('config', 'user.email', 'fixture@example.invalid');
    git('config', 'user.name', 'Selection fixture');
    const commit = () => {
        git('add', '-A');
        git('commit', '--quiet', '-m', 'fixture');
    };
    commit();
    git('branch', 'base');
    const select = (base = 'base') => {
        const result = execFileSync('bash', ['supporting_scripts/ci/E2E-tests/determine-relevant-tests.sh', base], {
            cwd: directory,
            encoding: 'utf8',
            stdio: ['ignore', 'pipe', 'pipe'],
        });
        return Object.fromEntries([...result.matchAll(/^OUTPUT: ([^=]+)=(.*)$/gm)].map(([, key, value]) => [key, value]));
    };
    return { directory, git, commit, select };
}

describe('E2E selector against real commit diffs', () => {
    it('selects a mapped Java API module and all smoke specs without the unrelated module', () => {
        const { directory, commit, select } = repository();
        put(directory, 'src/main/java/de/tum/cit/aet/artemis/quiz/web/QuizResource.java');
        commit();
        const result = select();
        assert.equal(result.RUN_ALL_TESTS, 'false');
        assert.deepEqual(result.RELEVANT_TESTS.split(' '), ['e2e/Login.spec.ts', 'e2e/Logout.spec.ts', 'e2e/SystemHealth.spec.ts', 'e2e/exercise/quiz-exercise/']);
        assert.ok(result.REMAINING_TESTS.includes('e2e/exercise/text/'));
    });
    for (const file of [
        'src/main/java/de/tum/cit/aet/artemis/newmodule/Endpoint.java',
        'src/main/webapp/app/shared/table.ts',
        'src/main/java/de/tum/cit/aet/artemis/core/service/distributed/Provider.java',
        'src/main/java/de/tum/cit/aet/artemis/localci/service/BuildQueue.java',
        'src/main/webapp/app/iris/chat.ts',
        'src/test/playwright/e2e/shared/helpers.ts',
        'src/main/resources/config/application.yml',
        'src/test/playwright/support/fixture.ts',
        'src/test/playwright/e2e/space name.spec.ts',
        'unknown/path\nwith-newline',
    ]) {
        it(`fails closed to the full suite for ${file}`, () => {
            const { directory, commit, select } = repository();
            put(directory, file);
            commit();
            assert.equal(select().RUN_ALL_TESTS, 'true');
        });
    }
    it('fails closed when a smoke spec was removed', () => {
        const { directory, commit, select } = repository();
        rmSync(join(directory, 'src/test/playwright/e2e/Login.spec.ts'));
        commit();
        assert.equal(select().RUN_ALL_TESTS, 'true');
    });
    it('fails closed when the mapping is malformed', () => {
        const { directory, commit, select } = repository();
        put(directory, 'supporting_scripts/ci/E2E-tests/e2e-test-mapping.json', '{invalid');
        commit();
        assert.equal(select().RUN_ALL_TESTS, 'true');
    });
    it('selects the full suite for an empty committed diff', () => {
        const { select } = repository();
        assert.equal(select().RUN_ALL_TESTS, 'true');
    });
    it('does not substitute the last commit when the PR base is unavailable', () => {
        const { directory, commit, select } = repository();
        put(directory, 'src/main/webapp/app/quiz/question.ts');
        commit();
        assert.equal(select('missing-base').RUN_ALL_TESTS, 'true');
    });
    it('uses the merge base rather than including unrelated changes made on the base branch', () => {
        const { directory, git, commit, select } = repository();
        git('checkout', '--quiet', 'base');
        put(directory, 'unknown-global-file');
        commit();
        git('checkout', '--quiet', '-');
        put(directory, 'src/main/webapp/app/quiz/question.ts');
        commit();
        assert.equal(select().RUN_ALL_TESTS, 'false');
    });
    it('selects both source modules when a file is renamed across module boundaries', () => {
        const { directory, git, commit, select } = repository();
        const before = 'src/main/webapp/app/quiz/question.ts';
        const after = 'src/main/webapp/app/text/question.ts';
        put(directory, before);
        commit();
        git('branch', '-f', 'base', 'HEAD');
        mkdirSync(dirname(join(directory, after)), { recursive: true });
        renameSync(join(directory, before), join(directory, after));
        commit();
        const result = select();
        assert.ok(result.RELEVANT_TESTS.includes('e2e/exercise/quiz-exercise/'));
        assert.ok(result.RELEVANT_TESTS.includes('e2e/exercise/text/'));
    });
    it('keeps deleted specs out of the Playwright arguments and retains smoke coverage', () => {
        const { directory, commit, select } = repository();
        rmSync(join(directory, 'src/test/playwright/e2e/exercise/quiz-exercise/Quiz.spec.ts'));
        commit();
        const result = select();
        assert.equal(result.RUN_ALL_TESTS, 'false');
        assert.deepEqual(result.RELEVANT_TESTS.split(' '), ['e2e/Login.spec.ts', 'e2e/Logout.spec.ts', 'e2e/SystemHealth.spec.ts']);
    });
    it('computes the remaining suite for spec-only changes so a full-suite override stays full', () => {
        const { directory, commit, select } = repository();
        put(directory, 'src/test/playwright/e2e/exercise/quiz-exercise/Quiz.spec.ts', 'changed\n');
        commit();
        const result = select();
        assert.ok(result.REMAINING_TESTS.includes('e2e/exercise/text/'));
    });
});

// Execute the workflow's actual block scalars; no duplicate copy of the gate or PR comment logic.
function scalar(source, key, indent) {
    const lines = source.split('\n');
    const start = lines.findIndex((line) => line === `${' '.repeat(indent)}${key}: |`);
    assert.notEqual(start, -1, `Missing ${key} block`);
    const result = [];
    for (const line of lines.slice(start + 1)) {
        if (line.trim() && !line.startsWith(' '.repeat(indent + 2))) break;
        result.push(line.slice(indent + 2));
    }
    return result.join('\n');
}
function job(name) {
    const start = workflow.indexOf(`\n  ${name}:\n`);
    assert.notEqual(start, -1);
    return workflow.slice(start).split(/\n  [\w-]+:\n/)[1];
}
function gate(overrides = {}) {
    const output = join(temporaryRoot(), 'output');
    const summary = `${output}-summary`;
    const env = {
        PATH: process.env.PATH,
        GITHUB_OUTPUT: output,
        GITHUB_STEP_SUMMARY: summary,
        IS_PR: 'true',
        RUN_FULL_SUITE: 'false',
        RUN_ALL_TESTS: 'false',
        RELEVANT_TESTS: 'e2e/Login.spec.ts',
        REMAINING_TESTS: 'e2e/exercise/text/',
        DETERMINE_JOB: 'success',
    };
    for (const phase of ['RELEVANT', 'REMAINING', 'ALL_PR', 'ALL_NON_PR']) {
        Object.assign(env, { [`${phase}_RESULT`]: '', [`${phase}_CLASS`]: '', [`${phase}_INFRA`]: '', [`${phase}_JOB`]: 'skipped' });
    }
    Object.assign(env, { RELEVANT_JOB: 'success', RELEVANT_RESULT: 'success', RELEVANT_CLASS: 'clean', RELEVANT_INFRA: 'false' }, overrides);
    execFileSync('bash', ['-c', scalar(job('report-results'), 'run', 8)], { env });
    return Object.fromEntries(
        readFileSync(output, 'utf8')
            .trim()
            .split('\n')
            .map((line) => line.split(/=(.*)/s).slice(0, 2)),
    );
}
function phase2Selected(runFullSuite) {
    const expression = scalar(job('run-e2e-remaining'), 'if', 4).replaceAll('needs.determine-tests', 'needs.determine').replaceAll('needs.run-e2e-relevant', 'needs.relevant');
    return Function(
        'inputs',
        'needs',
        'cancelled',
        'contains',
        'fromJSON',
        `return (${expression});`,
    )(
        { is_pr: true, run_full_suite: runFullSuite },
        {
            determine: { result: 'success', outputs: { run_all_tests: 'false', remaining_tests: 'e2e/exercise/text/' } },
            relevant: { result: 'success', outputs: { infrastructure_failed: 'false', phase_result: 'clean' } },
        },
        () => false,
        (list, value) => list.includes(value),
        JSON.parse,
    );
}

describe('workflow selection and required report contract', () => {
    it('does not schedule Phase 2 in selective PR mode', () => assert.equal(phase2Selected(false), false));
    it('retains Phase 2 for full-suite callers', () => assert.equal(phase2Selected(true), true));
    it('passes an intentional Phase 2 omission with explicit selected coverage', () => {
        const result = gate();
        assert.equal(result.conclusion, 'success');
        assert.match(result.scope, /Selected.*smoke/);
        assert.match(result.description, /Selected E2E tests passed/);
    });
    for (const overrides of [
        { RELEVANT_JOB: 'skipped', RELEVANT_RESULT: '', RELEVANT_CLASS: '', RELEVANT_INFRA: '' },
        { RELEVANT_RESULT: '' },
        { RUN_ALL_TESTS: 'true' },
        { RUN_FULL_SUITE: 'true' },
        { IS_PR: 'false', DETERMINE_JOB: 'skipped' },
    ]) {
        it(`blocks a missing expected phase: ${JSON.stringify(overrides)}`, () => assert.equal(gate(overrides).conclusion, 'failure'));
    }
    it('reports incomplete full-suite execution as an error instead of a green raw status', () => {
        const result = gate({ RUN_FULL_SUITE: 'true' });
        assert.equal(result.status, 'error');
    });
    it('passes a completed full two-phase suite', () => {
        const result = gate({ RUN_FULL_SUITE: 'true', REMAINING_JOB: 'success', REMAINING_RESULT: 'success', REMAINING_CLASS: 'clean', REMAINING_INFRA: 'false' });
        assert.equal(result.conclusion, 'success');
        assert.equal(result.scope, 'Full suite');
    });
    it('passes a completed full-suite fallback even when selective coverage was requested', () => {
        const result = gate({
            RUN_ALL_TESTS: 'true',
            RELEVANT_JOB: 'skipped',
            RELEVANT_RESULT: '',
            RELEVANT_CLASS: '',
            RELEVANT_INFRA: '',
            ALL_PR_JOB: 'success',
            ALL_PR_RESULT: 'success',
            ALL_PR_CLASS: 'clean',
            ALL_PR_INFRA: 'false',
        });
        assert.equal(result.conclusion, 'success');
        assert.equal(result.scope, 'Full suite');
        assert.equal(result.phase2_intentionally_skipped, 'false');
    });
    it('passes the completed non-PR full-suite path used by develop, merge groups, and manual builds', () => {
        const result = gate({
            IS_PR: 'false',
            DETERMINE_JOB: 'skipped',
            RELEVANT_JOB: 'skipped',
            RELEVANT_RESULT: '',
            RELEVANT_CLASS: '',
            RELEVANT_INFRA: '',
            ALL_NON_PR_JOB: 'success',
            ALL_NON_PR_RESULT: 'success',
            ALL_NON_PR_CLASS: 'clean',
            ALL_NON_PR_INFRA: 'false',
        });
        assert.equal(result.conclusion, 'success');
        assert.equal(result.scope, 'Full suite');
    });
    it('preserves the blocking Phase 1 verdict when full-mode Phase 2 fails fast', () => {
        const result = gate({ RUN_FULL_SUITE: 'true', RELEVANT_RESULT: 'failure', RELEVANT_CLASS: 'real' });
        assert.equal(result.conclusion, 'failure');
        assert.match(result.description, /non-flaky/);
    });
    it('blocks cancellation of a selected phase', () => assert.equal(gate({ RELEVANT_JOB: 'cancelled', RELEVANT_RESULT: '' }).conclusion, 'failure'));
    it('exonerates only completed selected tests with known flaky history', () =>
        assert.equal(gate({ RELEVANT_RESULT: 'failure', RELEVANT_CLASS: 'flaky' }).conclusion, 'neutral'));
    it('keeps unknown failures blocking in selective mode', () => assert.equal(gate({ RELEVANT_RESULT: 'failure', RELEVANT_CLASS: 'unknown' }).conclusion, 'failure'));
});

describe('PR comment scope reporting', () => {
    const AsyncFunction = Object.getPrototypeOf(async () => {}).constructor;
    const context = { repo: { owner: 'fixture', repo: 'Artemis' }, serverUrl: 'https://github.invalid', runId: 1 };
    async function finalComment(existing = '') {
        const script = scalar(job('report-results').split('      - name: Update E2E comment with overall status')[1], 'script', 10);
        let body;
        const issues = {
            getComment: async () => ({ data: { body: existing } }),
            updateComment: async (data) => {
                body = data.body;
            },
            createComment: async (data) => {
                body = data.body;
            },
        };
        await new AsyncFunction('github', 'context', 'process', script)({ rest: { issues } }, context, {
            env: {
                INPUT_PR_NUMBER: '1',
                INPUT_COMMENT_ID: existing ? '2' : '',
                INPUT_CONCLUSION: 'success',
                INPUT_DESCRIPTION: 'Selected E2E tests passed',
                INPUT_SCOPE: 'Selected relevant + smoke specs',
                INPUT_PHASE2_INTENTIONALLY_SKIPPED: 'true',
                INPUT_PHASE1_RESULT: 'success',
                INPUT_PHASE2_RESULT: 'skipped',
            },
        });
        return body;
    }
    it('makes an intentionally omitted Phase 2 explicit when finalizing an existing comment', async () => {
        const body = await finalComment('<!-- PHASE2 -->pending<!-- /PHASE2 -->\n<!-- OVERALL -->');
        assert.match(body, /Intentionally skipped \(selective PR coverage\)/);
        assert.match(body, /Selected relevant \+ smoke specs/);
        assert.match(body, /full-suite coverage was not requested/);
        assert.doesNotMatch(body, /no remaining tests/);
    });
    it('reports selective scope even when Phase 1 could not create its comment', async () => {
        const body = await finalComment();
        assert.match(body, /Selected E2E tests passed/);
        assert.match(body, /Phase 2 intentionally omitted/);
        assert.match(body, /full-suite coverage was not requested/);
    });
    it('shows selective scope and the omitted paths in the Phase 1 comment', async () => {
        const script = scalar(job('run-e2e-relevant').split('      - name: Post E2E comment (Phase 1)')[1] || '', 'script', 10);
        let body;
        const require = () => ({ parseFailedTests: () => [], fetchFlakinessScores: async () => [], buildFlakinessTable: () => '' });
        const github = {
            rest: {
                issues: {
                    createComment: async (data) => {
                        body = data.body;
                    },
                },
            },
        };
        await new AsyncFunction('require', 'github', 'context', 'process', script)(require, github, context, {
            env: {
                INPUT_PR_NUMBER: '1',
                INPUT_RELEVANT_TESTS: 'e2e/Login.spec.ts',
                INPUT_REMAINING_TESTS: 'e2e/exam/',
                INPUT_RUN_FULL_SUITE: 'false',
            },
        });
        assert.match(body, /Intentionally skipped/);
        assert.match(body, /Selected relevant \+ smoke specs/);
        assert.match(body, /Phase 2 \(not selected\).*e2e\/exam\//);
        assert.doesNotMatch(body, /Pending/);
    });
});
