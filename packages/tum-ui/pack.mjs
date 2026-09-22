import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { mkdir, readFile, rm, writeFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = dirname(fileURLToPath(import.meta.url));
const manifest = JSON.parse(await readFile(resolve(root, 'dist-pack/package.json'), 'utf8'));
assert.equal(manifest.name, '@tumaet/ui-angular');
assert.notEqual(manifest.private, true);
assert.equal(manifest.type, 'module');
assert.equal(manifest.scripts, undefined, 'The published artifact must not contain lifecycle scripts');
assert.equal(manifest.devDependencies, undefined);
assert.equal(manifest.publishConfig.access, 'public');
assert.deepEqual(Object.keys(manifest.exports).sort(), ['.', './package.json', './styles.css']);
for (const version of Object.values({ ...manifest.dependencies, ...manifest.peerDependencies })) {
    assert.doesNotMatch(version, /^(?:workspace|catalog|file|link):/);
}
const destination = resolve(root, '../../build/tum-ui-package');
await rm(destination, { recursive: true, force: true });
await mkdir(destination, { recursive: true });
const [packed] = JSON.parse(execFileSync('npm', ['pack', '--json', '--ignore-scripts', '--pack-destination', destination], { cwd: resolve(root, 'dist-pack'), encoding: 'utf8' }));
const files = packed.files.map(({ path }) => path);
for (const required of ['package.json', 'README.md', 'LICENSE', 'CHANGELOG.md', 'styles.css', manifest.exports['.'].types.slice(2), manifest.exports['.'].default.slice(2)]) {
    assert.ok(files.includes(required), `Missing published file: ${required}`);
}
for (const path of files) {
    assert.match(
        path,
        /^(?:package\.json|README\.md|CHANGELOG\.md|LICENSE|styles\.css|fesm2022\/[^/]+\.mjs(?:\.map)?|types\/[^/]+\.d\.ts)$/u,
        `Unexpected published file: ${path}`,
    );
}
const tarball = resolve(destination, packed.filename);
const options = { cwd: root, stdio: 'inherit' };
execFileSync('pnpm', ['exec', 'publint', '--strict', tarball], options);
execFileSync('pnpm', ['exec', 'attw', tarball, '--entrypoints', '.', '--profile', 'esm-only'], options);
const { integrity } = packed;
await writeFile(resolve(destination, 'package.json'), JSON.stringify({ filename: packed.filename, name: manifest.name, version: manifest.version, integrity }, null, 2) + '\n');
console.log(`Validated ${packed.filename} (${packed.size} bytes, ${files.length} files, ${integrity})`);
