#!/usr/bin/env node
/**
 * Compares a freshly generated eager-chunk report (see analyze_eager_chunks.mjs) against a
 * baseline report -- in CI, whatever report a develop push most recently published (resolved
 * fresh every run via the GitHub API, see ci-build.yml's chunk-eager-quality job). There is no
 * committed baseline file: with as many PRs merging to develop daily as this repo has, a stale
 * committed snapshot is worse than none -- see README.md.
 *
 * Every route is classified, not just the ones that got worse -- this is a delta report, not a
 * pass/fail gate. A route is classified 'regressed' if EITHER eager chunk count grows by more
 * than CHUNK_COUNT_THRESHOLD, or eager bytes grow by more than BYTES_THRESHOLD_PCT (relative);
 * 'improved' on the same thresholds in the opposite direction; otherwise 'unchanged'. The
 * thresholds exist to guard against small/noisy diffs at either end, not to hide anything -- an
 * 'unchanged' route is only rolled into the summary count, though, not given its own table row:
 * with routes now auto-discovered (in the hundreds, not a hand-picked handful), a full table would
 * bury the ones that actually need a look under a wall of routes nothing happened to.
 *
 * Usage:
 *   node diff_eager_chunks.mjs <fresh-report.json> --baseline <baseline-report.json> [--out <report.md>]
 */

import { readFileSync, writeFileSync } from 'node:fs';
import { resolve } from 'node:path';

const CHUNK_COUNT_THRESHOLD = 3; // absolute: allow small drift (e.g. a shared vendor chunk split differently)
const BYTES_THRESHOLD_PCT = 0.1; // relative: classify as changed if eager bytes moved more than 10%

function requireOptionValue(args, index, option) {
    const value = args[index + 1];
    if (!value || value.startsWith('--')) {
        console.error(`Missing value for ${option}.`);
        process.exit(1);
    }
    return value;
}

function parseArgs(argv) {
    const args = argv.slice(2);
    let reportPathArg, baselinePathArg, outPathArg;
    for (let i = 0; i < args.length; i++) {
        if (args[i] === '--baseline') {
            baselinePathArg = requireOptionValue(args, i, '--baseline');
            i++;
        } else if (args[i] === '--out') {
            outPathArg = requireOptionValue(args, i, '--out');
            i++;
        } else if (!args[i].startsWith('--') && !reportPathArg) reportPathArg = args[i];
    }
    if (!baselinePathArg) {
        console.error('Missing required --baseline <baseline-report.json>. There is no default baseline file -- see README.md.');
        process.exit(1);
    }
    return {
        reportPath: reportPathArg ? resolve(reportPathArg) : null,
        baselinePath: resolve(baselinePathArg),
        outPath: outPathArg ? resolve(outPathArg) : null,
    };
}

function loadJson(path) {
    return JSON.parse(readFileSync(path, 'utf8'));
}

/**
 * A long list of changed chunks is mostly one static import's dependency tree, so the size-sorted
 * list buries the actual cause. The roots are the changed chunks that the rest of the change hangs
 * off -- candidates are changed chunks an UNCHANGED chunk (or the route's own chunk) imports, and
 * the greedy pick below keeps the ones that explain the most changed bytes -- each with the changed
 * subtree it drags in behind it. Needs the per-chunk `parents` edges, which reports from before
 * they were recorded lack; those yield no roots and the plain list is shown instead.
 */
