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
const artifactDirectory = resolve(root, 'build/tum-aet-ui-package');
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
    // Vary the library peers, keeping builder-only transitive dependencies outside this peer matrix.
    '@angular/build': catalog['@angular/build'],
    '@angular/cli': mode === 'minimum' ? catalog['@angular/cli'] : `^${catalog['@angular/cli']}`,
    '@angular/compiler-cli': dependencies['@angular/core'],
};
if (mode === 'minimum') devDependencies.typescript = catalog.typescript;
// @angular/build 22.2.5 added the optional @oxc-parser/binding-wasm32-wasi 0.150.0. It depends on @emnapi/core and @emnapi/runtime 2.0.0-alpha.3,
// which none of the versions of its own @napi-rs/wasm-runtime accept as a peer (1.2.4 and 1.2.5 allow ^1.7.1 || ^2.0.0-alpha.4, 1.2.6 allows
// ^1.7.1 || 2.0.0-beta.2), so `--strict-peer-deps` fails with ERESOLVE. Resolve the emnapi packages of that binding to the 1.x line the runtime
// accepts; remove once oxc-parser fixes its manifest.
const overrides = { '@oxc-parser/binding-wasm32-wasi': { '@emnapi/core': '^1.7.1', '@emnapi/runtime': '^1.7.1' } };
// Install in a temporary directory, so that workspace links and the workspace overrides stay out of consumer dependency resolution.
const consumer = await mkdtemp(resolve(tmpdir(), 'tum-aet-ui-consumer-'));
const results = resolve(root, 'build/test-results/tum-aet-ui-consumer', mode);
await rm(results, { recursive: true, force: true });
try {
    await cp(resolve(directory, 'app'), consumer, { recursive: true });
    await writeFile(resolve(consumer, 'package.json'), JSON.stringify({ name: 'tum-aet-ui-consumer', private: true, type: 'module', dependencies, devDependencies, overrides }, null, 2));
    const options = { cwd: consumer, stdio: 'inherit' };
    execFileSync('npm', ['install', '--ignore-scripts', '--strict-peer-deps', '--no-audit', '--no-fund'], options);
    execFileSync('npm', ['ls', '--depth=0'], options);
    execFileSync('npm', ['audit', '--omit=dev', '--audit-level=high'], options);
    execFileSync('node', ['node_modules/@angular/cli/bin/ng.js', 'build'], options);
    execFileSync('pnpm', ['exec', 'playwright', 'test', '--config', 'consumer/playwright.config.ts'], {
        cwd: resolve(directory, '..'),
        stdio: 'inherit',
        env: { ...process.env, TUM_AET_UI_CONSUMER_DIR: consumer, TUM_AET_UI_CONSUMER_RESULTS: results },
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
