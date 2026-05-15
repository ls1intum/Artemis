#!/usr/bin/env node

/**
 * Filters a cdxgen-generated CycloneDX SBOM down to the packages that survived
 * tree-shaking in the production Angular bundle.
 *
 * The Angular esbuild source maps (and CSS maps) list every input file that
 * contributed to each shipped chunk. With Bun's flat install layout each
 * `node_modules/<name>` path corresponds to a single installed version, which
 * we read from `<path>/package.json` to recover the (name, version) tuple.
 * We then intersect those tuples with the components in the input SBOM,
 * preserving the rich metadata (licenses, hashes, suppliers, purls) cdxgen
 * produced.
 *
 * Usage:
 *   node supporting_scripts/sbom-filter-by-bundle.mjs <input.json> <output.json> <build-dir>
 *
 * Fails with a non-zero exit if <build-dir> contains no source maps — that
 * means the webapp has not been built and the resulting SBOM would be invalid.
 */

import { readFileSync, writeFileSync, readdirSync, existsSync } from 'fs';
import { join, resolve } from 'path';

const [, , inputPath, outputPath, buildDir] = process.argv;
if (!inputPath || !outputPath || !buildDir) {
    console.error('usage: sbom-filter-by-bundle.mjs <input.json> <output.json> <build-dir>');
    process.exit(2);
}

const PROJECT_ROOT = process.cwd();

// Match every `node_modules/<name>` segment in a source path (scoped or not).
// Bun's flat layout means most paths have exactly one such segment, but nested
// `node_modules/` is still possible when peer conflicts force a private copy —
// we keep the *deepest* match in that case.
const NODE_MODULES_RE = /node_modules\/((?:@[^/]+\/)?[^/]+)(?:\/|$)/g;

function resolvePackageDir(src) {
    let last = null;
    NODE_MODULES_RE.lastIndex = 0;
    let m;
    while ((m = NODE_MODULES_RE.exec(src)) !== null) {
        const prefix = src.slice(0, m.index + 'node_modules/'.length + m[1].length);
        last = { name: m[1], dir: resolve(PROJECT_ROOT, prefix) };
    }
    return last;
}

const versionCache = new Map();
function readPackageVersion(dir) {
    if (versionCache.has(dir)) return versionCache.get(dir);
    const manifest = join(dir, 'package.json');
    if (!existsSync(manifest)) {
        versionCache.set(dir, null);
        return null;
    }
    try {
        const pkg = JSON.parse(readFileSync(manifest, 'utf8'));
        const entry = pkg.name && pkg.version ? { name: pkg.name, version: pkg.version } : null;
        versionCache.set(dir, entry);
        return entry;
    } catch {
        versionCache.set(dir, null);
        return null;
    }
}

function collectMaps(dir) {
    const out = [];
    for (const entry of readdirSync(dir, { withFileTypes: true })) {
        const path = join(dir, entry.name);
        if (entry.isDirectory()) {
            out.push(...collectMaps(path));
        } else if (entry.isFile() && (entry.name.endsWith('.js.map') || entry.name.endsWith('.css.map'))) {
            out.push(path);
        }
    }
    return out;
}

function scanMaps(dir) {
    const shipped = new Map(); // 'name@version' -> { name, version }
    const seenNames = new Set();
    if (!existsSync(dir)) {
        console.error(`sbom filter: build directory not found: ${dir}`);
        process.exit(1);
    }
    const mapPaths = collectMaps(dir);
    if (mapPaths.length === 0) {
        console.error(`sbom filter: no .js.map / .css.map files under ${dir} — run the production webapp build first.`);
        process.exit(1);
    }
    for (const path of mapPaths) {
        let sm;
        try {
            sm = JSON.parse(readFileSync(path, 'utf8'));
        } catch (err) {
            console.error(`sbom filter: failed to read source map ${path}: ${err.message}`);
            process.exit(1);
        }
        for (const src of sm.sources || []) {
            if (!src.includes('node_modules/')) continue;
            const resolved = resolvePackageDir(src);
            if (!resolved) continue;
            const pkg = readPackageVersion(resolved.dir);
            if (!pkg) continue;
            seenNames.add(pkg.name);
            shipped.set(`${pkg.name}@${pkg.version}`, pkg);
        }
    }
    return { shipped, seenNames, mapCount: mapPaths.length };
}

function fullName(component) {
    return component.group ? `${component.group}/${component.name}` : component.name;
}

// URL- and tarball-installed packages render different version strings in
// `node_modules/<name>/package.json` vs. in cdxgen's component (e.g. cdxgen
// reports xlsx version `xlsx-0.20.3.tgz` while package.json reports `0.20.3`).
// For those we accept a unique-by-name match. For semver-shaped versions,
// version drift means a stale build — failing loud is correct.
function isOpaqueVersion(version) {
    return /^https?[+:]|^git[+:]|\.(tgz|tar\.gz)$|^[a-z]+\+\+\+/i.test(version);
}

