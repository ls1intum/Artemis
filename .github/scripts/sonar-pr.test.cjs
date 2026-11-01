const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const workflow = fs.readFileSync(path.join(__dirname, '../workflows/ci.yml'), 'utf8');
function job(name) {
    const rest = workflow.slice(workflow.indexOf('\n  ' + name + ':\n') + 1);
    const end = rest.slice(1).search(/^  [a-z][a-z-]+:\n/m);
    return end < 0 ? rest : rest.slice(0, end + 1);
}
function shouldAnalyze(event, { draft = false, current = 'true', relevant = 'true', result = 'success' } = {}) {
    const body = job('sonar');
    const match = body.match(/    if: >-\n((?:      [^\n]*\n)+)/) ?? body.match(/    if: (.+)\n/);
    const condition = match[1]
        .trim()
        .replace(/^\$\{\{\s*|\s*\}\}$/g, '')
        .replaceAll('needs.detect-changes', 'needs.detect_changes');
    return new Function('github', 'needs', 'cancelled', 'return (' + condition + ')')(
        { event_name: event, ref: 'refs/heads/develop', event: { pull_request: { draft } } },
        { test: { result }, detect_changes: { outputs: { current, tests_deferred: String(draft), sonar_relevant: relevant } } },
        () => false,
    );
}
test('a ready production PR runs Sonar before merging', () => assert.equal(shouldAnalyze('pull_request'), true));
test('a relevant PR still analyzes when ordinary test suites are irrelevant', () => assert.equal(shouldAnalyze('pull_request', { result: 'skipped' }), true));
test('Sonar failure is included in the real required aggregate', () => assert.match(job('all-required-ci-passed'), /^      - sonar$/m));

const { prepareAnalysis, verifyFork, findPrCoverage } = require('./sonar-pr.js');
const sourceSha = '6817281a4c6a593bf43ca6613ee10b735d462563';
const otherSha = 'fd5d65d4c7d0812e5223623acc0c3c1aefc297e4';
const repo = { owner: 'ls1intum', repo: 'Artemis' };

function analysisFixture({ eventName = 'pull_request', fork = false } = {}) {
    const pull = {
        number: 13999,
        state: 'open',
        draft: false,
        head: { sha: sourceSha, ref: 'feature/source-branch', repo: { full_name: fork ? 'contributor/Artemis' : 'ls1intum/Artemis' } },
        base: { sha: otherSha, ref: 'develop', repo: { full_name: 'ls1intum/Artemis' } },
    };
    const state = { pull, promotionSha: sourceSha, defaultSha: otherSha };
    const context = {
        eventName,
        repo,
        sha: eventName === 'pull_request' ? otherSha : sourceSha,
        ref: eventName === 'pull_request' ? 'refs/pull/13999/merge' : 'refs/heads/workflow-repair',
        payload: { pull_request: structuredClone(pull), repository: { default_branch: 'develop' } },
    };
    const outputs = {};
    const calls = [];
    const github = {
        rest: {
            pulls: {
                get: async (options) => {
                    calls.push(['pull', options]);
                    assert.deepEqual(options, { ...repo, pull_number: 13999 });
                    if (state.pullError) throw state.pullError;
                    return { data: structuredClone(state.pull) };
                },
            },
            git: {
                getRef: async (options) => {
                    calls.push(['promotion', options]);
                    assert.equal(eventName, 'workflow_dispatch', 'PR events must never resolve or execute a promotion branch');
                    assert.deepEqual(options, { ...repo, ref: 'heads/chore/sonar/pr-13999' });
                    if (state.promotionError) throw state.promotionError;
                    return { data: { ref: 'refs/heads/chore/sonar/pr-13999', object: { type: 'commit', sha: state.promotionSha } } };
                },
            },
            repos: {
                getBranch: async (options) => {
                    calls.push(['default', options]);
                    assert.deepEqual(options, { ...repo, branch: 'develop' });
                    return { data: { name: 'develop', commit: { sha: state.defaultSha }, protected: true } };
                },
            },
        },
    };
    const core = {
        setOutput: (key, value) => {
            outputs[key] = value;
        },
        notice: () => {},
    };
    const prepare = (options = {}) => prepareAnalysis({ github, context, core, ...options });
    return { state, context, outputs, calls, github, prepare };
}

function sonarEntry() {
    return {
        key: '13999',
        title: '`Development`: Improve analysis',
        branch: 'feature/source-branch',
        // Sonar's project baseline can differ from the actual PR target for stacked PRs.
        base: 'main',
        target: 'develop',
        status: { qualityGateStatus: 'OK', bugs: 0, vulnerabilities: 0, codeSmells: 2 },
        analysisDate: '2026-10-16T22:30:20+0000',
        url: 'https://github.com/ls1intum/Artemis/pull/13999',
        commit: { sha: sourceSha, author: { name: 'Contributor' }, date: '2026-10-16T21:53:28+0000', message: 'Improve analysis' },
        contributors: [],
    };
}

