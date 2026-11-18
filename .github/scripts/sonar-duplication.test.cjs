const { test } = require('node:test');
const assert = require('node:assert/strict');
const { checkPullRequestDuplication } = require('./sonar-duplication.js');

const sha = 'a'.repeat(40);
function fixture() {
    const pull = {
        number: 123,
        state: 'open',
        head: { sha, ref: 'chore/development/reduce-duplication' },
        base: { ref: 'develop', sha: 'b'.repeat(40) },
        changed_files: 2,
    };
    const analysis = {
        key: '123',
        commit: { sha },
        branch: pull.head.ref,
        target: 'develop',
        analysisDate: '2026-11-17T08:00:00+0000',
        status: { qualityGateStatus: 'OK' },
    };
    const measures = [
        { metric: 'new_duplicated_lines', periods: [{ index: 1, value: '0' }] },
        { metric: 'new_duplicated_blocks', periods: [{ index: 1, value: '0' }] },
        { metric: 'new_lines', periods: [{ index: 1, value: '1000' }] },
    ];
    const expectedBranch = pull.head.ref;
    const state = { pull, analyses: [analysis], measures, calls: [], files: [{ filename: '.github/workflows/ci-test.yml' }, { filename: 'README.md' }] };
    const github = {
        rest: { pulls: { get: async () => ({ data: structuredClone(state.pull) }), listFiles: Symbol('listFiles') } },
        paginate: async (method, options) => {
            assert.equal(method, github.rest.pulls.listFiles);
            assert.deepEqual(options, { owner: 'ls1intum', repo: 'Artemis', pull_number: 123, per_page: 100 });
            return structuredClone(state.files);
        },
    };
    const context = { repo: { owner: 'ls1intum', repo: 'Artemis' } };
    const fetchImpl = async (url, options) => {
        const parsed = new URL(url);
        assert.equal(parsed.origin, 'https://sonarcloud.io');
        assert.equal(options.cache, 'no-store');
        state.calls.push(parsed);
        if (state.httpError) return { ok: false, status: 503 };
        if (parsed.pathname === '/api/project_pull_requests/list') return { ok: true, json: async () => ({ pullRequests: structuredClone(state.analyses) }) };
        assert.equal(parsed.pathname, '/api/measures/component');
        assert.equal(parsed.searchParams.get('component'), 'ls1intum_Artemis');
        assert.equal(parsed.searchParams.get('pullRequest'), '123');
        const response = { component: { key: state.metricProject ?? 'ls1intum_Artemis', pullRequest: state.metricPull ?? '123', measures: structuredClone(state.measures) } };
        state.afterMetrics?.();
        return { ok: true, json: async () => response };
    };
    return {
        state,
        run: (options = {}) => checkPullRequestDuplication({ github, context, sha, pullRequest: 123, branch: expectedBranch, base: 'develop', fetchImpl, ...options }),
    };
}

test('accepts zero duplicated lines from the current PR analysis and reports absolute counts', async () => {
    const f = fixture();
    assert.deepEqual(await f.run(), { duplicatedLines: 0, duplicatedBlocks: 0, newLines: 1000 });
    assert.equal(f.state.calls.filter((url) => url.pathname.endsWith('/list')).length, 2);
});

test('rejects one duplicated line even when its density would round to 0.0 percent', async () => {
    const f = fixture();
    f.state.measures[0].periods[0].value = '1';
    f.state.measures[1].periods[0].value = '1';
    f.state.measures[2].periods[0].value = '1000000';
    await assert.rejects(f.run(), /1 newly duplicated line.*1 duplicated block/);
});

test('accepts an analysis with no new lines', async () => {
    const f = fixture();
    f.state.measures[2].periods[0].value = '0';
    assert.equal((await f.run()).newLines, 0);
});

test('accepts empty Sonar measures when the complete PR diff is outside the analyzed source roots', async () => {
    const f = fixture();
    f.state.measures = [];
    assert.deepEqual(await f.run(), { duplicatedLines: 0, duplicatedBlocks: 0, newLines: 0 });
});

