#!/usr/bin/env node
/**
 * Compares a freshly generated eager-chunk report (see analyze_eager_chunks.mjs) against a
 * baseline report -- in CI, whatever report a develop push most recently published (resolved
 * fresh every run via the GitHub API, see ci-build.yml's chunk-eager-quality job). There is no
 * committed baseline file: with as many PRs merging to develop daily as this repo has, a stale
 * committed snapshot is worse than none -- see README.md.
 *
 * Every route is reported, not just the ones that got worse -- this is a delta report, not a
 * pass/fail gate. A route is classified 'regressed' if EITHER eager chunk count grows by more
 * than CHUNK_COUNT_THRESHOLD, or eager bytes grow by more than BYTES_THRESHOLD_PCT (relative);
 * 'improved' on the same thresholds in the opposite direction; otherwise 'unchanged'. The
 * thresholds exist to guard against small/noisy diffs at either end, not to hide anything -- the
 * full numbers are always in the table regardless of classification.
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

function toMarkdown(diffs, meta) {
    const regressed = diffs.filter((d) => d.status === 'regressed');
    const improved = diffs.filter((d) => d.status === 'improved');
    const lines = [];
    lines.push('### Client Eager-Chunk Loading Report');

    const summary = [];
    if (regressed.length) summary.push(`⚠️ ${regressed.length} regression(s)`);
    if (improved.length) summary.push(`✅ ${improved.length} improvement(s)`);
    lines.push(summary.length ? summary.join(', ') + '.' : '➖ No meaningful change on any route.');

    lines.push('');
    lines.push(`_Baseline: \`${meta.sourceBranch}\` @ \`${meta.sourceCommit}\`._`);
    lines.push('');
    lines.push('| | Route | Eager chunks | Eager size | Δ chunks | Δ size |');
    lines.push('|---|---|---|---|---|---|');
    for (const d of diffs) {
        if (d.skipped) {
            lines.push(`| | ${d.route} | - | - | _${d.skipped}_ | - |`);
            continue;
        }
        lines.push(
            `| ${STATUS_MARKER[d.status]} | ${d.route} | ${d.fresh.eagerChunkCount} | ${fmtKB(d.fresh.eagerBytes)} | ${d.chunkCountDelta >= 0 ? '+' : ''}${d.chunkCountDelta} | ${fmtKB(d.bytesDelta)} (${fmtPct(d.bytesDeltaPct)}) |`,
        );
    }

    for (const d of regressed) {
        if (d.newChunks.length === 0) continue; // size-only regression, nothing new to list
        lines.push('');
        lines.push(`**${d.route}** — ${d.newChunks.length} new chunk(s) became eager-reachable:`);
        for (const c of d.newChunks.slice(0, 15)) {
            lines.push(`- \`${c.chunk}\`${c.entryPoint ? ` (${c.entryPoint})` : ''} — ${fmtKB(c.bytes)}`);
        }
        if (d.newChunks.length > 15) {
            lines.push(`- _...and ${d.newChunks.length - 15} more._`);
        }
    }

    for (const d of improved) {
        if (d.removedChunks.length === 0) continue; // size-only improvement, nothing removed to list
        lines.push('');
        lines.push(`**${d.route}** — ${d.removedChunks.length} chunk(s) are no longer eager-reachable:`);
        for (const c of d.removedChunks.slice(0, 15)) {
            lines.push(`- \`${c.chunk}\`${c.entryPoint ? ` (${c.entryPoint})` : ''} — ${fmtKB(c.bytes)}`);
        }
        if (d.removedChunks.length > 15) {
            lines.push(`- _...and ${d.removedChunks.length - 15} more._`);
        }
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
