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
import { join, basename } from 'path';

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

function scanMaps(dir) {
    const shipped = new Map(); // 'name@version' -> { name, version }
    const seenNames = new Set();
    if (!existsSync(dir)) {
        console.error(`sbom filter: build directory not found: ${dir}`);
        process.exit(1);
    }
    const entries = readdirSync(dir).filter((f) => f.endsWith('.js.map') || f.endsWith('.css.map'));
    if (entries.length === 0) {
        console.error(`sbom filter: no .js.map / .css.map files in ${dir} — run the production webapp build first.`);
        process.exit(1);
    }
    for (const file of entries) {
        let sm;
        try {
            sm = JSON.parse(readFileSync(join(dir, file), 'utf8'));
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
    return { shipped, seenNames, mapCount: entries.length };
}

function fullName(component) {
    return component.group ? `${component.group}/${component.name}` : component.name;
}

const { shipped, seenNames, mapCount } = scanMaps(buildDir);
const sbom = JSON.parse(readFileSync(inputPath, 'utf8'));

const keptRefs = new Set();
const newComponents = [];
for (const comp of sbom.components || []) {
    const key = `${fullName(comp)}@${comp.version}`;
    if (shipped.has(key)) {
        keptRefs.add(comp['bom-ref']);
        newComponents.push(comp);
        continue;
    }
    // Fallback for non-semver-like versions (URL- or tarball-installed packages)
    // where the source map and cdxgen disagree on the version string but the
    // package name is unambiguous in both.
    const name = fullName(comp);
    if (seenNames.has(name)) {
        const sameName = (sbom.components || []).filter((c) => fullName(c) === name);
        if (sameName.length === 1) {
            keptRefs.add(comp['bom-ref']);
            newComponents.push(comp);
        }
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
sbom.compositions = sbom.compositions || [];
if (metadataRef) {
    sbom.compositions.push({ 'bom-ref': metadataRef, aggregate: 'complete' });
}

writeFileSync(outputPath, JSON.stringify(sbom, null, 2));

const missing = [...shipped.values()].filter(
    (s) => !newComponents.some((c) => fullName(c) === s.name && c.version === s.version),
);
console.log(
    `sbom filter: ${mapCount} maps scanned, ${shipped.size} shipped packages, ${newComponents.length} components kept (${missing.length} shipped pkgs not in input SBOM)`,
);
if (missing.length > 0 && process.env.SBOM_FILTER_VERBOSE) {
    for (const m of missing) console.error(`  shipped but not in input SBOM: ${m.name}@${m.version}`);
}