test('rejects empty Sonar measures when an analyzed source file changed', async () => {
    const f = fixture();
    f.state.measures = [];
    f.state.files[0].filename = 'packages/tum-aet-ui/src/lib/button.ts';
    await assert.rejects(f.run(), /unavailable Sonar metric/);
});

test('rejects empty Sonar measures when the GitHub file list is incomplete', async () => {
    const f = fixture();
    f.state.measures = [];
    f.state.pull.changed_files = 3;
    await assert.rejects(f.run(), /unavailable Sonar metric/);
});

test('rejects empty Sonar measures when the PR base advances during verification', async () => {
    const f = fixture();
    f.state.measures = [];
    f.state.afterMetrics = () => {
        f.state.pull.base.sha = 'c'.repeat(40);
    };
    await assert.rejects(f.run(), /PR comparison changed/);
});

test('reads the single-period API representation', async () => {
    const f = fixture();
    f.state.measures = f.state.measures.map((measure) => ({ metric: measure.metric, period: { value: measure.periods[0].value } }));
    assert.equal((await f.run()).duplicatedLines, 0);
});

for (const value of [undefined, '', '-1', '0.01', 'NaN', 'Infinity', '9007199254740992']) {
    test(`rejects an invalid duplicated-line count: ${String(value)}`, async () => {
        const f = fixture();
        f.state.measures[0].periods[0].value = value;
        await assert.rejects(f.run(), /Invalid.*new_duplicated_lines/);
    });
}

for (const metric of ['new_duplicated_lines', 'new_duplicated_blocks', 'new_lines']) {
    test(`fails when ${metric} is unavailable`, async () => {
        const f = fixture();
        f.state.measures = f.state.measures.filter((measure) => measure.metric !== metric);
        await assert.rejects(f.run(), new RegExp('Invalid.*' + metric));
    });
    test(`fails when ${metric} is ambiguous`, async () => {
        const f = fixture();
        f.state.measures.push(structuredClone(f.state.measures.find((measure) => measure.metric === metric)));
        await assert.rejects(f.run(), new RegExp('Invalid.*' + metric));
    });
}

for (const property of ['sha', 'branch', 'base', 'state']) {
    test(`rejects a GitHub PR with mismatched ${property}`, async () => {
        const f = fixture();
        if (property === 'sha') f.state.pull.head.sha = 'b'.repeat(40);
        else if (property === 'branch') f.state.pull.head.ref = 'chore/other';
        else if (property === 'base') f.state.pull.base.ref = 'main';
        else f.state.pull.state = 'closed';
        await assert.rejects(f.run(), /current open PR head and target/);
        assert.equal(f.state.calls.length, 0);
    });
}

for (const property of ['sha', 'branch', 'target', 'qualityGateStatus']) {
    test(`rejects Sonar evidence with mismatched ${property}`, async () => {
        const f = fixture();
        const analysis = f.state.analyses[0];
        if (property === 'sha') analysis.commit.sha = 'b'.repeat(40);
        else if (property === 'qualityGateStatus') analysis.status.qualityGateStatus = 'ERROR';
        else analysis[property] = 'other';
        await assert.rejects(f.run(), /exact PR head and target/);
    });
}

test('fails when no analysis is available', async () => {
    const f = fixture();
    f.state.analyses = [];
    await assert.rejects(f.run(), /exact PR head and target/);
});

test('fails on a Sonar API outage', async () => {
    const f = fixture();
    f.state.httpError = true;
    await assert.rejects(f.run(), /HTTP 503/);
});

test('rejects a PR superseded while metrics are being read', async () => {
    const f = fixture();
    f.state.afterMetrics = () => {
        f.state.pull.head.sha = 'b'.repeat(40);
    };
    await assert.rejects(f.run(), /current open PR head and target/);
});

test('rejects a Sonar analysis replaced while metrics are being read', async () => {
    const f = fixture();
    f.state.afterMetrics = () => {
        f.state.analyses[0].analysisDate = '2026-11-17T08:01:00+0000';
    };
    await assert.rejects(f.run(), /changed while reading duplication/);
});

for (const pullRequest of [0, -1, '1/../../other', '1.5', Number.NaN]) {
    test(`rejects an invalid PR identifier: ${String(pullRequest)}`, async () => {
        const f = fixture();
        await assert.rejects(f.run({ pullRequest }), /positive pull request number/);
        assert.equal(f.state.calls.length, 0);
    });
}