const { shipped, seenNames, mapCount } = scanMaps(buildDir);
const sbom = JSON.parse(readFileSync(inputPath, 'utf8'));

const componentsByName = new Map();
for (const comp of sbom.components || []) {
    const name = fullName(comp);
    const list = componentsByName.get(name) || [];
    list.push(comp);
    componentsByName.set(name, list);
}

const shippedByName = new Map();
for (const pkg of shipped.values()) {
    const list = shippedByName.get(pkg.name) || [];
    list.push(pkg);
    shippedByName.set(pkg.name, list);
}

const keptRefs = new Set();
const newComponents = [];
const fallbackMatches = [];
// Track fallback acceptances by the *shipped* (name, version) tuple, not by
// name alone. If a package has multiple opaque source-map versions, a single
// fallback can't legitimately cover both — claiming it by name would silently
// mask the second.
const fallbackMatchedKeys = new Set();
for (const comp of sbom.components || []) {
    const name = fullName(comp);
    const key = `${name}@${comp.version}`;
    if (shipped.has(key)) {
        keptRefs.add(comp['bom-ref']);
        newComponents.push(comp);
        continue;
    }
    if (!seenNames.has(name)) continue;
    const sameName = componentsByName.get(name) || [];
    const shippedWithSameName = shippedByName.get(name) || [];
    // Require exactly one component on both sides so the fallback unambiguously
    // pairs the shipped version to the cdxgen entry.
    if (sameName.length !== 1 || shippedWithSameName.length !== 1) continue;
    // We have a single cdxgen component for a name we saw in source maps but
    // with a different version. Accept only if the version is opaque (URL /
    // tarball) — those legitimately disagree in representation. Otherwise this
    // is build / lockfile drift and we must not silently substitute.
    if (isOpaqueVersion(comp.version)) {
        keptRefs.add(comp['bom-ref']);
        newComponents.push(comp);
        fallbackMatches.push({ name, version: comp.version });
        fallbackMatchedKeys.add(`${name}@${shippedWithSameName[0].version}`);
    }
}

const metadataRef = sbom.metadata?.component?.['bom-ref'];
if (metadataRef) keptRefs.add(metadataRef);

const newDependencies = (sbom.dependencies || [])
    .filter((d) => keptRefs.has(d.ref))
    .map((d) => ({
        ref: d.ref,
        dependsOn: (d.dependsOn || []).filter((r) => keptRefs.has(r)),
    }));

sbom.components = newComponents;
sbom.dependencies = newDependencies;
// Per CycloneDX 1.6, `complete` asserts no further relationships exist. We
// intentionally drop ~1500 components from the input, so the post-filter BOM
// is `incomplete_third_party_only`: first-party (`metadata.component` itself)
// is fully known; third-party components were filtered to a subset.
const newComposition = { 'bom-ref': metadataRef, aggregate: 'incomplete_third_party_only' };
// Prune ref arrays on any pre-existing compositions cdxgen emitted so they
// can't reference components we just dropped, and replace any composition the
// input already had for our metadata component.
const pruneRefs = (refs) => (refs || []).filter((r) => keptRefs.has(r));
sbom.compositions = (sbom.compositions || [])
    .filter((c) => c['bom-ref'] !== metadataRef)
    .map((c) => ({
        ...c,
        ...(c.assemblies ? { assemblies: pruneRefs(c.assemblies) } : {}),
        ...(c.dependencies ? { dependencies: pruneRefs(c.dependencies) } : {}),
        ...(c.vulnerabilities ? { vulnerabilities: pruneRefs(c.vulnerabilities) } : {}),
    }));
if (metadataRef) sbom.compositions.push(newComposition);

writeFileSync(outputPath, JSON.stringify(sbom, null, 2));

// A shipped package counts as "missing" only if neither an exact (name, version)
// match nor a per-tuple opaque-version fallback claimed it. Opaque-version
// source-map versions (e.g. `https+++cdn.../xlsx-0.20.3.tgz`) never match
// cdxgen's version string (`xlsx-0.20.3.tgz`), so the matching fallback tuple
// must be excluded from the gap list to avoid false positives that would
// fail CI.
const exactMatches = new Set(newComponents.map((c) => `${fullName(c)}@${c.version}`));
const missing = [...shipped.values()].filter((s) => {
    const tupleKey = `${s.name}@${s.version}`;
    if (exactMatches.has(tupleKey)) return false;
    if (fallbackMatchedKeys.has(tupleKey)) return false;
    return true;
});

console.log(
    `sbom filter: ${mapCount} maps scanned, ${shipped.size} shipped packages, ${newComponents.length} components kept`,
);

if (fallbackMatches.length > 0) {
    console.warn(`sbom filter: ${fallbackMatches.length} opaque-version fallback match(es):`);
    for (const m of fallbackMatches) console.warn(`  ${m.name}@${m.version}`);
}

if (missing.length > 0) {
    console.error(`sbom filter: ${missing.length} shipped package(s) not found in input SBOM:`);
    for (const m of missing) console.error(`  ${m.name}@${m.version}`);
    process.exit(1);
}
