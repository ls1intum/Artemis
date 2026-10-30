const { afterEach, describe, it } = require('node:test');
const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const { join } = require('node:path');
const updateCoverage = require('./update-pr-coverage.js');

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
    const step = workflow.split('      - name: Determine coverage availability from run artifacts')[1];
    assert.ok(step, 'Expected metadata-only availability step');
    const lines = step.split('\n');
    const start = lines.findIndex((line) => line === '          script: |');
    assert.notEqual(start, -1);
    const script = [];
    for (const line of lines.slice(start + 1)) {
        if (line.trim() && !line.startsWith('            ')) break;
        script.push(line.slice(12));
    }
    return script.join('\n');
}
async function availability(artifacts, overrides = {}, conclusion = 'success') {
    const output = {};
    const AsyncFunction = Object.getPrototypeOf(async () => {}).constructor;
    await new AsyncFunction('github', 'context', 'core', 'process', availabilityScript())(
        { paginate: async () => artifacts, rest: { actions: { listWorkflowRunArtifacts() {} } } },
        { repo: { owner: 'fixture', repo: 'Artemis' }, payload: { workflow_run: { id: 7, conclusion } } },
        {
            setOutput: (key, value) => {
                output[key] = value;
            },
            info() {},
            warning() {},
        },
        { env: { HAS_CLIENT: 'true', HAS_SERVER: 'true', ...overrides } },
    );
    return output;
}
describe('fork reporter artifact scope', () => {
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
});
