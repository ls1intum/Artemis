const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { execFileSync } = require('node:child_process');
const workflow = fs.readFileSync(path.join(__dirname, '../workflows/ci-sonar.yml'), 'utf8');

function coverageScript() {
    const start = workflow.indexOf('      - name: Find the CI run that produced coverage for this commit');
    const block = workflow.slice(start).match(/          script: \|\n((?:            [^\n]*\n|\n)+)/);
    assert.ok(block, 'Coverage lookup must use the tested GitHub API script');
    return block[1].replace(/^ {12}/gm, '');
}

function fixture() {
    const outputs = {};
    const warnings = [];
    const calls = [];
    const state = {
        runs: [{ id: 12, head_sha: 'source-sha', status: 'completed', conclusion: 'failure' }],
        artifacts: [
            { name: 'Server JaCoCo XML', expired: false },
            { name: 'Vitest Coverage Report', expired: false },
        ],
        jobs: [
            { name: 'Test / Client Tests', conclusion: 'success' },
            { name: 'Test / Server Tests Report', conclusion: 'success' },
            ...Array.from({ length: 7 }, (_, i) => ({ name: `Test / Server Tests (shard-${i})`, conclusion: 'success' })),
            { name: 'Quality / Server Code Style', conclusion: 'failure' },
        ],
    };
    const github = {
        paginate: async (route, options) => {
            calls.push({ route, options });
            if (route.includes('/workflows/')) return state.runs;
            if (state.metadataError) throw new Error('API unavailable');
            return route.endsWith('/artifacts') ? state.artifacts : state.jobs;
        },
    };
    const core = {
        setOutput: (key, value) => {
            outputs[key] = value;
        },
        warning: (value) => warnings.push(value),
        info: () => {},
    };
    const context = { repo: { owner: 'example', repo: 'project' } };
    return {
        state,
        outputs,
        warnings,
        calls,
        run: () =>
            new (Object.getPrototypeOf(async function () {}).constructor)('github', 'context', 'core', 'process', coverageScript())(github, context, core, {
                env: { SHA: 'source-sha' },
            }),
    };
}

test('imports exact-revision complete test coverage despite unrelated CI failures', async () => {
    const f = fixture();
    await f.run();
    assert.equal(f.outputs.run_id, '12');
    assert.equal(f.calls[0].options.head_sha, 'source-sha');
    assert.equal(f.calls[0].options.status, 'completed');
});
for (const property of ['head_sha', 'status']) {
    test(`rejects a candidate with a mismatched ${property}`, async () => {
        const f = fixture();
        f.state.runs[0][property] = 'different';
        await f.run();
        assert.equal(f.outputs.run_id, undefined);
        assert.equal(f.calls.length, 1);
    });
}
for (const name of ['Server JaCoCo XML', 'Vitest Coverage Report']) {
    for (const failure of ['missing', 'expired']) {
        test(`does not import ${failure} ${name}`, async () => {
            const f = fixture();
            if (failure === 'missing') f.state.artifacts = f.state.artifacts.filter((a) => a.name !== name);
            else f.state.artifacts.find((a) => a.name === name).expired = true;
            await f.run();
            assert.equal(f.outputs.run_id, undefined);
        });
    }
}
for (const name of ['Test / Client Tests', 'Test / Server Tests Report', 'Test / Server Tests (shard-0)']) {
    test(`rejects failed ${name}`, async () => {
        const f = fixture();
        f.state.jobs.find((job) => job.name === name).conclusion = 'failure';
        await f.run();
        assert.equal(f.outputs.run_id, undefined);
    });
}
for (const conclusion of ['skipped', 'cancelled', null]) {
    test(`rejects a ${conclusion} server shard even if its sibling jobs passed`, async () => {
        const f = fixture();
        f.state.jobs.find((job) => job.name.includes('shard-0')).conclusion = conclusion;
        await f.run();
        assert.equal(f.outputs.run_id, undefined);
    });
}
test('rejects selected or incomplete server matrices even when coverage artifacts exist', async () => {
    const f = fixture();
    f.state.jobs = f.state.jobs.filter((job) => !job.name.includes('shard-'));
    f.state.jobs.push({ name: 'Test / Server Tests (PostgreSQL)', conclusion: 'success' });
    await f.run();
    assert.equal(f.outputs.run_id, undefined);
});
test('missing job metadata never establishes complete coverage', async () => {
    const f = fixture();
    f.state.jobs = [];
    await f.run();
    assert.equal(f.outputs.run_id, undefined);
});
test('API errors preserve analysis without importing uncertain coverage', async () => {
    const f = fixture();
    f.state.metadataError = true;
    await f.run();
    assert.equal(f.outputs.run_id, undefined);
    assert.ok(f.warnings.length);
});
test('a bad candidate does not hide older valid coverage for the same source SHA', async () => {
    const f = fixture();
    f.state.runs.unshift({ id: 13, head_sha: 'old-sha', status: 'completed' });
    await f.run();
    assert.equal(f.outputs.run_id, '12');
});
test('scanner config reaches the separate scanner JVM and manual failures remain visible', () => {
    assert.match(workflow, /SONAR_SCANNER_JAVA_OPTS: '-Xmx6g'/);
    assert.match(workflow, /continue-on-error: \$\{\{ github.event_name != 'workflow_dispatch' \}\}/);
    assert.match(workflow, /"-Dsonar.scm.revision=\$\{SONAR_REVISION\}"/);
});
test('default-branch recovery is opt-in and checks out the actual default branch', () => {
    assert.match(workflow, /analyze_default_branch:\n\s+description:[^\n]+\n\s+type: boolean\n\s+default: false/);
    assert.match(
        workflow,
        /ref: \$\{\{ github.event_name == 'workflow_dispatch' && inputs.analyze_default_branch && github.event.repository.default_branch \|\| inputs.commit_sha \|\| github.sha \}\}/,
    );
    assert.match(workflow, /SHA: \$\{\{ steps.source.outputs.sha \}\}/);
});
test('source identity is read from checked-out Git rather than the workflow ref', () => {
    const block = workflow.match(/      - name: Record analyzed source revision[\s\S]*?        run: \|\n((?:          [^\n]*\n|\n)+)/);
    assert.ok(block, 'Source identity step required');
    const directory = fs.mkdtempSync('/tmp/sonar-source-');
    const output = path.join(directory, 'output');
    const summary = path.join(directory, 'summary');
    try {
        execFileSync('bash', ['-c', block[1].replace(/^ {10}/gm, '')], {
            env: { ...process.env, GITHUB_OUTPUT: output, GITHUB_STEP_SUMMARY: summary, SONAR_BRANCH_NAME: 'develop' },
        });
        const sha = execFileSync('git', ['rev-parse', 'HEAD'], { encoding: 'utf8' }).trim();
        assert.equal(fs.readFileSync(output, 'utf8'), `sha=${sha}\n`);
        assert.match(fs.readFileSync(summary, 'utf8'), new RegExp(`develop.*${sha}`));
    } finally {
        fs.rmSync(directory, { recursive: true, force: true });
    }
});