function findRoots(route, changedKeys) {
    if (!route.chunks.every((c) => Array.isArray(c.parents))) return [];
    const byFile = new Map(route.chunks.map((c) => [c.chunk, c]));
    const isChanged = (file) => changedKeys.has(byFile.get(file).moduleKey);
    const children = new Map(route.chunks.map((c) => [c.chunk, []]));
    for (const c of route.chunks) {
        for (const parent of c.parents) children.get(parent).push(c.chunk);
    }

    const roots = [];
    for (const c of route.chunks.filter((chunk) => isChanged(chunk.chunk))) {
        const unchangedParents = c.parents.filter((p) => !isChanged(p));
        if (c.parents.length > 0 && unchangedParents.length === 0) continue; // only reached through another changed chunk

        const subtree = new Set([c.chunk]);
        const stack = [c.chunk];
        while (stack.length > 0) {
            for (const child of children.get(stack.pop())) {
                if (isChanged(child) && !subtree.has(child)) {
                    subtree.add(child);
                    stack.push(child);
                }
            }
        }
        roots.push({
            chunk: c.chunk,
            topModules: c.topModules ?? [],
            moreModules: c.moreModules ?? 0,
            via:
                unchangedParents.length > 0
                    ? unchangedParents.map((p) => (p === route.entryChunk ? route.route : (byFile.get(p).topModules?.[0] ?? p)))
                    : ["the route's own chunk"],
            subtree,
        });
    }

    // esbuild lists every chunk a chunk needs, not only the immediate ones, so a route's own chunk
    // is a direct parent of nearly everything and "has an unchanged parent" alone cannot separate
    // the cause from its dependencies. What does separate them is reach: the chunk at the top of
    // the added tree needs the most other changed chunks. Pick greedily -- the root covering the most
    // not-yet-explained bytes, then the next for whatever is left -- so each chunk is attributed to
    // exactly one root and independent causes still show up as separate entries.
    const explained = new Set();
    const chosen = [];
    for (let remaining = roots; remaining.length > 0;) {
        const scored = remaining.map((root) => {
            const files = [...root.subtree].filter((file) => !explained.has(file));
            return { root, files, bytes: files.reduce((sum, file) => sum + byFile.get(file).bytes, 0) };
        });
        const best = scored.reduce((a, b) => (b.bytes > a.bytes ? b : a));
        if (best.files.length === 0) break;
        best.files.forEach((file) => explained.add(file));
        const { subtree: _subtree, ...root } = best.root;
        chosen.push({ ...root, subtreeCount: best.files.length, subtreeBytes: best.bytes });
        remaining = remaining.filter((r) => r !== best.root);
    }
    return chosen;
}

function diffRoute(routeName, baselineRoute, freshRoute) {
    if (!baselineRoute || baselineRoute.error) return { route: routeName, skipped: baselineRoute?.error ? `baseline error: ${baselineRoute.error}` : 'no baseline data' };
    if (!freshRoute || freshRoute.error) return { route: routeName, skipped: freshRoute?.error ? `fresh error: ${freshRoute.error}` : 'no fresh data' };

    const chunkCountDelta = freshRoute.eagerChunkCount - baselineRoute.eagerChunkCount;
    const bytesDelta = freshRoute.eagerBytes - baselineRoute.eagerBytes;
    const bytesDeltaPct = baselineRoute.eagerBytes > 0 ? bytesDelta / baselineRoute.eagerBytes : bytesDelta > 0 ? Infinity : 0;

    // Match by moduleKey (stable set of source inputs), not filename (content-hashed, changes
    // on every build regardless of whether this particular chunk's content actually changed).
    const baselineModuleKeys = new Set(baselineRoute.chunks.map((c) => c.moduleKey));
    const freshModuleKeys = new Set(freshRoute.chunks.map((c) => c.moduleKey));
    const newChunks = freshRoute.chunks.filter((c) => !baselineModuleKeys.has(c.moduleKey)).sort((a, b) => b.bytes - a.bytes);
    const removedChunks = baselineRoute.chunks.filter((c) => !freshModuleKeys.has(c.moduleKey)).sort((a, b) => b.bytes - a.bytes);

    const regressed = chunkCountDelta > CHUNK_COUNT_THRESHOLD || bytesDeltaPct > BYTES_THRESHOLD_PCT;
    const improved = chunkCountDelta < -CHUNK_COUNT_THRESHOLD || bytesDeltaPct < -BYTES_THRESHOLD_PCT;
    const status = regressed ? 'regressed' : improved ? 'improved' : 'unchanged';

    return {
        route: freshRoute.route,
        status,
        baseline: { eagerChunkCount: baselineRoute.eagerChunkCount, eagerBytes: baselineRoute.eagerBytes },
        fresh: { eagerChunkCount: freshRoute.eagerChunkCount, eagerBytes: freshRoute.eagerBytes },
        chunkCountDelta,
        bytesDelta,
        bytesDeltaPct,
        newChunks,
        removedChunks,
        newRoots: findRoots(freshRoute, new Set(newChunks.map((c) => c.moduleKey))),
        removedRoots: findRoots(baselineRoute, new Set(removedChunks.map((c) => c.moduleKey))),
    };
}

