#!/usr/bin/env node

/**
 * Fails when the production client resolves the same package to more than one version.
 *
 * Two copies of a library mean two copies in the bundle, two sets of advisories to follow and, for
 * stateful libraries, bugs that only show when the copies meet. A duplicate usually comes from two
 * dependencies that ask for incompatible ranges, so it shows up silently when one of them is bumped.
 *
 * The production closure is read from pnpm-lock.yaml: everything reachable from the `dependencies`
 * (not `devDependencies`) of every workspace project, following `dependencies` and
 * `optionalDependencies` of each resolved package. Peer dependencies are already part of the
 * resolved snapshots, so they count.
 *
 * A duplicate that cannot be removed yet goes into duplicate-dependencies-allowlist.json with the
 * reason and the exact versions. The check also fails when an allowlisted entry is no longer a
 * duplicate or its versions changed, so the list shrinks and every change gets a second look.
 *
 * Usage:
 *   node supporting_scripts/client/check_duplicate_dependencies.mjs
 *   node supporting_scripts/client/check_duplicate_dependencies.mjs --stats build/browser-stats.json
 *   node supporting_scripts/client/check_duplicate_dependencies.mjs --lockfile path/to/pnpm-lock.yaml
 *
 * With --stats the check also reads the esbuild metafile of a production build
 * (`ng build --configuration production --stats-json`) and fails when the same package is bundled
 * in more than one version, which is what users download.
 */

import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { parseAllDocuments } from 'yaml';

const scriptDirectory = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(scriptDirectory, '..', '..');
const defaultLockfile = resolve(repoRoot, 'pnpm-lock.yaml');
const defaultAllowlist = resolve(scriptDirectory, 'duplicate-dependencies-allowlist.json');

/** The lockfile is two YAML documents (environment, then project); the project one is last. */
export function parseLockfile(text) {
    const documents = parseAllDocuments(text).map((document) => document.toJS());
    return documents[documents.length - 1];
}

/** Splits a snapshot key such as `@scope/name@1.2.3(peer@4)` into name and version. */
export function splitSnapshotKey(key) {
    const base = key.split('(')[0];
    const separator = base.lastIndexOf('@');
    return { name: base.slice(0, separator), version: base.slice(separator + 1) };
}

/** The snapshot key a dependency edge points to, or undefined for a workspace link. */
function snapshotKeyFor(name, reference) {
    const value = String(reference);
    if (value.startsWith('link:')) {
        return undefined;
    }
    // An alias such as `npm:other@1.0.0` already carries its target name.
    const isAlias = value.includes('@') && !/^\d/.test(value);
    return isAlias ? value.replace(/^npm:/, '') : `${name}@${value}`;
}

/**
 * Returns a map from package name to the sorted list of versions in the production closure,
 * for every package that has more than one version.
 */
export function findDuplicates(lockfile) {
    const snapshots = lockfile.snapshots ?? {};
    const pending = [];
    for (const importer of Object.values(lockfile.importers ?? {})) {
        for (const [name, entry] of Object.entries(importer.dependencies ?? {})) {
            const key = snapshotKeyFor(name, entry.version);
            if (key) {
                pending.push(key);
            }
        }
    }

    const seen = new Set();
    while (pending.length > 0) {
        const key = pending.pop();
        if (seen.has(key)) {
            continue;
        }
        seen.add(key);
        const snapshot = snapshots[key] ?? {};
        for (const edges of [snapshot.dependencies, snapshot.optionalDependencies]) {
            for (const [name, reference] of Object.entries(edges ?? {})) {
                const next = snapshotKeyFor(name, reference);
                if (next && !seen.has(next)) {
                    pending.push(next);
                }
            }
        }
    }

    const versionsByName = new Map();
    for (const key of seen) {
        const { name, version } = splitSnapshotKey(key);
        if (!versionsByName.has(name)) {
            versionsByName.set(name, new Set());
        }
        versionsByName.get(name).add(version);
    }
    return toDuplicateMap(versionsByName);
}

