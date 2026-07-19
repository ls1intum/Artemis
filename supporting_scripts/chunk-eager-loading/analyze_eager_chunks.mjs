#!/usr/bin/env node
/**
 * Computes, for a curated list of route-entry components, the set of output chunks that
 * download EAGERLY the instant that route's own chunk loads -- i.e. everything reachable by
 * following only static ("import-statement") edges in the esbuild build graph, stopping at
 * "dynamic-import" (Angular loadComponent()/import()) and "url-token" (asset reference) edges.
 *
 * This is the automated form of the manual DevTools chunk-count check that caught PR #13027's
 * bug: a component statically importing another component it ALSO declares as a lazy route
 * silently pulls that component's whole subtree into the eager chunk graph.
 *
 * Input is an esbuild metafile (angular.json "statsJson": true build option), which has the
 * shape { inputs: {...}, outputs: { [chunkFile]: { bytes, entryPoint?, imports: [{path, kind}] } } }.
 *
 * Usage:
 *   node analyze_eager_chunks.mjs <path-to-stats.json> [--out <report.json>]
 */

import { readFileSync, writeFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { execFileSync } from 'node:child_process';

// Route name -> source entry file (as it appears in outputs[x].entryPoint), relative to repo root.
// Extend this list as more routes are worth guarding.
const ROUTE_ENTRIES = {
    'course-overview': 'src/main/webapp/app/course/overview/course-overview/course-overview.component.ts',
    'course-management-container': 'src/main/webapp/app/course/manage/course-management-container/course-management-container.component.ts',
};

const EAGER_KIND = 'import-statement';

function parseArgs(argv) {
    const args = argv.slice(2);
    const statsPathArg = args.find((a) => !a.startsWith('--'));
    const outIndex = args.indexOf('--out');
    return {
        statsPath: statsPathArg ? resolve(statsPathArg) : null,
        outPath: outIndex >= 0 ? resolve(args[outIndex + 1]) : null,
    };
}

function loadMetafile(statsPath) {
    const raw = readFileSync(statsPath, 'utf8');
    return JSON.parse(raw);
}

/**
 * Finds the output chunk that contains the given source file. Prefers `entryPoint` match when
 * present (some build configs emit it), but falls back to scanning each chunk's `inputs` map --
 * `entryPoint` isn't reliably present across esbuild/Angular versions, `inputs` always is.
 * If multiple chunks contain the source (unexpected but possible), picks the smallest -- the
 * dedicated per-component chunk, not an accidental merge into something larger like main.js.
 */
function findEntryChunk(outputs, entrySourcePath) {
    const byEntryPoint = Object.entries(outputs).find(([file, out]) => file.endsWith('.js') && out.entryPoint === entrySourcePath);
    if (byEntryPoint) return byEntryPoint;

    const candidates = Object.entries(outputs).filter(([file, out]) => file.endsWith('.js') && out.inputs && Object.prototype.hasOwnProperty.call(out.inputs, entrySourcePath));
    if (candidates.length === 0) return undefined;
    candidates.sort((a, b) => a[1].bytes - b[1].bytes);
    return candidates[0];
}

/** BFS over static-only edges starting at `startChunk`. Returns the reachable chunk file names (including start). */
function computeEagerSet(outputs, startChunk) {
    const visited = new Set([startChunk]);
    const queue = [startChunk];

    while (queue.length > 0) {
        const current = queue.shift();
        const out = outputs[current];
        if (!out || !out.imports) continue;

        for (const imp of out.imports) {
            if (imp.kind !== EAGER_KIND) continue; // stop at dynamic-import / url-token boundaries
            if (visited.has(imp.path)) continue;
            if (!outputs[imp.path]) continue; // import target isn't a tracked output (e.g. external)
            visited.add(imp.path);
            queue.push(imp.path);
        }
    }

    return visited;
}

function analyzeRoute(outputs, routeName, entrySourcePath) {
    const match = findEntryChunk(outputs, entrySourcePath);
    if (!match) {
        return { route: routeName, entrySourcePath, error: 'entry chunk not found in metafile' };
    }
    const [entryChunk] = match;
    const eagerSet = computeEagerSet(outputs, entryChunk);

    const chunks = [...eagerSet]
        .map((file) => ({
            chunk: file,
            // Stable identity across builds: output filenames are content-hashed and change on
            // every commit even for logically unchanged chunks, so filename can't be used to
            // detect "is this chunk new" across builds. The set of source inputs is stable.
            moduleKey: Object.keys(outputs[file].inputs ?? {}).sort().join('|'),
            entryPoint: outputs[file].entryPoint ?? null,
            bytes: outputs[file].bytes,
        }))
        .sort((a, b) => b.bytes - a.bytes);

    const eagerBytes = chunks.reduce((sum, c) => sum + c.bytes, 0);

    return {
        route: routeName,
        entrySourcePath,
        entryChunk,
        eagerChunkCount: chunks.length,
        eagerBytes,
        chunks,
    };
}

function gitFallback(args) {
    try {
        return execFileSync('git', args, { encoding: 'utf8' }).trim();
    } catch {
        return null;
    }
}

/**
 * Every report embeds provenance -- which commit/branch it was built from -- because any report
 * this script produces might later be used as another run's baseline (see ci-build.yml's
 * chunk-eager-quality job: it diffs a PR's report against whatever develop's last report was).
 *
 * For `pull_request`-triggered runs, GITHUB_SHA/GITHUB_REF_NAME are NOT the PR's real head commit
 * or branch -- GitHub Actions checks out a synthetic merge commit for that event and sets those
 * two vars to describe *it* (ref name literally becomes "<pr-number>/merge"), confirmed by
 * inspecting a real run's uploaded artifact. CHUNK_REPORT_SHA/CHUNK_REPORT_BRANCH are explicit
 * overrides set by ci-build.yml's "Generate eager-chunk loading report" step (from
 * `inputs.commit_sha` / `github.head_ref`, which resolve correctly); GITHUB_SHA/GITHUB_REF_NAME
 * remain the fallback for push-triggered (develop) runs, which have no such synthetic ref.
 */
// `??` only falls through on null/undefined -- ci-build.yml sets CHUNK_REPORT_BRANCH from
// `github.head_ref`, which GitHub Actions evaluates to an empty string (not unset) on
// push-triggered runs, so a plain `??` chain would silently pick '' over the real fallback.
function firstNonEmpty(...values) {
    return values.find((v) => v != null && v !== '') ?? 'unknown';
}

function resolveMeta() {
    return {
        sourceCommit: firstNonEmpty(process.env.CHUNK_REPORT_SHA, process.env.GITHUB_SHA, gitFallback(['rev-parse', 'HEAD'])),
        sourceBranch: firstNonEmpty(process.env.CHUNK_REPORT_BRANCH, process.env.GITHUB_REF_NAME, gitFallback(['rev-parse', '--abbrev-ref', 'HEAD'])),
    };
}

function main() {
    const { statsPath, outPath } = parseArgs(process.argv);
    if (!statsPath) {
        console.error('Usage: node analyze_eager_chunks.mjs <path-to-stats.json> [--out <report.json>]');
        process.exit(1);
    }

    const metafile = loadMetafile(statsPath);
    const routes = Object.entries(ROUTE_ENTRIES).map(([routeName, entrySourcePath]) => analyzeRoute(metafile.outputs, routeName, entrySourcePath));

    const report = {
        generatedAt: new Date().toISOString(),
        source: statsPath,
        meta: resolveMeta(),
        routes,
    };

    const json = JSON.stringify(report, null, 2);
    if (outPath) {
        writeFileSync(outPath, json);
        console.log(`Report written to ${outPath}`);
    } else {
        console.log(json);
    }

    for (const r of routes) {
        if (r.error) {
            console.log(`\n[${r.route}] ERROR: ${r.error}`);
            continue;
        }
        console.log(`\n[${r.route}] eager chunks: ${r.eagerChunkCount}, eager bytes: ${(r.eagerBytes / 1024).toFixed(1)} KB`);
    }
}

main();
