/**
 * Writes a stub `package.json` for every entry point of the built package: `dist/<entry>/package.json`.
 *
 * The monorepo resolves `@tumaet/ui-angular` and `@tumaet/ui-angular/<entry>` with the `paths` of its tsconfig files and the aliases of its test
 * runners, which map to the directory `dist`. A path mapping cannot read the `exports` of the package, so a directory per entry is what makes
 * the mapping `@tumaet/ui-angular/* -> dist/*` work for the compiler (`types`) and for the bundler (`module`), as the manifest of `dist`
 * itself does for the root entry. The stubs are not part of the published package: `files` of the package lists only `fesm2022` and `types`.
 */
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const packageRoot = dirname(fileURLToPath(import.meta.url));
const packageDirectoryArgument = process.argv.indexOf('--package-dir');
const packageDirectory = packageDirectoryArgument >= 0 ? resolve(process.argv[packageDirectoryArgument + 1]) : resolve(packageRoot, 'dist');
const manifest = JSON.parse(readFileSync(resolve(packageDirectory, 'package.json'), 'utf8'));

let count = 0;
for (const [subpath, target] of Object.entries(manifest.exports)) {
    if (subpath === '.' || typeof target !== 'object' || !target.types || !target.default) {
        continue; // the root entry (the manifest of dist), `./styles.css`, `./package.json`
    }
    const entryDirectory = resolve(packageDirectory, subpath);
    mkdirSync(entryDirectory, { recursive: true });
    const stub = {
        name: `${manifest.name}/${subpath.slice(2)}`,
        main: `../${target.default.slice(2)}`,
        module: `../${target.default.slice(2)}`,
        types: `../${target.types.slice(2)}`,
        typings: `../${target.types.slice(2)}`,
        sideEffects: false,
    };
    writeFileSync(resolve(entryDirectory, 'package.json'), `${JSON.stringify(stub, null, 2)}\n`);
    count++;
}
if (count === 0) {
    throw new Error(`${packageDirectory}/package.json lists no entry point besides the root: nothing to write.`);
}
console.log(`Wrote ${count} entry point stubs to ${packageDirectory}`);
