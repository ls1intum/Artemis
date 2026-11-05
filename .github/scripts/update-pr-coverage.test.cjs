const { afterEach, describe, it } = require('node:test');
const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const { join } = require('node:path');
const { createRequire } = require('node:module');
const updateCoverage = require('./update-pr-coverage.js');
const workflowRequire = createRequire(join(__dirname, '../../package.json'));

const originalEnvironment = { ...process.env };
afterEach(() => {
    process.env = { ...originalEnvironment };
});
const originalBody = 'Problem\n\n### Test Coverage\n\n| stale full-suite coverage | 100% |\n\n### Checklist\n\n- [x] Existing task';
async function update(overrides = {}, body = originalBody) {
    process.env = {
        ...originalEnvironment,
        PR_NUMBER: '42',
        PR_AUTHOR: 'fixture-author',
        HAS_CLIENT: 'true',
        HAS_SERVER: 'false',
        COVERAGE_TABLE: '| fresh full-suite coverage | 95% |',
        COVERAGE_SUCCESS: 'true',
        TEST_CONCLUSION: 'success',
        TEST_RUN_URL: 'https://github.invalid/actions/runs/7',
        POST_AUTHOR_COMMENT: 'false',
        TEST_SCOPE: 'full',
        ...overrides,
    };
    let updated;
    const comments = [];
    const github = {
        rest: {
            pulls: {
                get: async () => ({ data: { body } }),
                update: async (request) => {
                    updated = request.body;
                },
            },
            issues: {
                createComment: async (request) => {
                    comments.push(request.body);
                },
            },
        },
    };
    await updateCoverage({ github, context: { repo: { owner: 'fixture', repo: 'Artemis' } }, core: {} });
    return { body: updated, comments };
}

