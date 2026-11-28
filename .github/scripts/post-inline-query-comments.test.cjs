const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { visibleLines, anchorable, plan, postInlineQueryComments } = require('./post-inline-query-comments.js');

const patch = ['@@ -10,3 +10,5 @@ class A {', ' context', '-removed', '+added one', '+added two', ' tail', String.fromCharCode(92) + ' No newline at end of file', '@@ -40,2 +42,2 @@', ' x', '+y'].join('\n');

test('the diff shows the added and unchanged lines of the new file, not the removed ones', () => {
    assert.deepEqual([...visibleLines(patch)], [10, 11, 12, 13, 42, 43]);
    assert.equal(visibleLines(undefined).size, 0);
});

test('only comments on a line of the diff can be anchored', () => {
    const files = [{ filename: 'src/A.java', patch }, { filename: 'src/Binary.png' }];
    const comments = [
        { path: 'src/A.java', line: 11, key: 'a', body: '' },
        { path: 'src/A.java', line: 30, key: 'b', body: '' },
        { path: 'src/Other.java', line: 11, key: 'c', body: '' },
        { path: 'src/Binary.png', line: 1, key: 'd', body: '' },
    ];
    assert.deepEqual(anchorable(comments, files).map((c) => c.key), ['a']);
});

test('a comment moves to its fallback line when its own line is not in the diff', () => {
    const files = [{ filename: 'src/A.java', patch }];
    const comments = [
        { path: 'src/A.java', line: 30, key: 'a', body: '', fallback: { path: 'src/A.java', line: 12 } },
        { path: 'src/A.java', line: 30, key: 'b', body: '', fallback: { path: 'src/A.java', line: 31 } },
        { path: 'src/A.java', line: 11, key: 'c', body: '', fallback: { path: 'src/A.java', line: 12 } },
    ];
    assert.deepEqual(anchorable(comments, files).map((c) => [c.key, c.line]), [['a', 12], ['c', 11]]);
});

const bot = { login: 'github-actions[bot]' };
const human = { login: 'a-reviewer' };

test('a re-run posts new findings, keeps current ones and deletes the ones that are gone', () => {
    const existing = [
        { id: 1, user: bot, body: 'text <!-- query-inline:aaaa -->' },
        { id: 2, user: bot, body: 'text <!-- query-inline:bbbb -->' },
        { id: 3, user: bot, body: 'a bot comment without a marker' },
    ];
    const result = plan([{ key: 'aaaa', body: 'text <!-- query-inline:aaaa -->' }, { key: 'cccc', body: 'new' }], existing);
    assert.deepEqual(result.post.map((c) => c.key), ['cccc']);
    assert.deepEqual(result.update, []);
    assert.deepEqual(result.remove, [2]);
});

test('a current comment whose text changed is rewritten in place', () => {
    const existing = [{ id: 7, user: bot, body: 'old <!-- query-inline:aaaa -->' }];
    const result = plan([{ key: 'aaaa', body: 'new <!-- query-inline:aaaa -->' }], existing);
    assert.deepEqual(result.post, []);
    assert.deepEqual(result.update, [{ id: 7, body: 'new <!-- query-inline:aaaa -->' }]);
    assert.deepEqual(result.remove, []);
});

test('a comment of someone else that contains a marker is never rewritten or deleted', () => {
    const quoted = [
        { id: 8, user: human, body: '> <!-- query-inline:aaaa -->\nI disagree' },
        { id: 9, user: { login: 'another-app[bot]' }, body: 'quoting <!-- query-inline:bbbb -->' },
        { id: 10, user: null, body: 'a deleted account <!-- query-inline:cccc -->' },
    ];
    assert.deepEqual(plan([], quoted), { post: [], update: [], remove: [] });
    // a quote does not stand in for the bot's comment either: the finding is still posted
    const wanted = { key: 'aaaa', body: 'finding <!-- query-inline:aaaa -->' };
    assert.deepEqual(plan([wanted], quoted), { post: [wanted], update: [], remove: [] });
});

