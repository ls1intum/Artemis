const { test } = require('node:test');
const assert = require('node:assert/strict');
const check = require('./pr-ready.js');

function fixture(action = 'synchronize', draft = false) {
    const outputs = {};
    const calls = [];
    const context = {
        eventName: 'pull_request',
        payload: { action, pull_request: { number: 12, draft, head: { sha: 'expected' }, base: { ref: 'develop' } } },
        repo: { owner: 'example', repo: 'project' },
    };
    const state = { pull: { state: 'open', head: { sha: 'expected' }, base: { ref: 'develop' } } };
    const github = {
        rest: {
            pulls: {
                get: async () => {
                    calls.push('pull');
                    return { data: state.pull };
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
    return { outputs, calls, context, state, args: { github, context, core } };
}
for (const action of ['opened', 'synchronize', 'reopened', 'ready_for_review']) {
    test(`${action} checks the current head immediately without a timer or workflow-run request`, async () => {
        const f = fixture(action);
        await check(f.args);
        assert.deepEqual(f.calls, ['pull']);
        assert.deepEqual(f.outputs, { current: 'true', tests_deferred: 'false' });
    });
}
test('drafts defer expensive tests while still validating their head', async () => {
    const f = fixture('synchronize', true);
    await check(f.args);
    assert.deepEqual(f.outputs, { current: 'true', tests_deferred: 'true' });
});
test('promoting the same commit schedules tests instead of reusing draft scope', async () => {
    const draft = fixture('synchronize', true);
    await check(draft.args);
    const ready = fixture('ready_for_review', false);
    await check(ready.args);
    assert.equal(draft.context.payload.pull_request.head.sha, ready.context.payload.pull_request.head.sha);
    assert.equal(draft.outputs.tests_deferred, 'true');
    assert.equal(ready.outputs.tests_deferred, 'false');
});
for (const state of [
    { state: 'open', head: { sha: 'newer' } },
    { state: 'closed', head: { sha: 'expected' }, base: { ref: 'develop' } },
]) {
    test(`rejects ${state.state === 'closed' ? 'closed' : 'superseded'} revisions`, async () => {
        const f = fixture();
        f.state.pull = state;
        await check(f.args);
        assert.equal(f.outputs.current, 'false');
        assert.deepEqual(f.calls, ['pull']);
    });
}
for (const event of ['push', 'merge_group', 'workflow_dispatch', 'release']) {
    test(`${event} never defers tests even if its ref belongs to a draft`, async () => {
        const f = fixture('synchronize', true);
        f.context.eventName = event;
        await check(f.args);
        assert.deepEqual(f.calls, []);
        assert.deepEqual(f.outputs, { current: 'true', tests_deferred: 'false' });
    });
}
test('API errors fail closed', async () => {
    const f = fixture();
    f.args.github.rest.pulls.get = async () => {
        throw new Error('unavailable');
    };
    await assert.rejects(check(f.args), /unavailable/);
    assert.equal(f.outputs.current, undefined);
});
test('malformed pull requests fail closed', async () => {
    const f = fixture();
    delete f.context.payload.pull_request.head;
    await assert.rejects(check(f.args), /head/i);
    assert.equal(f.outputs.current, undefined);
});

test('a retargeted PR invalidates an analysis even when the head SHA is unchanged', async () => {
    const f = fixture();
    f.state.pull.base.ref = 'main';
    await check(f.args);
    assert.equal(f.outputs.current, 'false');
});