function fmtKB(bytes) {
    return `${(bytes / 1024).toFixed(1)} KB`;
}

function fmtPct(pct) {
    if (!Number.isFinite(pct)) return 'new';
    return `${pct >= 0 ? '+' : ''}${(pct * 100).toFixed(1)}%`;
}

const STATUS_MARKER = { regressed: '⚠️', improved: '✅', unchanged: '➖' };

/**
 * One drill-down bullet. The content-hashed filename alone says nothing about why a chunk is
 * there, so the largest source modules are appended. Reports from before that field existed
 * (an older baseline) simply omit it.
 */
function chunkLine(c) {
    const modules = (c.topModules ?? []).map((m) => `\`${m}\``).join(', ');
    const more = c.moreModules > 0 ? ` (+${c.moreModules} more)` : '';
    return `- \`${c.chunk}\`${c.entryPoint ? ` (${c.entryPoint})` : ''} — ${fmtKB(c.bytes)}${modules ? ` — ${modules}${more}` : ''}`;
}

const MAX_LISTED_ROOTS = 5;
const MAX_LISTED_CHUNKS = 15;

/**
 * Renders one route's drill-down. When the reports carry import edges, the roots (the static
 * imports that caused the change) come first and the full size-sorted chunk list is folded away;
 * without edges (an older baseline) only the plain list is shown, as before.
 */
function renderDrillDown(lines, heading, rootsLabel, roots, chunks) {
    lines.push('');
    if (roots.length === 0) {
        lines.push(`${heading}:`);
        appendChunkList(lines, chunks);
        return;
    }
    lines.push(`${heading}, through ${roots.length} static import(s):`);
    for (const r of roots.slice(0, MAX_LISTED_ROOTS)) {
        const modules = r.topModules.map((m) => `\`${m}\``).join(', ');
        const more = r.moreModules > 0 ? ` (+${r.moreModules} more)` : '';
        const via = [...new Set(r.via)].map((v) => `\`${v}\``).join(', ');
        lines.push(`- ${rootsLabel} ${via}: ${modules}${more} (\`${r.chunk}\`) — ${r.subtreeCount} chunk(s), ${fmtKB(r.subtreeBytes)} including its dependencies`);
    }
    if (roots.length > MAX_LISTED_ROOTS) {
        lines.push(`- _...and ${roots.length - MAX_LISTED_ROOTS} more._`);
    }
    lines.push('');
    lines.push(`<details><summary>All ${chunks.length} chunk(s), largest first</summary>`);
    lines.push('');
    appendChunkList(lines, chunks);
    lines.push('');
    lines.push('</details>');
}

function appendChunkList(lines, chunks) {
    for (const c of chunks.slice(0, MAX_LISTED_CHUNKS)) {
        lines.push(chunkLine(c));
    }
    if (chunks.length > MAX_LISTED_CHUNKS) {
        lines.push(`- _...and ${chunks.length - MAX_LISTED_CHUNKS} more._`);
    }
}

