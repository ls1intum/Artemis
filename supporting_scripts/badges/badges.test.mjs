import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { afterEach, test } from 'node:test';
import { fetchReleases, latestArtemisRelease, main, renderBadge, renderCoverage } from './render-badges.mjs';
import { publishBadges } from './publish-badges.mjs';

const directories = [];
function temporaryDirectory() {
    const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'artemis-badges-'));
    directories.push(directory);
    return directory;
}
afterEach(() => {
    for (const directory of directories.splice(0)) fs.rmSync(directory, { recursive: true, force: true });
});

const release = (tag_name, overrides = {}) => ({ tag_name, draft: false, prerelease: false, ...overrides });

test('selects the highest stable Artemis version despite scoped and backport releases', () => {
    assert.equal(
        latestArtemisRelease([
            release('@tumaet/ui@99.0.0'),
            release('9.9.9'),
            release('10.3'),
            release('v10.3.1'),
            release('999.0.0', { draft: true }),
            release('998.0.0', { prerelease: true }),
            release('997.0.0-dev'),
            release('996.0.0-rc.1'),
            release('10.2.4'),
        ]),
        'v10.3.1',
    );
    assert.throws(() => latestArtemisRelease([release('@tumaet/ui@1.0.0')]));
    assert.throws(() => latestArtemisRelease({ message: 'rate limited' }));
});

test('paginates releases so a page of scoped tags cannot hide Artemis', async () => {
    const urls = [];
    const releases = await fetchReleases('ls1intum/Artemis', 'secret', async (url, options) => {
        urls.push(url);
        assert.equal(options.headers.Authorization, 'Bearer secret');
        return { ok: true, json: async () => (urls.length === 1 ? Array.from({ length: 100 }, () => release('@tumaet/ui@1.0.0')) : [release('10.3.1')]) };
    });
    assert.equal(latestArtemisRelease(releases), '10.3.1');
    assert.match(urls[1], /page=2$/);
});

test('rejects API failures and malformed data instead of inventing a release', async () => {
    await assert.rejects(
        fetchReleases('ls1intum/Artemis', undefined, async () => ({ ok: false, status: 503 })),
        /HTTP 503/,
    );
    await assert.rejects(
        fetchReleases('ls1intum/Artemis', undefined, async () => ({ ok: true, json: async () => ({ message: 'bad response' }) })),
        /not an array/,
    );
});

