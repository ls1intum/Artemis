import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { afterEach, test } from 'node:test';

const script = path.join(path.dirname(fileURLToPath(import.meta.url)), 'diff_eager_chunks.mjs');
const directories = [];

afterEach(() => {
    for (const directory of directories.splice(0)) fs.rmSync(directory, { recursive: true, force: true });
});

const chunk = (name, bytes) => ({ imports: [], moduleKey: name, bytes, modules: [[`${name}.ts`, bytes, 1]] });

/** A route with one chunk of its own; `extra` adds that many further chunks of 1 KiB each. */
function route(name, extra = 0) {
    const chunks = { [`${name}.js`]: chunk(name, 1000) };
    for (let i = 0; i < extra; i++) chunks[`${name}-extra-${i}.js`] = chunk(`${name}-extra-${i}`, 1024);
    const files = Object.keys(chunks);
    return { route: { route: name, eagerChunkCount: files.length, eagerBytes: files.length * 1000, chunks: files }, chunks };
}

function report(routes) {
    const chunks = Object.assign({}, ...routes.map((r) => r.chunks ?? {}));
    return { formatVersion: 2, meta: { sourceBranch: 'develop', sourceCommit: '0123456789abcdef' }, chunks, routes: routes.map((r) => r.route) };
}

function compare(baselineRoutes, freshRoutes) {
    const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'chunk-diff-'));
    directories.push(directory);
    const write = (name, content) => {
        const file = path.join(directory, name);
        fs.writeFileSync(file, JSON.stringify(content));
        return file;
    };
    const comment = path.join(directory, 'comment.md');
    const full = path.join(directory, 'full.html');
    const result = spawnSync(
        process.execPath,
        [script, write('fresh.json', report(freshRoutes)), '--baseline', write('baseline.json', report(baselineRoutes)), '--out', comment, '--full-out', full],
        {
            encoding: 'utf8',
        },
    );
    return { result, comment: fs.existsSync(comment) ? fs.readFileSync(comment, 'utf8') : null, full: fs.existsSync(full) ? fs.readFileSync(full, 'utf8') : null };
}

test('still reports every other route when a lazy route is added', () => {
    const { result, comment, full } = compare([route('stable'), route('grows')], [route('stable'), route('grows', 10), route('added')]);
    assert.notEqual(comment, null, result.stderr);
    assert.match(comment, /1 regression\(s\)/);
    assert.match(comment, /grows/);
    assert.match(comment, /added[\s\S]*no baseline data/);
    assert.match(full, /grows/);
});

test('still reports every other route when a lazy route is removed', () => {
    const { result, comment } = compare([route('stable'), route('grows'), route('removed')], [route('stable'), route('grows', 10)]);
    assert.notEqual(comment, null, result.stderr);
    assert.match(comment, /1 regression\(s\)/);
    assert.match(comment, /removed[\s\S]*no fresh data/);
});

test('still reports every other route when a route has an error in either report', () => {
    const broken = { route: { route: 'broken', error: 'no entry chunk' }, chunks: {} };
    const baselineBroken = compare([route('grows'), broken], [route('grows', 10), route('broken')]);
    assert.notEqual(baselineBroken.comment, null, baselineBroken.result.stderr);
    assert.match(baselineBroken.comment, /baseline error: no entry chunk/);
    assert.match(baselineBroken.comment, /1 regression\(s\)/);

    const freshBroken = compare([route('grows'), route('broken')], [route('grows', 10), broken]);
    assert.notEqual(freshBroken.comment, null, freshBroken.result.stderr);
    assert.match(freshBroken.comment, /fresh error: no entry chunk/);
});