function sonarFetch(data = { pullRequests: [sonarEntry()] }) {
    return async (url, options) => {
        assert.equal(url, 'https://sonarcloud.io/api/project_pull_requests/list?project=ls1intum_Artemis');
        assert.equal(options.cache, 'no-store');
        assert.equal(options.signal instanceof AbortSignal, true);
        assert.equal(options.headers?.Authorization, undefined, 'Public fork verification must not use a secret');
        return { ok: true, status: 200, json: async () => structuredClone(data) };
    };
}

test('Sonar policy: an internal PR scans the current head rather than the event merge commit', async () => {
    const f = analysisFixture();
    const result = await f.prepare({ requestedSha: sourceSha, fetchImpl: async () => assert.fail('Internal scans must not reuse public fork results') });
    assert.deepEqual(result, { sha: sourceSha, scan: true, pullNumber: 13999, branch: 'feature/source-branch', base: 'develop' });
    assert.deepEqual(f.outputs, { sha: sourceSha, scan: 'true', pull_request: '13999', branch: 'feature/source-branch', base: 'develop' });
});

test('Sonar policy: a PR without an explicit SHA uses the event head and validates it against GitHub', async () => {
    const f = analysisFixture();
    assert.equal((await f.prepare()).sha, sourceSha);
});

for (const [name, change] of [
    [
        'superseded head',
        (f) => {
            f.state.pull.head.sha = otherSha;
        },
    ],
    [
        'closed PR',
        (f) => {
            f.state.pull.state = 'closed';
        },
    ],
    [
        'retargeted PR',
        (f) => {
            f.state.pull.base.ref = 'main';
        },
    ],
    [
        'missing source branch',
        (f) => {
            delete f.state.pull.head.ref;
        },
    ],
    [
        'missing target branch',
        (f) => {
            delete f.state.pull.base.ref;
        },
    ],
    [
        'malformed source SHA',
        (f) => {
            f.state.pull.head.sha = 'short-sha';
        },
    ],
]) {
    test(`Sonar policy: rejects a ${name} before publishing scan outputs`, async () => {
        const f = analysisFixture();
        change(f);
        await assert.rejects(f.prepare({ requestedSha: sourceSha }));
        assert.deepEqual(f.outputs, {});
    });
}

test('Sonar policy: GitHub lookup errors do not establish an internal PR source', async () => {
    const f = analysisFixture();
    f.state.pullError = new Error('GitHub unavailable');
    await assert.rejects(f.prepare({ requestedSha: sourceSha }), /GitHub unavailable/);
    assert.deepEqual(f.outputs, {});
});

for (const value of ['-1', '1.5', 'NaN', '9007199254740992', '13999; echo unsafe']) {
    test(`Sonar policy: rejects malformed manual PR number ${JSON.stringify(value)} before any lookup`, async () => {
        const f = analysisFixture({ eventName: 'workflow_dispatch' });
        await assert.rejects(f.prepare({ pullRequest: value }), /pull request number/);
        assert.deepEqual(f.calls, []);
    });
}

test('Sonar policy: a matching fork result disables scanning without resolving or executing fork code', async () => {
    const f = analysisFixture({ fork: true });
    const result = await f.prepare({ requestedSha: sourceSha, fetchImpl: sonarFetch() });
    assert.deepEqual(result, { sha: sourceSha, scan: false, pullNumber: 13999, branch: 'feature/source-branch', base: 'develop' });
    assert.equal(f.outputs.scan, 'false');
    assert.deepEqual(
        f.calls.map(([name]) => name),
        ['pull'],
    );
});

test('Sonar policy: a fork without verified evidence cannot publish scan outputs', async () => {
    const f = analysisFixture({ fork: true });
    await assert.rejects(f.prepare({ requestedSha: sourceSha, fetchImpl: sonarFetch({ pullRequests: [] }) }), /maintainer/);
    assert.deepEqual(f.outputs, {});
    assert.deepEqual(
        f.calls.map(([name]) => name),
        ['pull'],
    );
});

test('Sonar policy: public verification uses the analyzed target rather than the project baseline', async () => {
    const f = analysisFixture({ fork: true });
    await verifyFork({ pull: f.state.pull, fetchImpl: sonarFetch() });
    const entry = sonarEntry();
    entry.base = 'develop';
    entry.target = 'main';
    await assert.rejects(verifyFork({ pull: f.state.pull, fetchImpl: sonarFetch({ pullRequests: [entry] }) }));
});

