/**
 * Writes one ES module per FontAwesome icon, so that the client build puts only the icons that a page uses into the page.
 *
 * The packages `@fortawesome/free-solid-svg-icons` and `@fortawesome/free-regular-svg-icons` offer an ES module only as one file with all icons. A bundler
 * that splits code per file (esbuild, which the Angular build uses) puts everything that any lazy route takes from such a file into the chunk that the
 * entry bundle loads, so every icon that any route uses is part of the first download. The packages also contain one file per icon (`faTrash.js`),
 * but as CommonJS. This script turns them into ES modules in `build/fontawesome/<package>/<icon>.ts`, and the `paths` of the tsconfig files map
 * `@fortawesome/<package>/<icon>` to them. Where the generated files do not exist (before the first prebuild) and in the test runner, the import resolves
 * to the CommonJS file of the package, which exports the same icon.
 *
 * See documentation/docs/developer/guidelines/client-development.mdx and supporting_scripts/client/migrate-fontawesome-imports.mjs.
 */
import { createHash } from 'node:crypto';
import { createRequire } from 'node:module';
import { mkdirSync, readFileSync, readdirSync, rmSync, writeFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const repoRoot = resolve(dirname(fileURLToPath(import.meta.url)), '../..');

/** The icon packages of the client, as named below `@fortawesome/`. */
export const ICON_PACKAGES = ['free-solid-svg-icons', 'free-regular-svg-icons'];

/** The directory that the generated modules are written to, relative to the repository root. */
export const OUTPUT_DIRECTORY = 'build/fontawesome';

const STAMP_FILE = '.generated';
/** The file in the output directory that names the package, with its version, that each directory of generated modules comes from. */
export const PROVENANCE_FILE = 'packages.json';
// An icon is a file `fa<Name>.js`: the name of an icon starts with `fa` and goes on with a capital letter or a digit.
const ICON_FILE = /^(fa[A-Z0-9][A-Za-z0-9]*)\.js$/;

/** The names of the icons that a package offers as a module of their own. */
export function iconNames(packageDirectory) {
    return readdirSync(packageDirectory)
        .map((file) => ICON_FILE.exec(file)?.[1])
        .filter(Boolean)
        .sort();
}

/**
 * The source of the module of one icon, from the definition that the package exports for it. The data is the package's own. The assertion is needed
 * because the aliases of an icon contain numbers (character codes) in the data, while the type of the package declares them as strings.
 */
export function iconModuleSource(name, definition, packageName, packageVersion) {
    const { prefix, iconName, icon } = definition;
    return `// Generated from @fortawesome/${packageName} ${packageVersion} by supporting_scripts/client/fontawesome-icons.mjs. Do not edit.
import type { IconDefinition } from '@fortawesome/fontawesome-svg-core';

export const ${name} = ${JSON.stringify({ prefix, iconName, icon })} as unknown as IconDefinition;
`;
}

/**
 * Writes the modules of all icons of the icon packages. Does nothing when they have been written for the installed versions of the packages and of
 * this script.
 *
 * @returns the number of modules written, 0 when they were up to date
 */
export function generateIconModules(root = repoRoot) {
    const require = createRequire(join(root, 'package.json'));
    const output = join(root, OUTPUT_DIRECTORY);
    const packages = ICON_PACKAGES.map((packageName) => {
        const manifestPath = require.resolve(`@fortawesome/${packageName}/package.json`);
        return { packageName, directory: dirname(manifestPath), version: JSON.parse(readFileSync(manifestPath, 'utf8')).version };
    });
    const scriptHash = createHash('sha256')
        .update(readFileSync(fileURLToPath(import.meta.url)))
        .digest('hex');
    const stamp = JSON.stringify({ script: scriptHash, packages: packages.map(({ packageName, version }) => `${packageName}@${version}`) });
    try {
        if (readFileSync(join(output, STAMP_FILE), 'utf8') === stamp) return 0;
    } catch {
        // not generated yet
    }

    rmSync(output, { recursive: true, force: true });
    let written = 0;
    for (const { packageName, directory, version } of packages) {
        const target = join(output, `${packageName}`);
        mkdirSync(target, { recursive: true });
        for (const name of iconNames(directory)) {
            const definition = require(join(directory, `${name}.js`))[name];
            if (!definition?.icon) throw new Error(`${packageName}/${name}.js does not export the icon ${name}`);
            writeFileSync(join(target, `${name}.ts`), iconModuleSource(name, definition, packageName, version));
            written++;
        }
    }
    // The data of the icons ships in the client, so the SBOM of a release has to name the packages that it comes from: the paths of the generated modules in
    // the source maps of the build do not, and supporting_scripts/sbom-filter-by-bundle.mjs reads the versions from here.
    writeFileSync(
        join(output, PROVENANCE_FILE),
        `${JSON.stringify(Object.fromEntries(packages.map(({ packageName, version }) => [`@fortawesome/${packageName}`, version])), null, 2)}\n`,
    );
    writeFileSync(join(output, STAMP_FILE), stamp);
    return written;
}

if (process.argv[1] && fileURLToPath(import.meta.url) === resolve(process.argv[1])) {
    const written = generateIconModules();
    console.log(written > 0 ? `Wrote ${written} icon modules to ${OUTPUT_DIRECTORY}` : `The icon modules in ${OUTPUT_DIRECTORY} are up to date`);
}