test('findings are posted as one review; a rejected review falls back to single comments', async () => {
    const file = path.join(fs.mkdtempSync(path.join(os.tmpdir(), 'inline-')), 'comments.json');
    fs.writeFileSync(file, JSON.stringify([{ path: 'src/A.java', line: 11, key: 'aaaa', body: 'one' }, { path: 'src/A.java', line: 12, key: 'bbbb', body: 'two' }]));
    const calls = [];
    const github = {
        paginate: async (fn) => (fn.name === 'listFiles' ? [{ filename: 'src/A.java', patch }] : []),
        rest: { pulls: {
            listFiles: function listFiles() {},
            listReviewComments: function listReviewComments() {},
            createReview: async (r) => { calls.push(['review', r.comments.length]); throw new Error('422'); },
            createReviewComment: async (r) => { calls.push(['single', r.line]); },
            deleteReviewComment: async () => {},
            updateReviewComment: async () => {},
        } },
    };
    const result = await postInlineQueryComments({ github, context: { repo: { owner: 'o', repo: 'r' } }, prNumber: 1, commentsPath: file, headSha: 'sha', testedSha: 'sha' });
    assert.deepEqual(result, { anchored: 2, posted: 2, updated: 0, removed: 0 });
    assert.deepEqual(calls, [['review', 2], ['single', 11], ['single', 12]]);
});

function recordingGithub(calls) {
    return {
        paginate: async (fn) => (fn.name === 'listFiles' ? [{ filename: 'src/A.java', patch }] : [
            { id: 7, user: bot, body: 'old <!-- query-inline:aaaa -->' },
            { id: 8, user: human, body: '> old <!-- query-inline:aaaa -->' },
        ]),
        rest: { pulls: {
            listFiles: function listFiles() {},
            listReviewComments: function listReviewComments() {},
            createReview: async (r) => { calls.push(['review', r.commit_id]); },
            createReviewComment: async () => {},
            deleteReviewComment: async (r) => { calls.push(['delete', r.comment_id]); },
            updateReviewComment: async () => {},
        } },
    };
}

test('without a complete report nothing is deleted', async () => {
    const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'inline-'));
    const missing = path.join(dir, 'missing.json');
    const broken = path.join(dir, 'broken.json');
    fs.writeFileSync(broken, '{not json');
    for (const commentsPath of [missing, broken]) {
        const calls = [];
        const result = await postInlineQueryComments({ github: recordingGithub(calls), context: { repo: { owner: 'o', repo: 'r' } }, prNumber: 1, commentsPath, headSha: 'sha', testedSha: 'sha' });
        assert.equal(result.removed, 0);
        assert.ok(result.skipped);
        assert.deepEqual(calls, []);
    }
});

test('an empty but complete report deletes the comments whose findings are gone', async () => {
    const file = path.join(fs.mkdtempSync(path.join(os.tmpdir(), 'inline-')), 'comments.json');
    fs.writeFileSync(file, '[]');
    const calls = [];
    const result = await postInlineQueryComments({ github: recordingGithub(calls), context: { repo: { owner: 'o', repo: 'r' } }, prNumber: 1, commentsPath: file, headSha: 'sha', testedSha: 'sha' });
    assert.equal(result.removed, 1);
    assert.deepEqual(calls, [['delete', 7]]);
});

test('a report of another commit than the pull request head is not reconciled', async () => {
    const file = path.join(fs.mkdtempSync(path.join(os.tmpdir(), 'inline-')), 'comments.json');
    fs.writeFileSync(file, JSON.stringify([{ path: 'src/A.java', line: 11, key: 'bbbb', body: 'two' }]));
    const calls = [];
    const result = await postInlineQueryComments({ github: recordingGithub(calls), context: { repo: { owner: 'o', repo: 'r' } }, prNumber: 1, commentsPath: file, headSha: 'newer', testedSha: 'older' });
    assert.ok(result.skipped);
    assert.deepEqual(calls, []);
});

test('comments are attached to the tested commit', async () => {
    const file = path.join(fs.mkdtempSync(path.join(os.tmpdir(), 'inline-')), 'comments.json');
    fs.writeFileSync(file, JSON.stringify([{ path: 'src/A.java', line: 11, key: 'bbbb', body: 'two' }]));
    const calls = [];
    await postInlineQueryComments({ github: recordingGithub(calls), context: { repo: { owner: 'o', repo: 'r' } }, prNumber: 1, commentsPath: file, headSha: 'sha', testedSha: 'sha', removeStale: false });
    assert.deepEqual(calls, [['review', 'sha']]);
});