for (const [name, change] of [
    [
        'different PR key',
        (entry) => {
            entry.key = '14000';
        },
    ],
    [
        'previous analyzed commit',
        (entry) => {
            entry.commit.sha = otherSha;
        },
    ],
    [
        'missing analyzed commit',
        (entry) => {
            delete entry.commit;
        },
    ],
    [
        'different source branch',
        (entry) => {
            entry.branch = 'feature/other';
        },
    ],
    [
        'different target branch',
        (entry) => {
            entry.target = 'main';
        },
    ],
    [
        'missing target branch',
        (entry) => {
            delete entry.target;
        },
    ],
    [
        'failed quality gate',
        (entry) => {
            entry.status.qualityGateStatus = 'ERROR';
        },
    ],
    [
        'uncomputed quality gate',
        (entry) => {
            entry.status.qualityGateStatus = 'NONE';
        },
    ],
    [
        'missing quality gate',
        (entry) => {
            delete entry.status;
        },
    ],
    [
        'missing analysis date',
        (entry) => {
            delete entry.analysisDate;
        },
    ],
    [
        'unparseable analysis date',
        (entry) => {
            entry.analysisDate = 'not-a-date';
        },
    ],
    [
        'empty analysis date',
        (entry) => {
            entry.analysisDate = '';
        },
    ],
    [
        'numeric analysis date',
        (entry) => {
            entry.analysisDate = 0;
        },
    ],
]) {
    test(`Sonar policy: fork verification rejects ${name}`, async () => {
        const f = analysisFixture({ fork: true });
        const entry = sonarEntry();
        change(entry);
        await assert.rejects(verifyFork({ pull: f.state.pull, fetchImpl: sonarFetch({ pullRequests: [entry] }) }));
    });
}

for (const [name, data] of [
    ['missing PR list', {}],
    ['non-array PR list', { pullRequests: {} }],
    ['no matching analysis', { pullRequests: [] }],
    ['duplicate matching analyses', { pullRequests: [sonarEntry(), sonarEntry()] }],
    ['malformed analysis record', { pullRequests: [null] }],
    ['null response', null],
]) {
    test(`Sonar policy: fork verification fails closed on ${name}`, async () => {
        const f = analysisFixture({ fork: true });
        await assert.rejects(verifyFork({ pull: f.state.pull, fetchImpl: sonarFetch(data) }));
    });
}

for (const [name, fetchImpl] of [
    ['HTTP failure', async () => ({ ok: false, status: 503 })],
    [
        'network failure',
        async () => {
            throw new Error('Network unavailable');
        },
    ],
    [
        'invalid JSON',
        async () => ({
            ok: true,
            json: async () => {
                throw new SyntaxError('Invalid JSON');
            },
        }),
    ],
]) {
    test(`Sonar policy: fork verification fails closed on ${name}`, async () => {
        const f = analysisFixture({ fork: true });
        await assert.rejects(verifyFork({ pull: f.state.pull, fetchImpl }));
    });
}

test('Sonar policy: manual PR analysis scans only the exact maintainer promotion under the original fork identity', async () => {
    const f = analysisFixture({ eventName: 'workflow_dispatch', fork: true });
    f.context.sha = otherSha;
    const result = await f.prepare({ pullRequest: '13999', fetchImpl: async () => assert.fail('A promoted scan must produce fresh evidence') });
    assert.deepEqual(result, { sha: sourceSha, scan: true, pullNumber: 13999, branch: 'feature/source-branch', base: 'develop' });
    assert.deepEqual(
        f.calls.map(([name]) => name),
        ['pull', 'promotion'],
    );
});

test('Sonar policy: manual PR analysis rejects a promotion pointing at an earlier fork head', async () => {
    const f = analysisFixture({ eventName: 'workflow_dispatch', fork: true });
    f.state.promotionSha = otherSha;
    await assert.rejects(f.prepare({ pullRequest: 13999 }), /promotion branch/);
    assert.deepEqual(f.outputs, {});
});

test('Sonar policy: a missing internal promotion does not fall back to checking out the fork', async () => {
    const f = analysisFixture({ eventName: 'workflow_dispatch', fork: true });
    f.state.promotionError = new Error('Not Found');
    await assert.rejects(f.prepare({ pullRequest: 13999 }), /Not Found/);
    assert.deepEqual(f.outputs, {});
});

test('Sonar policy: PR analysis and default-branch recovery cannot be combined', async () => {
    const f = analysisFixture({ eventName: 'workflow_dispatch', fork: true });
    await assert.rejects(f.prepare({ pullRequest: 13999, defaultBranch: true }), /cannot be combined/);
    assert.deepEqual(f.calls, []);
});