describe('PR coverage body updates', () => {
    it('preserves full-run coverage tables and surrounding PR content', async () => {
        const result = await update();
        assert.match(result.body, /fresh full-suite coverage/);
        assert.doesNotMatch(result.body, /stale full-suite coverage/);
        assert.match(result.body, /^Problem\n\n### Test Coverage/);
        assert.match(result.body, /### Checklist\n\n- \[x\] Existing task$/);
        assert.equal(result.comments.length, 0);
    });
    it('replaces stale global coverage with an explicit successful affected-selection status', async () => {
        const result = await update({ TEST_SCOPE: 'affected' });
        assert.match(result.body, /affected.*selection/i);
        assert.match(result.body, /success/);
        assert.match(result.body, /full-suite coverage.*not (generated|available)/i);
        assert.doesNotMatch(result.body, /fresh full-suite coverage|stale full-suite coverage/);
        assert.equal(result.comments.length, 0);
    });
    it('replaces stale coverage with explicit draft test deferral', async () => {
        const result = await update({ TEST_SCOPE: 'deferred', TEST_CONCLUSION: 'skipped' });
        assert.match(result.body, /tests.*deferred.*draft/i);
        assert.match(result.body, /ready for review/i);
        assert.doesNotMatch(result.body, /fresh full-suite coverage|stale full-suite coverage|tests passed|affected.*selection/i);
        assert.equal(result.comments.length, 0);
    });
    it('reports failed affected tests honestly without publishing coverage tables', async () => {
        const result = await update({ TEST_SCOPE: 'affected', TEST_CONCLUSION: 'failure' });
        assert.match(result.body, /affected.*selection/i);
        assert.match(result.body, /failure/);
        assert.match(result.body, /workflow logs.*https:\/\/github.invalid\/actions\/runs\/7/);
        assert.doesNotMatch(result.body, /fresh full-suite coverage|stale full-suite coverage|tests passed/i);
    });
    for (const scope of ['unavailable', 'unrecognized']) {
        it(`does not mistake ${scope} coverage for no code or a full run`, async () => {
            const result = await update({ TEST_SCOPE: scope, COVERAGE_SUCCESS: 'true' });
            assert.match(result.body, /missing|incomplete|unavailable/i);
            assert.doesNotMatch(result.body, /No code changes|fresh full-suite coverage|stale full-suite coverage/);
        });
    }
    it('retains the explicit no-code result when reliable API detection finds no code', async () => {
        const result = await update({ TEST_SCOPE: 'affected', HAS_CLIENT: 'false', HAS_SERVER: 'false', POST_AUTHOR_COMMENT: 'true' });
        assert.match(result.body, /No code changes detected/);
        assert.doesNotMatch(result.body, /fresh full-suite coverage|stale full-suite coverage/);
        assert.equal(result.comments.length, 0);
    });
    it('retains full-run failure messaging when coverage exists', async () => {
        const result = await update({ TEST_CONCLUSION: 'failure' });
        assert.match(result.body, /did not pass.*failure/);
        assert.match(result.body, /fresh full-suite coverage/);
    });
    it('retains the full-run missing-artifact warning', async () => {
        const result = await update({ COVERAGE_SUCCESS: 'false' });
        assert.match(result.body, /Coverage table could not be generated/);
        assert.doesNotMatch(result.body, /fresh full-suite coverage|stale full-suite coverage/);
    });
    it('uses the existing fork notification only to describe the actual affected-scope update', async () => {
        const result = await update({ TEST_SCOPE: 'affected', POST_AUTHOR_COMMENT: 'true' });
        assert.equal(result.comments.length, 1);
        assert.match(result.comments[0], /affected.*selection/i);
        assert.doesNotMatch(result.comments[0], /coverage has been automatically updated/);
    });
    it('does not add a missing coverage section or post an extra comment', async () => {
        const result = await update({ POST_AUTHOR_COMMENT: 'true' }, 'No coverage section here');
        assert.equal(result.body, undefined);
        assert.equal(result.comments.length, 0);
    });
});

function availabilityScript() {
    const workflow = readFileSync(join(__dirname, '../workflows/pullrequest-coverage-reporter.yml'), 'utf8');
    const step = workflow.split('      - name: Determine coverage scope from upstream run metadata')[1];
    assert.ok(step, 'Expected metadata-only scope step');
    const script = step.split('\n      - name:')[0].match(/^          script: (.+)$/m);
    assert.ok(script, 'Expected a trusted helper invocation');
    return script[1];
}
function summary(scope) {
    return {
        name: 'CI Summary',
        status: 'completed',
        steps: scope === undefined ? [] : [{ name: `Test validation scope: ${scope}`, status: 'completed', conclusion: 'success' }],
    };
}
const fullArtifacts = [
    { name: 'Server JaCoCo XML', expired: false },
    { name: 'Client Coverage Summaries', expired: false },
];
async function availability(artifacts, overrides = {}, conclusion = 'success', metadata = {}) {
    const output = {};
    const requests = [];
    const actions = { listWorkflowRunArtifacts() {}, listJobsForWorkflowRunAttempt() {} };
    const github = {
        paginate: async (endpoint, request) => {
            requests.push({ endpoint, ...request });
            if (endpoint === actions.listJobsForWorkflowRunAttempt) {
                if (metadata.jobsError) throw new Error('Jobs API unavailable');
                return Object.hasOwn(metadata, 'jobs') ? metadata.jobs : [summary()];
            }
            assert.equal(endpoint, actions.listWorkflowRunArtifacts, 'Only run metadata may be queried');
            if (metadata.artifactsError) throw new Error('Artifacts API unavailable');
            return artifacts;
        },
        rest: { actions },
    };
    process.env = { ...originalEnvironment, HAS_CLIENT: 'true', HAS_SERVER: 'true', ...overrides };
    const AsyncFunction = Object.getPrototypeOf(async () => {}).constructor;
    await new AsyncFunction('require', 'github', 'context', 'core', availabilityScript())(
        workflowRequire,
        github,
        {
            repo: { owner: 'fixture', repo: 'Artemis' },
            payload: { workflow_run: { id: metadata.runId ?? 7, run_attempt: metadata.runAttempt ?? 3, head_sha: 'same-sha', conclusion } },
        },
        {
            setOutput: (key, value) => {
                output[key] = value;
            },
            info() {},
            warning() {},
        },
    );
    return { ...output, requests, actions };
}
describe('fork reporter run scope', () => {
    it('reports intentionally deferred draft tests without requiring coverage artifacts', async () => {
        const result = await availability([], {}, 'success', { jobs: [summary('deferred')] });
        assert.equal(result.test_scope, 'deferred');
        assert.equal(result.requests.length, 1);
    });
    it('reports ready-PR affected tests even when they fail', async () => {
        const result = await availability([], {}, 'failure', { jobs: [summary('affected')] });
        assert.equal(result.test_scope, 'affected');
    });
    it('distinguishes draft and ready runs on the same SHA using their own metadata', async () => {
        const deferred = await availability([], {}, 'success', { runId: 7, jobs: [summary('deferred')] });
        const affected = await availability([], {}, 'success', { runId: 8, jobs: [summary('affected')] });
        assert.equal(deferred.test_scope, 'deferred');
        assert.equal(affected.test_scope, 'affected');
    });
    it('requests jobs for the exact upstream run attempt', async () => {
        const result = await availability([], {}, 'success', { runId: 19, runAttempt: 4, jobs: [summary('deferred')] });
        assert.deepEqual(result.requests, [
            { endpoint: result.actions.listJobsForWorkflowRunAttempt, owner: 'fixture', repo: 'Artemis', run_id: 19, attempt_number: 4, per_page: 100 },
        ]);
    });
    for (const scope of ['deferred', 'affected', 'unavailable']) {
        it(`does not upgrade explicit ${scope} scope when stale full-run artifacts exist`, async () => {
            const result = await availability(fullArtifacts, {}, 'success', { jobs: [summary(scope)] });
            assert.equal(result.test_scope, scope);
            assert.equal(result.requests.length, 1);
        });
    }
    it('keeps intentional affected scope when the artifact API is unavailable', async () => {
        const result = await availability([], {}, 'success', { jobs: [summary('affected')], artifactsError: true });
        assert.equal(result.test_scope, 'affected');
    });
    it('accepts a successful full-suite fallback only with complete artifacts', async () => {
        const result = await availability(fullArtifacts, {}, 'success', { jobs: [summary('full')] });
        assert.equal(result.test_scope, 'full');
    });
    it('requires full-scope artifacts even with an explicit successful full marker', async () => {
        const result = await availability([], {}, 'success', { jobs: [summary('full')] });
        assert.equal(result.test_scope, 'unavailable');
    });
    it('does not accept a full marker from a failed workflow', async () => {
        const result = await availability(fullArtifacts, {}, 'failure', { jobs: [summary('full')] });
        assert.equal(result.test_scope, 'unavailable');
    });
    it('treats absent artifacts as unavailable coverage for changed code', async () => {
        const result = await availability([]);
        assert.equal(result.test_scope, 'unavailable');
    });
    it('does not publish a mixed full/affected run as complete coverage', async () => {
        const result = await availability([{ name: 'Server JaCoCo XML', expired: false }]);
        assert.equal(result.test_scope, 'unavailable');
    });
    it('only allows the required artifacts for the changed areas', async () => {
        const result = await availability([{ name: 'Client Coverage Summaries', expired: false }], { HAS_SERVER: 'false' });
        assert.equal(result.test_scope, 'full');
    });
    it('treats a failed workflow as incomplete even when artifact names exist', async () => {
        const result = await availability(
            [
                { name: 'Server JaCoCo XML', expired: false },
                { name: 'Client Coverage Summaries', expired: false },
            ],
            {},
            'failure',
        );
        assert.equal(result.test_scope, 'unavailable');
    });
    it('requires non-expired artifacts for all changed areas', async () => {
        const result = await availability([
            { name: 'Server JaCoCo XML', expired: false },
            { name: 'Client Coverage Summaries', expired: true },
        ]);
        assert.equal(result.test_scope, 'unavailable');
    });
    it('does not treat missing expiration metadata as a valid full artifact', async () => {
        const result = await availability([{ name: 'Server JaCoCo XML' }, { name: 'Client Coverage Summaries' }]);
        assert.equal(result.test_scope, 'unavailable');
    });
    for (const scope of ['deferred', 'affected', 'full']) {
        for (const conclusion of ['failure', 'skipped']) {
            it(`rejects a ${conclusion} ${scope} marker despite full artifacts`, async () => {
                const job = summary(scope);
                job.steps[0].conclusion = conclusion;
                const result = await availability(fullArtifacts, {}, 'success', { jobs: [job] });
                assert.equal(result.test_scope, 'unavailable');
            });
        }
    }
    it('rejects a marker step that has not completed', async () => {
        const job = summary('affected');
        job.steps[0].status = 'in_progress';
        const result = await availability(fullArtifacts, {}, 'success', { jobs: [job] });
        assert.equal(result.test_scope, 'unavailable');
    });
    for (const scopes of [
        ['affected', 'affected'],
        ['affected', 'full'],
    ]) {
        it(`rejects duplicate scope markers (${scopes.join(', ')})`, async () => {
            const job = summary(scopes[0]);
            job.steps.push(summary(scopes[1]).steps[0]);
            const result = await availability(fullArtifacts, {}, 'success', { jobs: [job] });
            assert.equal(result.test_scope, 'unavailable');
        });
    }
    for (const invalid of ['other', 'full extra', 'full\n']) {
        it(`rejects invalid scope ${JSON.stringify(invalid)} instead of inferring full coverage`, async () => {
            const result = await availability(fullArtifacts, {}, 'success', { jobs: [summary(invalid)] });
            assert.equal(result.test_scope, 'unavailable');
        });
    }
    it('ignores scope-like steps in jobs other than the exact CI Summary job', async () => {
        const result = await availability(fullArtifacts, {}, 'success', {
            jobs: [summary(), { ...summary('affected'), name: 'Test / CI Summary' }],
        });
        assert.equal(result.test_scope, 'full');
    });
    for (const [reason, jobs] of [
        ['missing summary job', []],
        ['duplicate summary jobs', [summary('full'), summary('full')]],
        ['missing summary steps', [{ name: 'CI Summary' }]],
        ['malformed summary steps', [{ ...summary(), steps: [null] }]],
        ['incomplete summary job', [{ ...summary('full'), status: 'in_progress' }]],
        ['missing jobs list', null],
    ]) {
        it(`fails closed for ${reason}`, async () => {
            const result = await availability(fullArtifacts, {}, 'success', { jobs });
            assert.equal(result.test_scope, 'unavailable');
        });
    }
    it('fails closed when jobs metadata cannot be read', async () => {
        const result = await availability(fullArtifacts, {}, 'success', { jobsError: true });
        assert.equal(result.test_scope, 'unavailable');
    });
    it('fails closed when artifact metadata cannot be read for a full run', async () => {
        const result = await availability([], {}, 'success', { jobs: [summary('full')], artifactsError: true });
        assert.equal(result.test_scope, 'unavailable');
    });
    for (const metadata of [{ runId: 0 }, { runAttempt: 0 }, { runAttempt: '4' }]) {
        it(`requires an unambiguous upstream run attempt: ${JSON.stringify(metadata)}`, async () => {
            const result = await availability(fullArtifacts, {}, 'success', metadata);
            assert.equal(result.test_scope, 'unavailable');
            assert.equal(result.requests.length, 0);
        });
    }
    it('does not read or execute fork artifact payloads as scope metadata', async () => {
        let payloadRead = false;
        const artifact = {
            name: 'test-scope.js',
            expired: false,
            get contents() {
                payloadRead = true;
                return 'throw new Error("Fork artifact payload must never execute")';
            },
        };
        const result = await availability([artifact]);
        assert.equal(result.test_scope, 'unavailable');
        assert.equal(payloadRead, false);
    });
});
