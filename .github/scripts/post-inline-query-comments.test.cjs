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

test('a re-run posts new findings, keeps current ones and deletes the ones that are gone', () => {
    const existing = [
        { id: 1, body: 'text <!-- query-inline:aaaa -->' },
        { id: 2, body: 'text <!-- query-inline:bbbb -->' },
        { id: 3, body: 'a human comment' },
    ];
    const result = plan([{ key: 'aaaa', body: 'text <!-- query-inline:aaaa -->' }, { key: 'cccc', body: 'new' }], existing);
    assert.deepEqual(result.post.map((c) => c.key), ['cccc']);
    assert.deepEqual(result.update, []);
    assert.deepEqual(result.remove, [2]);
});

test('a current comment whose text changed is rewritten in place', () => {
    const existing = [{ id: 7, body: 'old <!-- query-inline:aaaa -->' }];
    const result = plan([{ key: 'aaaa', body: 'new <!-- query-inline:aaaa -->' }], existing);
    assert.deepEqual(result.post, []);
    assert.deepEqual(result.update, [{ id: 7, body: 'new <!-- query-inline:aaaa -->' }]);
    assert.deepEqual(result.remove, []);
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
    const result = await postInlineQueryComments({ github, context: { repo: { owner: 'o', repo: 'r' } }, prNumber: 1, commentsPath: file, headSha: 'sha' });
    assert.deepEqual(result, { anchored: 2, posted: 2, updated: 0, removed: 0 });
    assert.deepEqual(calls, [['review', 2], ['single', 11], ['single', 12]]);
});