test('Sonar policy: recovery selects the actual default branch and records its exact commit', async () => {
    const f = analysisFixture({ eventName: 'workflow_dispatch' });
    const result = await f.prepare({ defaultBranch: true, requestedSha: sourceSha });
    assert.deepEqual(result, { sha: otherSha, scan: true, pullNumber: 0, branch: 'develop', base: '' });
    assert.deepEqual(
        f.calls.map(([name]) => name),
        ['default'],
    );
    assert.equal(f.outputs.sha, otherSha);
});

test('Sonar policy: a standalone branch scan preserves its dispatch source identity', async () => {
    const f = analysisFixture({ eventName: 'workflow_dispatch' });
    const result = await f.prepare();
    assert.deepEqual(result, { sha: sourceSha, scan: true, pullNumber: 0, branch: 'workflow-repair', base: '' });
    assert.deepEqual(f.calls, []);
});

test('Sonar policy: an invalid default-branch commit cannot produce analysis outputs', async () => {
    const f = analysisFixture({ eventName: 'workflow_dispatch' });
    f.state.defaultSha = 'short-sha';
    await assert.rejects(f.prepare({ defaultBranch: true }), /full source commit SHA/);
    assert.deepEqual(f.outputs, {});
});

function coverageFixture() {
    const actions = {
        listWorkflowRuns: () => {},
        listJobsForWorkflowRun: () => {},
        listWorkflowRunArtifacts: () => {},
    };
    const state = {
        runs: [{ id: 52, head_sha: sourceSha, status: 'completed', conclusion: 'failure', event: 'pull_request' }],
        jobs: [
            { name: 'Test / Client Tests', conclusion: 'success' },
            { name: 'Quality / Server Code Style', conclusion: 'failure' },
        ],
        artifacts: [{ id: 91, name: 'Sonar Client Coverage', expired: false, size_in_bytes: 2048 }],
    };
    const calls = [];
    const github = {
        rest: { actions },
        paginate: async (route, options) => {
            assert.equal(options.owner, 'ls1intum');
            assert.equal(options.repo, 'Artemis');
            assert.equal(options.per_page, 100);
            if (route === actions.listWorkflowRuns) {
                calls.push(['runs', options]);
                assert.equal(options.workflow_id, 'ci.yml');
                assert.equal(options.head_sha, sourceSha);
                if (state.runsError) throw state.runsError;
                return structuredClone(state.runs);
            }
            assert.equal(
                state.runs.some((run) => run.id === options.run_id),
                true,
            );
            if (route === actions.listJobsForWorkflowRun) {
                calls.push(['jobs', options]);
                assert.equal(options.filter, 'latest');
                if (state.jobsError) throw state.jobsError;
                return structuredClone(state.jobsByRun?.[options.run_id] ?? state.jobs);
            }
            assert.equal(route, actions.listWorkflowRunArtifacts);
            calls.push(['artifacts', options]);
            if (state.artifactsError) throw state.artifactsError;
            return structuredClone(state.artifacts);
        },
    };
    return { state, calls, find: (sha = sourceSha) => findPrCoverage({ github, context: { repo }, sha }) };
}

test('Sonar coverage: imports successful exact-source affected client coverage despite unrelated CI failure', async () => {
    const f = coverageFixture();
    assert.equal(await f.find(), '52');
});

test('Sonar coverage: imports a selected server execution artifact without requiring client coverage', async () => {
    const f = coverageFixture();
    f.state.jobs = [
        { name: 'Test / Plan Server Test Shards', conclusion: 'success' },
        { name: 'Test / Server Tests (PostgreSQL)', conclusion: 'success' },
        { name: 'Test / Server Tests Report', conclusion: 'success' },
        { name: 'Test / Client Tests', conclusion: 'skipped' },
    ];
    f.state.artifacts[0].name = 'Sonar Server Coverage';
    assert.equal(await f.find(), '52');
});

for (const artifactName of ['Server JaCoCo XML', 'Vitest Coverage Report']) {
    test(`Sonar coverage: accepts the complete-suite ${artifactName} artifact from successful producers`, async () => {
        const f = coverageFixture();
        f.state.jobs = [
            { name: 'Test / Client Tests', conclusion: 'success' },
            { name: 'Test / Server Tests Report', conclusion: 'success' },
            ...['LocalCILocalVC', 'LocalCILocalVCBatch', 'JenkinsBatch', 'JenkinsLocalVC', 'Independent', 'IndependentBatch', 'Remaining'].map((name) => ({
                name: `Test / Server Tests (${name})`,
                conclusion: 'success',
            })),
        ];
        f.state.artifacts[0].name = artifactName;
        assert.equal(await f.find(), '52');
    });
}

