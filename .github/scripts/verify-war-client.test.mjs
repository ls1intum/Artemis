import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { after, test } from 'node:test';
import { fileURLToPath } from 'node:url';
import { strToU8, zipSync } from 'fflate';

const VERIFY = fileURLToPath(new URL('./verify-war-client.mjs', import.meta.url));
const STATIC = 'WEB-INF/classes/static/';
const OPTIMIZED = `const optimized='${'a'.repeat(4000)}';\n`;
const UNOPTIMIZED = 'let unoptimized = 1;\n'.repeat(10);

const directory = mkdtempSync(join(tmpdir(), 'verify-war-client-'));
after(() => rmSync(directory, { recursive: true, force: true }));

let counter = 0;

/** Writes a WAR with the given entries (names relative to the static directory, `null` for no index.html) and runs the check on it. */
function verify(index, bundles) {
    const entries = Object.fromEntries(Object.entries(bundles).map(([name, content]) => [STATIC + name, strToU8(content)]));
    if (index !== null) {
        entries[`${STATIC}index.html`] = strToU8(index);
    }
    const war = join(directory, `Artemis-test-${counter++}.war`);
    writeFileSync(war, zipSync(entries));
    return run(war);
}

function run(...args) {
    return spawnSync(process.execPath, [VERIFY, ...args], { encoding: 'utf8' });
}

test('rejects an unminified entry point even with an unused minified bundle', () => {
    const result = verify('<script src="main-served.js" type="module"></script>', { 'main-served.js': UNOPTIMIZED, 'main-unused.js': OPTIMIZED });

    assert.equal(result.status, 1);
    assert.match(result.stdout, /main-served\.js is not minified/);
});

test('accepts the referenced minified bundle with an unused unminified bundle', () => {
    const result = verify("<script type='module' src='./main-served.js'></script>", { 'main-served.js': OPTIMIZED, 'main-unused.js': UNOPTIMIZED });

    assert.equal(result.status, 0, result.stdout + result.stderr);
});

test('accepts a query string and an absolute path on the script reference', () => {
    const result = verify('<script src="/main-served.js?v=1" type="module"></script>', { 'main-served.js': OPTIMIZED });

    assert.equal(result.status, 0, result.stdout + result.stderr);
});

test('rejects a missing referenced bundle', () => {
    const result = verify('<script src="/main-missing.js" type="module"></script>', { 'main-unused.js': OPTIMIZED });

    assert.equal(result.status, 1);
    assert.match(result.stdout, /referenced by index\.html/);
});

test('rejects an index without a main script, and ignores a script in a comment', () => {
    const result = verify('<!-- <script src="main-unused.js"></script> -->', { 'main-unused.js': OPTIMIZED });

    assert.equal(result.status, 1);
    assert.match(result.stdout, /found 0/);
});

test('rejects multiple main scripts', () => {
    const result = verify('<script src="main-first.js"></script><script src="main-second.js"></script>', {
        'main-first.js': OPTIMIZED,
        'main-second.js': OPTIMIZED,
    });

    assert.equal(result.status, 1);
    assert.match(result.stdout, /found 2/);
});

test('rejects a WAR without index.html', () => {
    const result = verify(null, { 'main-served.js': OPTIMIZED });

    assert.equal(result.status, 1);
    assert.match(result.stdout, /exactly one WEB-INF\/classes\/static\/index\.html/);
});

test('counts the lines of a bundle with Windows line endings', () => {
    // 10 lines of 21 bytes plus the carriage return are far below the threshold, and the heuristic must not see a single long line
    const result = verify('<script src="main-served.js"></script>', { 'main-served.js': UNOPTIMIZED.replaceAll('\n', '\r\n') });

    assert.equal(result.status, 1);
    assert.match(result.stdout, /in 10 lines/);
});

test('rejects an empty bundle', () => {
    const result = verify('<script src="main-served.js"></script>', { 'main-served.js': '' });

    assert.equal(result.status, 1);
    assert.match(result.stdout, /in 0 lines/);
});

test('reports a file that is not an archive as an error annotation', () => {
    const file = join(directory, 'not-a-war.war');
    writeFileSync(file, 'this is not a zip archive');

    const result = run(file);

    assert.equal(result.status, 1);
    assert.match(result.stdout, /^::error::.*not-a-war\.war: /);
});

test('reports a missing file as an error annotation', () => {
    const result = run(join(directory, 'missing.war'));

    assert.equal(result.status, 1);
    assert.match(result.stdout, /^::error::.*missing\.war: /);
});

test('asks for the path of the WAR', () => {
    const result = run();

    assert.equal(result.status, 2);
    assert.match(result.stderr, /usage:/);
});
