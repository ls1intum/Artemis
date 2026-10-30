const { test } = require('node:test');
const assert = require('node:assert/strict');
const check = require('./pr-ready.js');

function fixture(action = 'synchronize') {
    const outputs = {};
    const calls = [];
    const notices = [];
    const context = {
        eventName: 'pull_request',
        payload: { action, pull_request: { number: 12, head: { sha: 'expected' } } },
        repo: { owner: 'example', repo: 'project' },
        runId: 123,
    };
    const state = { pull: { state: 'open', head: { sha: 'expected' } }, created: '2026-10-30T10:00:00Z', now: Date.parse('2026-10-30T10:00:10Z') };
    const github = {
        rest: {
            pulls: {
                get: async () => {
                    calls.push('pull');
                    return { data: state.pull };
                },
            },
            actions: {
                getWorkflowRun: async () => {
                    calls.push('run');
                    return { data: { created_at: state.created } };
                },
            },
        },
    };
    const core = {
        setOutput: (key, value) => {
            outputs[key] = value;
        },
        notice: (message) => notices.push(message),
        info: () => {},
    };
    const args = {
        github,
        context,
        core,
        now: () => state.now,
        delay: async (ms) => {
            calls.push(ms);
            state.now += ms;
        },
    };
    return { outputs, calls, notices, context, state, github, args };
}

test('waits only for the remaining quiet period then checks the current PR head', async () => {
    const f = fixture();
    await check(f.args);
    assert.deepEqual(f.calls, ['pull', 'run', 80_000, 'pull']);
    assert.equal(f.outputs.current, 'true');
});
test('does not wait again after an already queued run has settled', async () => {
    const f = fixture();
    f.state.now += 180_000;
    await check(f.args);
    assert.deepEqual(f.calls, ['pull', 'run', 'pull']);
    assert.equal(f.outputs.current, 'true');
});
test('superseded revisions never become eligible for expensive jobs', async () => {
    const f = fixture();
    f.state.pull = { state: 'open', head: { sha: 'newer' } };
    await check(f.args);
    assert.deepEqual(f.calls, ['pull']);
    assert.equal(f.outputs.current, 'false');
});
test('a revision superseded during the quiet period is rejected', async () => {
    const f = fixture();
    f.args.delay = async (ms) => {
        f.calls.push(ms);
        f.state.pull = { state: 'open', head: { sha: 'newer' } };
    };
    await check(f.args);
    assert.equal(f.outputs.current, 'false');
});
test('closed pull requests do not consume a quiet period or expensive runners', async () => {
    const f = fixture();
    f.state.pull.state = 'closed';
    await check(f.args);
    assert.deepEqual(f.calls, ['pull']);
    assert.equal(f.outputs.current, 'false');
});
for (const action of ['opened', 'reopened', 'ready_for_review']) {
    test(`${action} validates the current head without delaying initial feedback`, async () => {
        const f = fixture(action);
        await check(f.args);
        assert.deepEqual(f.calls, ['pull', 'pull']);
        assert.equal(f.outputs.current, 'true');
    });
}
for (const event of ['push', 'merge_group', 'workflow_dispatch', 'release']) {
    test(`${event} remains eligible without a PR request`, async () => {
        const f = fixture();
        f.context.eventName = event;
        f.context.payload = {};
        await check(f.args);
        assert.deepEqual(f.calls, []);
        assert.equal(f.outputs.current, 'true');
    });
}
test('API failures fail closed instead of approving a revision', async () => {
    const f = fixture();
    f.github.rest.pulls.get = async () => {
        throw new Error('unavailable');
    };
    await assert.rejects(check(f.args), /unavailable/);
    assert.equal(f.outputs.current, undefined);
});
test('malformed PR payloads fail closed', async () => {
    const f = fixture();
    delete f.context.payload.pull_request.head;
    await assert.rejects(check(f.args), /head/i);
    assert.equal(f.outputs.current, undefined);
});
test('invalid workflow timestamps fail closed', async () => {
    const f = fixture();
    f.state.created = 'invalid';
    await assert.rejects(check(f.args), /timestamp/i);
    assert.equal(f.outputs.current, undefined);
});