test('Sonar coverage: a foreign revision is rejected before its artifacts are inspected', async () => {
    const f = coverageFixture();
    f.state.runs[0].head_sha = otherSha;
    await assert.rejects(f.find(), /exact-source/);
    assert.deepEqual(
        f.calls.map(([name]) => name),
        ['runs'],
    );
});

test('Sonar coverage: a foreign latest run cannot hide valid coverage for the exact source', async () => {
    const f = coverageFixture();
    f.state.runs.unshift({ id: 53, head_sha: otherSha, status: 'completed', conclusion: 'success', event: 'push' });
    assert.equal(await f.find(), '52');
    assert.deepEqual(
        f.calls.filter(([name]) => name !== 'runs').map(([, options]) => options.run_id),
        [52, 52],
    );
});

test('Sonar coverage: a failed newer test attempt does not hide successful coverage for the same source', async () => {
    const f = coverageFixture();
    f.state.runs.unshift({ id: 53, head_sha: sourceSha, status: 'completed', conclusion: 'failure', event: 'pull_request' });
    f.state.jobsByRun = { 53: [{ name: 'Test / Client Tests', conclusion: 'failure' }] };
    assert.equal(await f.find(), '52');
    assert.deepEqual(
        f.calls.filter(([name]) => name === 'artifacts').map(([, options]) => options.run_id),
        [52],
    );
});

test('Sonar coverage: malformed source identity is rejected before any GitHub request', async () => {
    const f = coverageFixture();
    await assert.rejects(f.find('short-sha'), /full source commit SHA/);
    assert.deepEqual(f.calls, []);
});

for (const conclusion of ['failure', 'cancelled', 'in_progress', null]) {
    test(`Sonar coverage: rejects a ${conclusion} test producer`, async () => {
        const f = coverageFixture();
        f.state.jobs[0].conclusion = conclusion;
        await assert.rejects(f.find(), /exact-source/);
    });
}

for (const [name, change] of [
    [
        'no test metadata',
        (f) => {
            f.state.jobs = [];
        },
    ],
    [
        'only skipped test producers',
        (f) => {
            f.state.jobs[0].conclusion = 'skipped';
        },
    ],
    [
        'an unrelated successful test job',
        (f) => {
            f.state.jobs[0].name = 'Test / AI Worker Sandbox Tests';
        },
    ],
    [
        'client coverage with only server producers',
        (f) => {
            f.state.jobs = [{ name: 'Test / Server Tests (PostgreSQL)', conclusion: 'success' }];
        },
    ],
    [
        'selected server coverage with only a client producer',
        (f) => {
            f.state.artifacts[0].name = 'Sonar Server Coverage';
        },
    ],
    [
        'complete server coverage without a server report',
        (f) => {
            f.state.artifacts[0].name = 'Server JaCoCo XML';
        },
    ],
    [
        'complete server coverage without execution shards',
        (f) => {
            f.state.artifacts[0].name = 'Server JaCoCo XML';
            f.state.jobs = [{ name: 'Test / Server Tests Report', conclusion: 'success' }];
        },
    ],
    [
        'expired coverage',
        (f) => {
            f.state.artifacts[0].expired = true;
        },
    ],
    [
        'no coverage artifacts',
        (f) => {
            f.state.artifacts = [];
        },
    ],
    [
        'an unrelated artifact',
        (f) => {
            f.state.artifacts[0].name = 'Client Coverage Summaries';
        },
    ],
]) {
    test(`Sonar coverage: rejects ${name}`, async () => {
        const f = coverageFixture();
        change(f);
        await assert.rejects(f.find(), /exact-source/);
    });
}

for (const [name, property] of [
    ['workflow runs', 'runsError'],
    ['job metadata', 'jobsError'],
    ['artifact metadata', 'artifactsError'],
]) {
    test(`Sonar coverage: API failure while reading ${name} never establishes coverage`, async () => {
        const f = coverageFixture();
        f.state[property] = new Error('GitHub unavailable');
        await assert.rejects(f.find(), /GitHub unavailable/);
    });
}