function toMarkdown(diffs, meta) {
    const regressed = diffs.filter((d) => d.status === 'regressed');
    const improved = diffs.filter((d) => d.status === 'improved');
    const unchanged = diffs.filter((d) => d.status === 'unchanged');
    const skipped = diffs.filter((d) => d.skipped);
    const lines = [];
    lines.push('### Client Eager-Chunk Loading Report');

    const summary = [];
    if (regressed.length) summary.push(`⚠️ ${regressed.length} regression(s)`);
    if (improved.length) summary.push(`✅ ${improved.length} improvement(s)`);
    if (unchanged.length) summary.push(`➖ ${unchanged.length} unchanged`);
    if (skipped.length) summary.push(`${skipped.length} skipped`);
    lines.push(summary.length ? summary.join(', ') + ` (of ${diffs.length} routes).` : '➖ No meaningful change on any route.');

    lines.push('');
    lines.push(`_Baseline: \`${meta.sourceBranch}\` @ \`${meta.sourceCommit}\`._`);

    // Routes now number in the hundreds (auto-discovered, not a hand-curated handful) -- listing
    // every unchanged route in the table would bury the routes that actually need a look. Only
    // regressed/improved/skipped rows earn a table row; unchanged ones are just a count above.
    const tableWorthy = diffs.filter((d) => d.status !== 'unchanged');
    if (tableWorthy.length) {
        lines.push('');
        lines.push('| | Route | Eager chunks | Eager size | Δ chunks | Δ size |');
        lines.push('|---|---|---|---|---|---|');
        for (const d of tableWorthy) {
            if (d.skipped) {
                lines.push(`| | ${d.route} | - | - | _${d.skipped}_ | - |`);
                continue;
            }
            lines.push(
                `| ${STATUS_MARKER[d.status]} | ${d.route} | ${d.fresh.eagerChunkCount} | ${fmtKB(d.fresh.eagerBytes)} | ${d.chunkCountDelta >= 0 ? '+' : ''}${d.chunkCountDelta} | ${fmtKB(d.bytesDelta)} (${fmtPct(d.bytesDeltaPct)}) |`,
            );
        }
    }

    for (const d of regressed) {
        if (d.newChunks.length === 0) continue; // size-only regression, nothing new to list
        renderDrillDown(lines, `**${d.route}** — ${d.newChunks.length} new chunk(s) became eager-reachable`, 'Pulled in by', d.newRoots, d.newChunks);
    }

    for (const d of improved) {
        if (d.removedChunks.length === 0) continue; // size-only improvement, nothing removed to list
        renderDrillDown(lines, `**${d.route}** — ${d.removedChunks.length} chunk(s) are no longer eager-reachable`, 'No longer pulled in by', d.removedRoots, d.removedChunks);
    }

    return lines.join('\n');
}

function main() {
    const { reportPath, baselinePath, outPath } = parseArgs(process.argv);
    if (!reportPath) {
        console.error('Usage: node diff_eager_chunks.mjs <fresh-report.json> --baseline <baseline-report.json> [--out <report.md>]');
        process.exit(1);
    }

    const fresh = loadJson(reportPath);
    const baseline = loadJson(baselinePath);

    const freshByRoute = Object.fromEntries(fresh.routes.map((r) => [r.route, r]));
    const baselineByRoute = Object.fromEntries(baseline.routes.map((r) => [r.route, r]));
    const allRoutes = [...new Set([...Object.keys(freshByRoute), ...Object.keys(baselineByRoute)])];

    const diffs = allRoutes.map((route) => diffRoute(route, baselineByRoute[route], freshByRoute[route]));
    const markdown = toMarkdown(diffs, baseline.meta);

    if (outPath) {
        writeFileSync(outPath, markdown);
        console.log(`Markdown report written to ${outPath}`);
    }
    console.log(markdown);

    // Exit code reflects regressions only (never improvements) -- kept mainly so this is usable
    // as a merge gate later if desired; ci-build.yml's chunk-eager-quality job currently ignores
    // it (continue-on-error: true) and treats this as advisory reporting, not a gate.
    process.exitCode = diffs.some((d) => d.status === 'regressed') ? 1 : 0;
}

main();