test('SVG escapes text and contains no scripts, external resources or font downloads', () => {
    const svg = renderBadge('a<&', '"MIT"', 'blue');
    assert.match(svg, /<title>a&lt;&amp;: &quot;MIT&quot;<\/title>/);
    assert.match(svg, /role="img"/);
    assert.doesNotMatch(svg, /<script|href=|@import|url\(https?:/);
    assert.throws(() => renderBadge('license', 'MIT', 'red" onload="alert(1)'));
    assert.throws(() => renderBadge('license', '\u0000', 'blue'));
});

test('renders the authoritative coverage JSON and retains SVG on invalid input', async () => {
    const directory = temporaryDirectory();
    const file = path.join(directory, 'coverage.json');
    fs.writeFileSync(file, JSON.stringify({ schemaVersion: 1, label: 'coverage', message: '88.3%', color: 'green' }));
    await main(['coverage', directory]);
    const published = fs.readFileSync(path.join(directory, 'coverage.svg'), 'utf8');
    assert.match(published, /coverage: 88.3%/);
    assert.equal(renderCoverage(file), published);
    for (const message of ['101.0%', 'NaN%', '<script/>']) {
        fs.writeFileSync(file, JSON.stringify({ schemaVersion: 1, label: 'coverage', message, color: 'green' }));
        await assert.rejects(main(['coverage', directory]));
        assert.equal(fs.readFileSync(path.join(directory, 'coverage.svg'), 'utf8'), published);
    }
});

test('metadata renders all three badges from release and coverage sources, retaining them on API failure', async () => {
    const directory = temporaryDirectory();
    fs.writeFileSync(path.join(directory, 'coverage.json'), JSON.stringify({ schemaVersion: 1, label: 'coverage', message: '88.3%', color: 'green' }));
    const previousFetch = globalThis.fetch;
    const previousRepository = process.env.GITHUB_REPOSITORY;
    process.env.GITHUB_REPOSITORY = 'ls1intum/Artemis';
    try {
        globalThis.fetch = async () => ({ ok: true, json: async () => [release('10.3.1'), release('@tumaet/ui@99.0.0')] });
        await main(['metadata', directory]);
        const files = ['coverage.svg', 'latest-version.svg', 'license.svg'];
        const contents = files.map((file) => fs.readFileSync(path.join(directory, file), 'utf8'));
        assert.match(contents[0], /coverage: 88.3%/);
        assert.match(contents[1], /latest version: 10.3.1/);
        assert.match(contents[2], /license: MIT/);
        globalThis.fetch = async () => ({ ok: false, status: 503 });
        await assert.rejects(main(['metadata', directory]), /HTTP 503/);
        assert.deepEqual(
            files.map((file) => fs.readFileSync(path.join(directory, file), 'utf8')),
            contents,
        );
    } finally {
        globalThis.fetch = previousFetch;
        if (previousRepository === undefined) delete process.env.GITHUB_REPOSITORY;
        else process.env.GITHUB_REPOSITORY = previousRepository;
    }
});

function git(directory, args) {
    return execFileSync('git', args, { cwd: directory, stdio: 'pipe' });
}

function badgeRepository() {
    const directory = temporaryDirectory();
    const remote = path.join(directory, 'remote.git');
    const first = path.join(directory, 'first');
    const second = path.join(directory, 'second');
    git(directory, ['init', '--bare', '--initial-branch=badges', remote]);
    git(directory, ['clone', remote, first]);
    git(first, ['config', 'user.name', 'Badge Test']);
    git(first, ['config', 'user.email', 'test@example.com']);
    fs.writeFileSync(path.join(first, 'coverage.json'), '{}\n');
    git(first, ['add', '.']);
    git(first, ['commit', '-m', 'Seed']);
    git(first, ['push', 'origin', 'HEAD:badges']);
    git(directory, ['clone', remote, second]);
    return { remote, first, second };
}

test('concurrent coverage and metadata publishers preserve both updates with a non-force retry', () => {
    const { remote, first, second } = badgeRepository();
    fs.writeFileSync(path.join(first, 'coverage.svg'), 'new coverage');
    fs.writeFileSync(path.join(second, 'latest-version.svg'), '10.3.1');
    let pushes = 0;
    publishBadges(first, ['coverage.svg'], (args) => {
        if (args[0] === 'push' && pushes++ === 0) publishBadges(second, ['latest-version.svg']);
        assert.ok(!args.includes('--force'));
        return git(first, args);
    });
    assert.equal(pushes, 2);
    assert.equal(git(remote, ['show', 'badges:coverage.svg']).toString(), 'new coverage');
    assert.equal(git(remote, ['show', 'badges:latest-version.svg']).toString(), '10.3.1');
    assert.equal(publishBadges(first, ['coverage.svg']), false);
});

test('a conflict retains the last published value', () => {
    const { remote, first, second } = badgeRepository();
    fs.writeFileSync(path.join(first, 'coverage.json'), 'first update');
    fs.writeFileSync(path.join(second, 'coverage.json'), 'second update');
    publishBadges(second, ['coverage.json']);
    assert.throws(() => publishBadges(first, ['coverage.json']));
    assert.equal(git(remote, ['show', 'badges:coverage.json']).toString(), 'second update');
});

test('publishing named badges leaves unrelated staged files out of the commit', () => {
    const { remote, first } = badgeRepository();
    fs.writeFileSync(path.join(first, 'unrelated.txt'), 'keep staged');
    git(first, ['add', 'unrelated.txt']);
    fs.writeFileSync(path.join(first, 'coverage.svg'), 'new coverage');

    assert.equal(publishBadges(first, ['coverage.svg']), true);

    assert.equal(git(remote, ['show', '--format=', '--name-only', 'badges']).toString().trim(), 'coverage.svg');
    assert.equal(git(first, ['diff', '--cached', '--name-only']).toString().trim(), 'unrelated.txt');
});

test('unrelated staged files do not trigger publication of unchanged badges', () => {
    const { remote, first } = badgeRepository();
    const previousHead = git(remote, ['rev-parse', 'badges']).toString();
    fs.writeFileSync(path.join(first, 'unrelated.txt'), 'keep staged');
    git(first, ['add', 'unrelated.txt']);

    assert.equal(publishBadges(first, ['coverage.json']), false);

    assert.equal(git(remote, ['rev-parse', 'badges']).toString(), previousHead);
    assert.equal(git(first, ['diff', '--cached', '--name-only']).toString().trim(), 'unrelated.txt');
});