const { spawnSync } = require('node:child_process');
const os = require('node:os');
function scanner({ pr = '12', selected = false, status = 0, token = 'fixture-token' } = {}) {
    const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'sonar-arguments-'));
    try {
        fs.writeFileSync(path.join(dir, 'gradlew'), '#!/usr/bin/env bash\nprintf "%s\\n" "$@" > "$ARGS_OUTPUT"\nexit "$FAKE_EXIT"\n', { mode: 0o755 });
        if (selected) {
            for (const project of ['normal', 'monaco']) {
                const report = path.join(dir, 'build/test-results/vitest/sonar-coverage', project, 'lcov.info');
                fs.mkdirSync(path.dirname(report), { recursive: true });
                fs.writeFileSync(report, 'SF:src/main/webapp/app/course/example.ts\nDA:1,1\nend_of_record\n');
            }
        }
        const output = path.join(dir, 'arguments');
        const result = spawnSync('bash', [path.join(__dirname, 'run-sonar.sh')], {
            cwd: dir,
            encoding: 'utf8',
            env: {
                ...process.env,
                SONAR_REVISION: 'a'.repeat(40),
                SONAR_BRANCH_NAME: 'feature/space safe',
                SONAR_PULL_REQUEST: pr,
                SONAR_BASE_BRANCH: 'develop',
                SONAR_SELECTED_SERVER: String(selected),
                SONAR_TOKEN: token,
                ARGS_OUTPUT: output,
                FAKE_EXIT: String(status),
            },
        });
        return { ...result, args: fs.existsSync(output) ? fs.readFileSync(output, 'utf8').trim().split('\n') : [] };
    } finally {
        fs.rmSync(dir, { recursive: true, force: true });
    }
}
test('scanner waits for the PR gate and preserves PR identity without branch-analysis parameters', () => {
    const r = scanner();
    assert.equal(r.status, 0, r.stderr);
    for (const arg of [
        'compileJava',
        'sonar',
        '-Dsonar.qualitygate.wait=true',
        '-Dsonar.scm.revision=' + 'a'.repeat(40),
        '-Dsonar.pullrequest.key=12',
        '-Dsonar.pullrequest.branch=feature/space safe',
        '-Dsonar.pullrequest.base=develop',
    ])
        assert.ok(r.args.includes(arg), arg);
    assert.ok(!r.args.some((arg) => arg.startsWith('-Dsonar.branch.name=')));
    assert.ok(!r.args.includes('test'));
});
test('scanner keeps branch recovery separate from PR analysis', () => {
    const r = scanner({ pr: '0' });
    assert.equal(r.status, 0, r.stderr);
    assert.ok(r.args.includes('-Dsonar.branch.name=feature/space safe'));
    assert.ok(!r.args.some((arg) => arg.startsWith('-Dsonar.pullrequest.')));
});
test('scanner imports actual selected coverage without rerunning tests or changing exclusions', () => {
    const r = scanner({ selected: true });
    assert.equal(r.status, 0, r.stderr);
    assert.ok(r.args.includes('sonarPrCoverage'));
    assert.ok(r.args.includes('--init-script'));
    assert.ok(r.args.some((arg) => arg.endsWith('/sonar-pr-coverage.gradle')));
    assert.ok(
        r.args.includes('-Dsonar.javascript.lcov.reportPaths=build/test-results/vitest/sonar-coverage/normal/lcov.info,build/test-results/vitest/sonar-coverage/monaco/lcov.info'),
    );
    assert.ok(!r.args.some((arg) => arg.includes('exclusions')));
    assert.ok(!r.args.includes('test'));
});
test('scanner and server-quality-gate errors propagate to the required job', () => assert.equal(scanner({ status: 2 }).status, 2));
test('missing Sonar credentials fail before running Gradle', () => {
    const r = scanner({ token: '' });
    assert.notEqual(r.status, 0);
    assert.deepEqual(r.args, []);
});
for (const options of [{ draft: true }, { current: 'false' }, { relevant: 'false' }, { result: 'failure' }]) {
    test('Sonar eligibility rejects ' + JSON.stringify(options), () => assert.equal(shouldAnalyze('pull_request', options), false));
}
test('missing relevance output expands validation instead of hiding Sonar', () => assert.equal(shouldAnalyze('pull_request', { relevant: '' }), true));

test('Sonar coverage: mixed artifacts expose only the coverage with a successful matching producer', () => {
    const { coverageArtifacts } = require('./sonar-pr.js');
    const jobs = [
        { name: 'Test / Client Tests', conclusion: 'success' },
        { name: 'Test / Server Tests (PostgreSQL)', conclusion: 'skipped' },
        { name: 'Test / Server Tests Report', conclusion: 'skipped' },
    ];
    const artifacts = ['Sonar Client Coverage', 'Sonar Server Coverage', 'Server JaCoCo XML'].map((name) => ({ name, expired: false }));
    assert.deepEqual([...coverageArtifacts(jobs, artifacts)], ['Sonar Client Coverage']);
});

