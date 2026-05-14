#!/usr/bin/env node

/**
 * Filters a cdxgen-generated CycloneDX SBOM down to the packages that survived
 * tree-shaking in the production Angular bundle.
 *
 * The Angular esbuild source maps (and CSS maps) list every input file that
 * contributed to each shipped chunk, including the exact pnpm-store path of
 * every transitive dependency that was kept. We extract (name, version) tuples
 * from those paths and intersect with the components in the input SBOM,
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
import { join } from 'path';

const [, , inputPath, outputPath, buildDir] = process.argv;
if (!inputPath || !outputPath || !buildDir) {
    console.error('usage: sbom-filter-by-bundle.mjs <input.json> <output.json> <build-dir>');
    process.exit(2);
}

const PNPM_ID_RE = /\.pnpm\/([^/]+)\/node_modules\//;

function parsePnpmId(eid) {
    // `@angular+core@21.2.12_<peer hash>` -> { name: '@angular/core', version: '21.2.12' }
    const m = eid.match(/^((?:@[^@+]+\+)?[^@+]+)@([^_(]+)/);
    if (!m) return null;
    return { name: m[1].replace('+', '/'), version: m[2] };
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
        } catch {
            continue;
        }
        for (const src of sm.sources || []) {
            if (!src.includes('/.pnpm/')) continue;
            const m = src.match(PNPM_ID_RE);
            if (!m) continue;
            const parsed = parsePnpmId(m[1]);
            if (!parsed) continue;
            seenNames.add(parsed.name);
            shipped.set(`${parsed.name}@${parsed.version}`, parsed);
        }
    }
    return { shipped, seenNames, mapCount: mapPaths.length };
}

function fullName(component) {
    return component.group ? `${component.group}/${component.name}` : component.name;
}

// URL- and tarball-installed packages render different version strings in the
// pnpm source-map path vs. in cdxgen's component (e.g. cdxgen reports xlsx
// version `xlsx-0.20.3.tgz` while the source map sees the full CDN URL). For
// those we accept a unique-by-name match. For semver-shaped versions, version
// drift means a stale build — failing loud is correct.
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

const keptRefs = new Set();
const newComponents = [];
const fallbackMatches = [];
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
    if (sameName.length !== 1) continue;
    // We have a single cdxgen component for a name we saw in source maps but
    // with a different version. Accept only if the version is opaque (URL /
    // tarball) — those legitimately disagree in representation. Otherwise this
    // is build / lockfile drift and we must not silently substitute.
    if (isOpaqueVersion(comp.version)) {
        keptRefs.add(comp['bom-ref']);
        newComponents.push(comp);
        fallbackMatches.push({ name, version: comp.version });
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
if (metadataRef) {
    sbom.compositions = (sbom.compositions || []).filter((c) => c['bom-ref'] !== metadataRef);
    sbom.compositions.push(newComposition);
}

writeFileSync(outputPath, JSON.stringify(sbom, null, 2));

const missing = [...shipped.values()].filter(
    (s) => !newComponents.some((c) => fullName(c) === s.name && c.version === s.version),
);

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