const bundledPackagePattern = /node_modules\/\.pnpm\/((?:@[^+/]+\+)?[^@/]+)@([^/_(]+)[^/]*\/node_modules\/((?:@[^/]+\/)?[^/]+)/g;

/**
 * Returns the same map as findDuplicates for the packages that an esbuild metafile shows in more than
 * one version inside JavaScript output files.
 */
export function findBundledDuplicates(metafile) {
    const versionsByName = new Map();
    for (const [outputPath, output] of Object.entries(metafile.outputs ?? {})) {
        if (!outputPath.endsWith('.js')) {
            continue;
        }
        for (const [inputPath, input] of Object.entries(output.inputs ?? {})) {
            // esbuild lists inputs that end up with no code in the output; they are not bundled.
            if (!input.bytesInOutput) {
                continue;
            }
            let match;
            let last;
            bundledPackagePattern.lastIndex = 0;
            while ((match = bundledPackagePattern.exec(inputPath)) !== null) {
                last = match;
            }
            if (last) {
                const [, , version, name] = last;
                if (!versionsByName.has(name)) {
                    versionsByName.set(name, new Set());
                }
                versionsByName.get(name).add(version);
            }
        }
    }
    return toDuplicateMap(versionsByName);
}

function toDuplicateMap(versionsByName) {
    const duplicates = new Map();
    for (const [name, versions] of versionsByName) {
        if (versions.size > 1) {
            duplicates.set(name, [...versions].sort());
        }
    }
    return duplicates;
}

/**
 * Compares duplicates with the allowlist and returns human readable problems; an empty list means
 * the check passed.
 *
 * For the lockfile the allowlist must match exactly: an entry that is no longer duplicated, or whose
 * versions changed, is a problem, so the list shrinks. With `bundle` set, the duplicates come from a
 * build, which can contain fewer versions than the lockfile: every bundled version must be one the
 * allowlist names, but not every allowlisted entry has to show up.
 */
export function evaluate(duplicates, allowlist, { bundle = false } = {}) {
    const problems = [];
    for (const [name, versions] of duplicates) {
        const allowed = allowlist[name];
        if (!allowed) {
            problems.push(
                `${name} resolves to ${versions.join(' and ')}. Align the versions (an override in pnpm-workspace.yaml, or a bump of the package that asks for the old one; \`pnpm why ${name}\` shows who). ` +
                    `If that is not possible yet, add it to duplicate-dependencies-allowlist.json with a reason.`,
            );
        } else if (bundle) {
            const unexpected = versions.filter((version) => !allowed.versions.includes(version));
            if (unexpected.length > 0) {
                problems.push(
                    `${name} is allowlisted for ${allowed.versions.join(' and ')} but ${unexpected.join(' and ')} is bundled as well. Review it and update the allowlist.`,
                );
            }
        } else if (JSON.stringify([...allowed.versions].sort()) !== JSON.stringify(versions)) {
            problems.push(`${name} is allowlisted for ${allowed.versions.join(' and ')} but now resolves to ${versions.join(' and ')}. Review it and update the allowlist.`);
        }
    }
    if (!bundle) {
        for (const name of Object.keys(allowlist)) {
            if (!duplicates.has(name)) {
                problems.push(`${name} is allowlisted but is no longer duplicated. Remove it from duplicate-dependencies-allowlist.json.`);
            }
        }
    }
    return problems;
}

function main() {
    const args = process.argv.slice(2);
    const statsIndex = args.indexOf('--stats');
    const statsFile = statsIndex >= 0 ? args[statsIndex + 1] : undefined;
    if (statsIndex >= 0 && !statsFile) {
        console.error('--stats needs the path of an esbuild metafile (browser-stats.json).');
        process.exit(2);
    }

    const lockfileIndex = args.indexOf('--lockfile');
    const lockfileFile = lockfileIndex >= 0 ? resolve(args[lockfileIndex + 1] ?? '') : defaultLockfile;
    if (lockfileIndex >= 0 && !args[lockfileIndex + 1]) {
        console.error('--lockfile needs the path of a pnpm-lock.yaml.');
        process.exit(2);
    }

    const allowlist = JSON.parse(readFileSync(defaultAllowlist, 'utf8'));
    const problems = evaluate(findDuplicates(parseLockfile(readFileSync(lockfileFile, 'utf8'))), allowlist);
    if (statsFile) {
        const bundled = findBundledDuplicates(JSON.parse(readFileSync(statsFile, 'utf8')));
        problems.push(...evaluate(bundled, allowlist, { bundle: true }).map((problem) => `bundle: ${problem}`));
    }

    if (problems.length > 0) {
        console.error('Duplicate client dependencies found:\n');
        for (const problem of problems) {
            console.error(`  - ${problem}`);
        }
        console.error('\nSee the "Duplicate dependencies" section of the client development guideline.');
        process.exit(1);
    }
    console.log(`No unexpected duplicate client dependencies (${Object.keys(allowlist).length} allowlisted).`);
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
    main();
}