test('Sonar coverage: workflow discovery never downloads unverified server reports alongside valid client coverage', async () => {
    const sonarWorkflow = fs.readFileSync(path.join(__dirname, '../workflows/ci-sonar.yml'), 'utf8');
    const step = sonarWorkflow.slice(sonarWorkflow.indexOf('      - name: Discover scope-labelled coverage artifacts'));
    const script = step.match(/          script: \|\n((?:            [^\n]*\n|\n)+)/)[1].replace(/^ {12}/gm, '');
    const actions = { listWorkflowRunArtifacts() {}, listJobsForWorkflowRun() {} };
    const github = {
        rest: { actions },
        paginate: async (route, options) => {
            assert.equal(options.run_id, 52);
            if (route === actions.listWorkflowRunArtifacts) {
                return ['Sonar Client Coverage', 'Sonar Server Coverage', 'Server JaCoCo XML'].map((name) => ({ name, expired: false }));
            }
            assert.equal(route, actions.listJobsForWorkflowRun);
            assert.equal(options.filter, 'latest');
            return [
                { name: 'Test / Client Tests', conclusion: 'success' },
                { name: 'Test / Server Tests Report', conclusion: 'skipped' },
            ];
        },
    };
    const outputs = {};
    const trustedRequire = (file) => {
        assert.equal(file, './.sonar-tools/.github/scripts/sonar-pr.js');
        return require('./sonar-pr.js');
    };
    const AsyncFunction = Object.getPrototypeOf(async () => {}).constructor;
    await new AsyncFunction('github', 'context', 'core', 'require', 'process', script)(
        github,
        { repo },
        {
            setOutput: (key, value) => {
                outputs[key] = value;
            },
        },
        trustedRequire,
        { env: { COVERAGE_RUN: '52' } },
    );
    assert.deepEqual(outputs, { run_id: '52', server_full: 'false', client_full: 'false', server_selected: 'false', client_selected: 'true' });
});

function validateScope(values) {
    const step = job('detect-changes').slice(job('detect-changes').indexOf('      - name: Validate mandatory scope outputs'));
    const script = step.match(/        run: \|\n((?:          [^\n]*\n|\n)+)/)[1].replace(/^ {10}/gm, '');
    // Do not inherit similarly named machine variables: missing output must really be absent.
    const env = { PATH: process.env.PATH, ...values };
    return spawnSync('bash', ['-c', script], { encoding: 'utf8', env });
}

test('Sonar scope: the real Bash guard accepts literal true and false for every mandatory output', () => {
    for (const current of ['true', 'false']) {
        for (const deferred of ['true', 'false']) {
            for (const relevant of ['true', 'false']) {
                const result = validateScope({ CURRENT: current, DEFERRED: deferred, SONAR_RELEVANT: relevant });
                assert.equal(result.status, 0, result.stderr || result.stdout);
            }
        }
    }
});

for (const key of ['CURRENT', 'DEFERRED', 'SONAR_RELEVANT']) {
    for (const [name, value] of [
        ['missing', undefined],
        ['empty', ''],
        ['malformed', 'TRUE'],
    ]) {
        test(`Sonar scope: the real Bash guard rejects ${name} ${key}`, () => {
            const values = { CURRENT: 'true', DEFERRED: 'false', SONAR_RELEVANT: 'true' };
            if (value === undefined) delete values[key];
            else values[key] = value;
            assert.notEqual(validateScope(values).status, 0);
        });
    }
}

function evaluatePolicy(source, github) {
    const expression = source
        .trim()
        .replace(/^\$\{\{\s*|\s*\}\}$/g, '')
        .replaceAll('needs.detect-changes', 'needs.detect_changes');
    return new Function('github', 'needs', 'cancelled', 'startsWith', 'format', 'return (' + expression + ')')(
        github,
        { detect_changes: { outputs: { current: 'true' } } },
        () => false,
        (value, prefix) => String(value ?? '').startsWith(prefix),
        (template, ...values) => template.replace(/\{(\d+)\}/g, (_, index) => String(values[Number(index)])),
    );
}

function editEvent({ retarget = false, action = 'edited', runId = 100 } = {}) {
    return {
        event_name: 'pull_request',
        run_id: runId,
        ref: 'refs/pull/13999/merge',
        sha: sourceSha,
        event: {
            action,
            changes: retarget ? { base: { ref: { from: 'main' } } } : { body: { from: 'Earlier description' } },
            pull_request: { number: 13999, draft: false, title: 'Example PR' },
        },
    };
}

test('Sonar metadata: body edits neither enter validation nor reuse its canonical aggregate name', () => {
    const event = editEvent();
    const detectCondition = job('detect-changes').match(/    if: >-\n((?:      [^\n]*\n)+)/)[1];
    const gate = job('all-required-ci-passed');
    assert.equal(evaluatePolicy(detectCondition, event), false);
    assert.equal(evaluatePolicy(gate.match(/    if: (.+)\n/)[1], event), false);
    assert.notEqual(evaluatePolicy(gate.match(/    name: (.+)\n/)[1], event), 'All required CI Passed');
});

