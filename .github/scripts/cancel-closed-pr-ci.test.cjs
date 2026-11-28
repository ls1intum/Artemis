const assert = require('node:assert/strict');
const test = require('node:test');
const cancelClosedPrCi = require('./cancel-closed-pr-ci.js');

const repo = { owner: 'ls1intum', repo: 'Artemis' };
const closedAt = '2026-11-17T20:03:55Z';
const pull = { number: 14658, state: 'closed', closed_at: closedAt };
const run = {
    id: 44142993285,
    path: '.github/workflows/ci.yml',
    event: 'pull_request',
    status: 'in_progress',
    created_at: '2026-11-17T17:59:38Z',
    pull_requests: [],
};

function setup({ members = [{ run_id: run.id }], runs = [run], currentPull = pull, groupError, cancelError, completedRun } = {}) {
    const calls = [];
    const cancelled = [];
    const api = {
        request: async (route, params) => {
            calls.push('group');
            assert.equal(route, 'GET /repos/{owner}/{repo}/actions/concurrency_groups/{concurrency_group_name}');
            assert.deepEqual(params, { ...repo, concurrency_group_name: 'ci-pr-14658', headers: { 'X-GitHub-Api-Version': '2026-03-10' } });
            if (groupError) {
                throw groupError;
            }
            return { data: { group_members: members } };
        },
        rest: {
            pulls: {
                get: async (params) => {
                    calls.push('pull');
                    assert.deepEqual(params, { ...repo, pull_number: pull.number });
                    return { data: currentPull };
                },
            },
            actions: {
                getWorkflowRun: async (params) => {
                    calls.push('run');
                    assert.deepEqual(params, { ...repo, run_id: params.run_id });
                    const found = completedRun?.id === params.run_id && calls.includes('cancel') ? completedRun : runs.find(({ id }) => id === params.run_id);
                    assert.ok(found, `Unexpected candidate ${params.run_id}`);
                    return { data: found };
                },
                cancelWorkflowRun: async (params) => {
                    calls.push('cancel');
                    assert.deepEqual(params, { ...repo, run_id: params.run_id });
                    if (cancelError) {
                        throw cancelError;
                    }
                    cancelled.push(params.run_id);
                },
            },
        },
    };
    return {
        calls,
        cancelled,
        run: () => cancelClosedPrCi({ github: api, context: { eventName: 'pull_request_target', payload: { action: 'closed', pull_request: pull }, repo }, core: { info() {} } }),
    };
}

test('cancels active CI for the merged PR using its concurrency group when pull_requests is empty', async () => {
    const queuedRun = { ...run, id: 44142993301, status: 'pending' };
    const fixture = setup({ members: [{ run_id: run.id }, { run_id: queuedRun.id }], runs: [run, queuedRun] });
    await fixture.run();
    assert.deepEqual(fixture.cancelled, [44142993285, 44142993301]);
});

test('skips group members that are not active umbrella pull request runs from before the close', async () => {
    const candidates = [
        { ...run, id: 2, event: 'push' },
        { ...run, id: 3, event: 'workflow_dispatch' },
        { ...run, id: 4, event: 'release' },
        { ...run, id: 5, event: 'merge_group' },
        { ...run, id: 6, path: '.github/workflows/ci-e2e.yml' },
        { ...run, id: 7, status: 'completed' },
        { ...run, id: 8, created_at: closedAt },
        { ...run, id: 9, created_at: '2026-11-17T20:03:56Z' },
    ];
    const fixture = setup({ members: [...candidates.map(({ id }) => ({ run_id: id })), { run_id: 10, job_id: 12 }], runs: candidates });
    await fixture.run();
    assert.deepEqual(fixture.cancelled, []);
});

test('rechecks the PR after looking up the candidate in case it has been reopened', async () => {
    const fixture = setup({ currentPull: { ...pull, state: 'open', closed_at: null } });
    await fixture.run();
    assert.deepEqual(fixture.calls, ['group', 'run', 'pull']);
    assert.deepEqual(fixture.cancelled, []);
});

test('ignores a stale close event for a PR that has since reopened and closed again', async () => {
    const fixture = setup({ currentPull: { ...pull, closed_at: '2026-11-17T20:04:00Z' } });
    await fixture.run();
    assert.deepEqual(fixture.cancelled, []);
});

test('ends successfully if GitHub reports the concurrency group is inactive', async () => {
    const fixture = setup({ groupError: { status: 404 } });
    await fixture.run();
    assert.deepEqual(fixture.calls, ['group']);
});

test('surfaces API errors when looking up the group', async () => {
    const denied = { status: 403 };
    await assert.rejects(setup({ groupError: denied }).run(), (error) => error === denied);
});

test('accepts a cancellation conflict when the CI run completed in the meantime', async () => {
    const fixture = setup({ cancelError: { status: 409 }, completedRun: { ...run, status: 'completed' } });
    await fixture.run();
    assert.deepEqual(fixture.calls, ['group', 'run', 'pull', 'cancel', 'run']);
});

test('surfaces a cancellation conflict for a run that is still active', async () => {
    const conflict = { status: 409 };
    await assert.rejects(setup({ cancelError: conflict }).run(), (error) => error === conflict);
});