test('rejects a missing full source revision', async () => {
    const f = fixture();
    await assert.rejects(f.run({ sha: 'short' }), /full source commit SHA/);
});

const fs = require('node:fs');
const path = require('node:path');
function guardStep() {
    const workflow = fs.readFileSync(path.join(__dirname, '../workflows/ci-sonar.yml'), 'utf8');
    const start = workflow.indexOf('      - name: Enforce zero newly duplicated lines');
    assert.ok(start >= 0, 'The existing Sonar job must enforce the duplication guard');
    return workflow.slice(start);
}

test('the workflow enforces PR duplication for scanned and secretless-verification paths, without gating the existing default-branch backlog', () => {
    const condition = guardStep().match(/^        if: (.+)$/m)?.[1];
    // The PR-only condition must not depend on whether the source checkout/scan ran.
    assert.equal(condition, "steps.analysis.outputs.pull_request != '0'");
});

test('the workflow verifies with trusted tools and publishes the absolute counts in the job summary', async () => {
    const body = guardStep().match(/          script: \|\n((?:            [^\n]*\n|\n)+)/)?.[1];
    assert.ok(body);
    const calls = [];
    const f = fixture();
    f.state.measures[2].periods[0].value = '456';
    const summary = {
        addHeading() {
            return this;
        },
        addTable(rows) {
            calls.push(['summary', rows]);
            return this;
        },
        async write() {},
    };
    const run = new (Object.getPrototypeOf(async function () {}).constructor)('github', 'context', 'core', 'process', 'require', body.replace(/^ {12}/gm, ''));
    await run({}, {}, { summary }, { env: { SOURCE_SHA: sha, PULL_REQUEST: '123', SOURCE_BRANCH: 'chore/development/reduce-duplication', TARGET_BRANCH: 'develop' } }, (module) => {
        assert.equal(module, './.sonar-tools/.github/scripts/sonar-duplication.js');
        return {
            checkPullRequestDuplication: async (options) => {
                assert.equal(options.sha, sha);
                assert.equal(options.pullRequest, '123');
                assert.equal(options.branch, 'chore/development/reduce-duplication');
                assert.equal(options.base, 'develop');
                calls.push(['verified']);
                assert.equal(options.summary, summary);
                return f.run({ summary: options.summary });
            },
        };
    });
    assert.equal(calls[0][0], 'verified');
    assert.deepEqual(calls[1][1].slice(1), [
        ['New duplicated lines', '0'],
        ['New duplicated blocks', '0'],
        ['New lines', '456'],
    ]);
});

for (const property of ['metricProject', 'metricPull']) {
    test(`rejects measures returned for a different ${property}`, async () => {
        const f = fixture();
        f.state[property] = 'other';
        await assert.rejects(f.run(), /Invalid Sonar duplication response/);
    });
}

test('publishes verified absolute counts before failing a PR with duplication', async () => {
    const f = fixture();
    f.state.measures[0].periods[0].value = '2';
    f.state.measures[1].periods[0].value = '1';
    let rows;
    const summary = {
        addHeading() {
            return this;
        },
        addTable(value) {
            rows = value;
            return this;
        },
        async write() {},
    };
    await assert.rejects(f.run({ summary }), /2 newly duplicated line/);
    assert.deepEqual(rows?.slice(1), [
        ['New duplicated lines', '2'],
        ['New duplicated blocks', '1'],
        ['New lines', '1000'],
    ]);
});

for (const scan of ['true', 'false']) {
    test(`the guard receives the exact source SHA when scan=${scan}`, () => {
        const expression = guardStep().match(/^          SOURCE_SHA: \$\{\{ (.+) \}\}$/m)?.[1];
        assert.ok(expression);
        const steps = { analysis: { outputs: { sha, scan } }, source: { outputs: scan === 'true' ? { sha } : {} } };
        assert.equal(expression, 'steps.analysis.outputs.sha');
        // Resolve this data path without evaluating workflow expressions as JavaScript.
        const value = expression.split('.').reduce((current, key) => current[key], { steps });
        assert.equal(value, sha);
    });
}