test('Sonar metadata: retargeting enters validation and retains the canonical required aggregate', () => {
    const event = editEvent({ retarget: true });
    const detectCondition = job('detect-changes').match(/    if: >-\n((?:      [^\n]*\n)+)/)[1];
    const gate = job('all-required-ci-passed');
    assert.equal(evaluatePolicy(detectCondition, event), true);
    assert.equal(evaluatePolicy(gate.match(/    if: (.+)\n/)[1], event), true);
    assert.equal(evaluatePolicy(gate.match(/    name: (.+)\n/)[1], event), 'All required CI Passed');
});

test('Sonar metadata: each body edit has a separate concurrency group while retargeting supersedes real CI', () => {
    const group = workflow.match(/^concurrency:\n  group: >-\n([\s\S]*?)\n  cancel-in-progress:/m)[1];
    const real = evaluatePolicy(group, editEvent({ action: 'synchronize' }));
    assert.equal(real, 'ci-pr-13999');
    assert.equal(evaluatePolicy(group, editEvent({ retarget: true })), real);
    assert.notEqual(evaluatePolicy(group, editEvent()), real);
    assert.notEqual(evaluatePolicy(group, editEvent()), evaluatePolicy(group, editEvent({ runId: 101 })));
});

test('Sonar metadata: fork coverage reporting skips metadata runs before acquiring job concurrency', () => {
    const reporter = fs.readFileSync(path.join(__dirname, '../workflows/pullrequest-coverage-reporter.yml'), 'utf8');
    const reportingJob = reporter.slice(reporter.indexOf('  report-coverage:'));
    const condition = reportingJob.match(/    if: \|\n((?:      [^\n]*\n)+)/)[1];
    const context = {
        repository: 'ls1intum/Artemis',
        event: { workflow_run: { event: 'pull_request', display_title: 'PR metadata edit #13999', head_repository: { full_name: 'contributor/Artemis' }, conclusion: 'success' } },
    };
    assert.equal(evaluatePolicy(condition, context), false);
    context.event.workflow_run.display_title = 'CI: Example PR';
    assert.equal(evaluatePolicy(condition, context), true);
    // Workflow concurrency runs even when every job skips; the lock must belong to this eligible job.
    assert.equal(reporter.slice(0, reporter.indexOf('\njobs:')).includes('\nconcurrency:'), false);
    assert.match(reportingJob, /^    concurrency:\n/m);
});

test('Sonar policy: token-restricted internal PRs use the same secretless exact-source verifier', async () => {
    const f = analysisFixture();
    const result = await f.prepare({ allowScan: false, fetchImpl: sonarFetch() });
    assert.equal(result.scan, false);
    assert.equal(f.outputs.scan, 'false');
});
test('Sonar policy: token-restricted internal PRs cannot pass without exact analysis evidence', async () => {
    const f = analysisFixture();
    await assert.rejects(f.prepare({ allowScan: false, fetchImpl: sonarFetch({ pullRequests: [] }) }), /maintainer/);
    assert.deepEqual(f.outputs, {});
});
test('Sonar policy: a manual scan fails before checkout or lookup if its token is unavailable', async () => {
    const f = analysisFixture({ eventName: 'workflow_dispatch' });
    await assert.rejects(f.prepare({ allowScan: false }), /Sonar token is required/);
    assert.deepEqual(f.calls, []);
});
for (const name of ['terminology', 'dead-code']) {
    test('Sonar metadata: independent ' + name + ' checks skip body edits but still run on retargeting', () => {
        const body = job(name);
        const condition = (body.match(/    if: >-\n((?:      [^\n]*\n)+)/) ?? body.match(/    if: (.+)\n/))[1];
        assert.equal(evaluatePolicy(condition, editEvent()), false);
        assert.equal(evaluatePolicy(condition, editEvent({ retarget: true })), true);
    });
}
for (const file of ['vitest.config.ts', 'config/testing/vitest.monaco.config.ts']) {
    test('Sonar scope: declared configuration inputs include coverage producer ' + file, () => {
        const block = workflow.match(/            has_sonar_config:\n((?:              - [^\n]*\n)+)/)[1];
        const patterns = [...block.matchAll(/- '([^']+)'/g)].map((match) => match[1]);
        const matchedFiles = new Set(fs.globSync(patterns, { cwd: path.join(__dirname, '../..') }));
        assert.ok(matchedFiles.has(file), 'A coverage configuration change must trigger the required analysis');
    });
}
