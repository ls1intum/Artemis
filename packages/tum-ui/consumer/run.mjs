import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { cp, mkdir, mkdtemp, readFile, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import semver from 'semver';
import { parse } from 'yaml';

const directory = dirname(fileURLToPath(import.meta.url));
const root = resolve(directory, '../../..');
const mode = process.argv[2] ?? 'minimum';
assert.ok(['minimum', 'latest'].includes(mode), 'Consumer mode must be minimum or latest');
const { catalog } = parse(await readFile(resolve(root, 'pnpm-workspace.yaml'), 'utf8'));
const artifactDirectory = resolve(root, 'build/tum-ui-package');
const artifact = JSON.parse(await readFile(resolve(artifactDirectory, 'package.json'), 'utf8'));
const tarball = resolve(artifactDirectory, artifact.filename);
assert.equal(
    `sha512-${createHash('sha512')
        .update(await readFile(tarball))
        .digest('base64')}`,
    artifact.integrity,
);
const manifest = JSON.parse(execFileSync('tar', ['-xOf', tarball, 'package/package.json'], { encoding: 'utf8' }));
const dependencies = Object.fromEntries(Object.entries(manifest.peerDependencies).map(([name, range]) => [name, mode === 'minimum' ? semver.minVersion(range).version : range]));
for (const name of ['@angular/compiler', '@angular/platform-browser']) {
    dependencies[name] = dependencies['@angular/core'];
}
dependencies[manifest.name] = `file:${tarball}`;
const devDependencies = {
    '@angular/build': mode === 'minimum' ? catalog['@angular/build'] : `^${catalog['@angular/build']}`,
    '@angular/cli': mode === 'minimum' ? catalog['@angular/cli'] : `^${catalog['@angular/cli']}`,
    '@angular/compiler-cli': dependencies['@angular/core'],
};
if (mode === 'minimum') devDependencies.typescript = catalog.typescript;
// Keep workspace links and overrides out of consumer dependency resolution.
const consumer = await mkdtemp(resolve(tmpdir(), 'tum-ui-consumer-'));
const results = resolve(root, 'build/test-results/tum-ui-consumer', mode);
await rm(results, { recursive: true, force: true });
try {
    await cp(resolve(directory, 'app'), consumer, { recursive: true });
    await writeFile(resolve(consumer, 'package.json'), JSON.stringify({ name: 'tum-ui-consumer', private: true, type: 'module', dependencies, devDependencies }, null, 2));
    const options = { cwd: consumer, stdio: 'inherit' };
    execFileSync('npm', ['install', '--ignore-scripts', '--strict-peer-deps', '--no-audit', '--no-fund'], options);
    execFileSync('npm', ['ls', '--depth=0'], options);
    execFileSync('npm', ['audit', '--omit=dev', '--audit-level=high'], options);
    execFileSync('node', ['node_modules/@angular/cli/bin/ng.js', 'build'], options);
    execFileSync('pnpm', ['exec', 'playwright', 'test', '--config', 'consumer/playwright.config.ts'], {
        cwd: resolve(directory, '..'),
        stdio: 'inherit',
        env: { ...process.env, TUM_UI_CONSUMER_DIR: consumer, TUM_UI_CONSUMER_RESULTS: results },
    });
    console.log(`Validated ${artifact.filename} in an isolated Angular consumer (${mode}).`);
} catch (error) {
    await mkdir(results, { recursive: true });
    for (const filename of ['package.json', 'package-lock.json']) {
        await cp(resolve(consumer, filename), resolve(results, filename)).catch((copyError) => {
            if (copyError.code !== 'ENOENT') throw copyError;
        });
    }
    throw error;
} finally {
    await rm(consumer, { recursive: true, force: true });
}
