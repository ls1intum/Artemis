#!/usr/bin/env node
/**
 * Compares a freshly generated eager-chunk report (see analyze_eager_chunks.mjs) against a
 * baseline report -- in CI, the report of the develop commit a pull request was cut from (resolved
 * fresh every run via the GitHub API, see ci-build.yml's chunk-eager-quality job). There is no
 * committed baseline file: with as many PRs merging to develop daily as this repo has, a stale
 * committed snapshot is worse than none -- see README.md.
 *
 * Every route is classified, not just the ones that got worse -- this is a delta report, not a
 * pass/fail gate. A route is 'regressed' if EITHER its eager chunk count grows by more than
 * CHUNK_COUNT_THRESHOLD, or its eager bytes grow by more than BYTES_THRESHOLD_PCT (relative);
 * 'improved' on the same thresholds in the opposite direction; otherwise 'unchanged'. Unchanged
 * routes are only counted in the summary line: with routes auto-discovered (in the hundreds), a
 * full table would bury the ones that need a look.
 *
 * Two documents come out of one comparison:
 *   --out        the PR comment (markdown): route table, per route a fold naming the imports that
 *                caused the change and the largest modules each brings along, within --max-chars.
 *   --full-out   the full report as a standalone HTML page for people: every import, every module,
 *                every chunk and the unchanged routes. Nothing is shortened.
 * In the comment the route table is an HTML table inside markdown (a pipe table cannot hold a fold or a
 * cell that spans columns) and the open folds are text lines in a monospace block, because GitHub
 * stripes and borders every table row and strips styling.
 *
 * Usage:
 *   node diff_eager_chunks.mjs <fresh-report.json> --baseline <baseline-report.json>
 *     [--out <comment.md>] [--full-out <full-report.html>] [--max-chars <n>]
 */

import { readFileSync, writeFileSync } from 'node:fs';
import { resolve } from 'node:path';

const USAGE = 'node diff_eager_chunks.mjs <fresh-report.json> --baseline <baseline-report.json> [--out <comment.md>] [--full-out <full-report.html>] [--max-chars <n>]';

const REPORT_FORMAT_VERSION = 2; // must match analyze_eager_chunks.mjs
const CHUNK_COUNT_THRESHOLD = 3; // absolute: allow small drift (e.g. a shared vendor chunk split differently)
const BYTES_THRESHOLD_PCT = 0.1; // relative: classify as changed if eager bytes moved more than 10%

// GitHub rejects a comment body over 65,536 characters. The workflow appends a short footer.
const DEFAULT_MAX_CHARS = 60_000;
// Modules listed per import in the comment. The full report lists all of them.
const COMMENT_MODULES_PER_IMPORT = 5;
// Imports that explain less than this are not worth a row (usually a tiny helper in a chunk of its own).
const MIN_LISTED_IMPORT_BYTES = 1024;
// Imports named in a fold's summary line before "and N more".
const SUMMARY_IMPORT_NAMES = 3;

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
    let reportPathArg, baselinePathArg, outPathArg, fullOutPathArg;
    let maxChars = DEFAULT_MAX_CHARS;
    for (let i = 0; i < args.length; i++) {
        if (args[i] === '--baseline') {
            baselinePathArg = requireOptionValue(args, i, '--baseline');
            i++;
        } else if (args[i] === '--out') {
            outPathArg = requireOptionValue(args, i, '--out');
            i++;
        } else if (args[i] === '--full-out') {
            fullOutPathArg = requireOptionValue(args, i, '--full-out');
            i++;
        } else if (args[i] === '--max-chars') {
            maxChars = Number(requireOptionValue(args, i, '--max-chars'));
            if (!Number.isInteger(maxChars) || maxChars < 1000) {
                console.error('--max-chars must be an integer of at least 1000.');
                process.exit(1);
            }
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
        fullOutPath: fullOutPathArg ? resolve(fullOutPathArg) : null,
        maxChars,
    };
}

function loadReport(path) {
    const report = JSON.parse(readFileSync(path, 'utf8'));
    if (report.formatVersion !== REPORT_FORMAT_VERSION) {
        console.error(
            `${path} is a report in layout ${report.formatVersion ?? 1}, this script reads layout ${REPORT_FORMAT_VERSION}. Rebuild that report with the current analyze_eager_chunks.mjs.`,
        );
        process.exit(1);
    }
    return report;
}

// ---------------------------------------------------------------------------------------------
// Comparison
// ---------------------------------------------------------------------------------------------

const routeChunks = (report, route) => route.chunks.map((file) => ({ file, ...report.chunks[file] }));

/** The module that names an import: the largest project file in its chunk, else the largest module. */
function importName(chunk) {
    const app = chunk.modules.find((m) => m[2] === 1);
    return (app ?? chunk.modules[0])?.[0] ?? 'unnamed code';
}

/**
 * A long list of changed chunks is mostly one import's dependency tree, so the imports that caused
 * a change are what a reader needs. They are the changed chunks that the rest of the change hangs
 * off. esbuild lists every chunk a chunk needs, not only the immediate ones, so a route's own chunk
 * is a direct parent of nearly everything and "has an unchanged parent" alone cannot separate the
 * cause from its dependencies. What does separate them is reach: the chunk at the top of the added
 * tree needs the most other changed chunks. Pick greedily -- the root covering the most
 * not-yet-explained bytes, then the next for whatever is left -- so each chunk is attributed to
 * exactly one root and independent causes still show up as separate entries.
 */
function findRoots(report, route, changedKeys) {
    const info = (file) => report.chunks[file];
    const inRoute = new Set(route.chunks);
    const parents = new Map(route.chunks.map((file) => [file, []]));
    const children = new Map(route.chunks.map((file) => [file, []]));
    for (const file of route.chunks) {
        for (const target of info(file).imports) {
            if (!inRoute.has(target)) continue;
            parents.get(target).push(file);
            children.get(file).push(target);
        }
    }
    const isChanged = (file) => changedKeys.has(info(file).moduleKey);

    const candidates = [];
    for (const file of route.chunks.filter(isChanged)) {
        const ps = parents.get(file);
        if (ps.length > 0 && ps.every(isChanged)) continue; // only reached through another changed chunk
        const subtree = new Set([file]);
        const stack = [file];
        while (stack.length > 0) {
            for (const child of children.get(stack.pop())) {
                if (isChanged(child) && !subtree.has(child)) {
                    subtree.add(child);
                    stack.push(child);
                }
            }
        }
        candidates.push({ file, subtree });
    }

    const explained = new Set();
    const roots = [];
    for (let remaining = candidates; remaining.length > 0;) {
        const scored = remaining.map((candidate) => {
            const files = [...candidate.subtree].filter((file) => !explained.has(file));
            return { candidate, files, bytes: files.reduce((sum, file) => sum + info(file).bytes, 0) };
        });
        const best = scored.reduce((a, b) => (b.bytes > a.bytes ? b : a));
        if (best.files.length === 0) break;
        best.files.forEach((file) => explained.add(file));
        roots.push({ name: importName(info(best.candidate.file)), files: best.files, bytes: best.bytes });
        remaining = remaining.filter((candidate) => candidate !== best.candidate);
    }
    return roots;
}

/** One import with the modules it brings along, summed over its chunks, largest first. */
function describeImport(report, root) {
    const modules = new Map();
    for (const file of root.files) {
        for (const [name, bytes] of report.chunks[file].modules) modules.set(name, (modules.get(name) ?? 0) + bytes);
    }
    return {
        name: root.name,
        bytes: root.bytes,
        chunkCount: root.files.length,
        modules: [...modules.entries()].sort((a, b) => b[1] - a[1]).map(([name, bytes]) => ({ name, bytes })),
    };
}

function diffRoute(routeName, baseline, fresh, baselineRoute, freshRoute) {
    if (!baselineRoute || baselineRoute.error) return { route: routeName, skipped: baselineRoute?.error ? `baseline error: ${baselineRoute.error}` : 'no baseline data' };
    if (!freshRoute || freshRoute.error) return { route: routeName, skipped: freshRoute?.error ? `fresh error: ${freshRoute.error}` : 'no fresh data' };

    const chunkCountDelta = freshRoute.eagerChunkCount - baselineRoute.eagerChunkCount;
    const bytesDelta = freshRoute.eagerBytes - baselineRoute.eagerBytes;
    const bytesDeltaPct = baselineRoute.eagerBytes > 0 ? bytesDelta / baselineRoute.eagerBytes : bytesDelta > 0 ? Infinity : 0;

    // Match by moduleKey (stable set of source inputs), not filename (content-hashed, changes
    // on every build regardless of whether this particular chunk's content actually changed).
    const baselineKeys = new Set(routeChunks(baseline, baselineRoute).map((c) => c.moduleKey));
    const freshKeys = new Set(routeChunks(fresh, freshRoute).map((c) => c.moduleKey));
    const added = routeChunks(fresh, freshRoute).filter((c) => !baselineKeys.has(c.moduleKey));
    const removed = routeChunks(baseline, baselineRoute).filter((c) => !freshKeys.has(c.moduleKey));

    const regressed = chunkCountDelta > CHUNK_COUNT_THRESHOLD || bytesDeltaPct > BYTES_THRESHOLD_PCT;
    const improved = chunkCountDelta < -CHUNK_COUNT_THRESHOLD || bytesDeltaPct < -BYTES_THRESHOLD_PCT;
    const status = regressed ? 'regressed' : improved ? 'improved' : 'unchanged';

    // A regression is explained by what the fresh build added, an improvement by what the baseline had.
    const [report, route, changed] = improved && !regressed ? [baseline, baselineRoute, removed] : [fresh, freshRoute, added];
    const changedChunks = changed.sort((a, b) => b.bytes - a.bytes);
    let imports = status === 'unchanged' ? [] : findRoots(report, route, new Set(changed.map((c) => c.moduleKey))).map((root) => describeImport(report, root));
    const significant = imports.filter((i) => i.bytes >= MIN_LISTED_IMPORT_BYTES);
    imports = (significant.length > 0 ? significant : imports).sort((a, b) => b.bytes - a.bytes);

    return {
        route: routeName,
        status,
        baseline: { chunks: baselineRoute.eagerChunkCount, bytes: baselineRoute.eagerBytes },
        fresh: { chunks: freshRoute.eagerChunkCount, bytes: freshRoute.eagerBytes },
        chunkCountDelta,
        bytesDelta,
        bytesDeltaPct,
        imports,
        changedChunks,
    };
}

/** One order for every list: regressions (biggest growth first), then improvements (biggest saving first), then skipped routes. */
function sortDiffs(diffs) {
    const rank = (d) => (d.skipped ? 2 : d.status === 'regressed' ? 0 : d.status === 'improved' ? 1 : 3);
    return [...diffs].sort((a, b) => rank(a) - rank(b) || (a.skipped || b.skipped ? a.route.localeCompare(b.route) : Math.abs(b.bytesDelta) - Math.abs(a.bytesDelta)));
}

// ---------------------------------------------------------------------------------------------
// Rendering (HTML tables inside markdown, written for what GitHub keeps: no style, no class)
// ---------------------------------------------------------------------------------------------

const MARKER = { regressed: '⚠️', improved: '✅' };

const esc = (text) => String(text).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
const code = (text) => `<code>${esc(text)}</code>`;
const plural = (n, one, many) => `${n} ${n === 1 ? one : many}`;

function fmtSize(bytes) {
    const abs = Math.abs(bytes);
    if (abs >= 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
    return `${(bytes / 1024).toFixed(abs < 10 * 1024 ? 1 : 0)} KB`;
}
const fmtSizeSigned = (bytes) => `${bytes >= 0 ? '+' : '-'}${fmtSize(Math.abs(bytes))}`;
const fmtCountSigned = (n) => `${n >= 0 ? '+' : ''}${n}`;
function fmtPct(pct) {
    if (!Number.isFinite(pct)) return 'new';
    return `${pct >= 0 ? '+' : ''}${(pct * 100).toFixed(1)}%`;
}

const TABLE_HEAD =
    '<thead><tr><th></th><th align="left">Route</th><th align="right">Eager chunks</th><th align="right">Eager size</th><th align="right">Δ chunks</th><th align="right">Δ size</th></tr></thead>';

function routeRow(d) {
    if (d.skipped) return `<tr><td></td><td>${esc(d.route)}</td><td colspan="4"><em>${esc(d.skipped)}</em></td></tr>`;
    return `<tr><td>${MARKER[d.status]}</td><td><b>${esc(d.route)}</b></td><td align="right">${d.fresh.chunks}</td><td align="right">${fmtSize(d.fresh.bytes)}</td><td align="right">${fmtCountSigned(d.chunkCountDelta)}</td><td align="right">${fmtSizeSigned(d.bytesDelta)} (${fmtPct(d.bytesDeltaPct)})</td></tr>`;
}

function importNames(d) {
    const shown = d.imports.slice(0, SUMMARY_IMPORT_NAMES).map((i) => code(i.name));
    const rest = d.imports.length - shown.length;
    const joined = rest > 0 ? `${shown.join(', ')} and ${plural(rest, 'more', 'more')}` : shown.length > 1 ? `${shown.slice(0, -1).join(', ')} and ${shown.at(-1)}` : shown[0];
    return `${d.status === 'improved' ? 'no longer imports' : 'imports'} ${joined}`;
}

// Minimum width of a tree line in characters: names to the left, the size right-aligned at the end. Longer lines
// widen the whole block, so the sizes of one fold always sit in one column.
const TREE_MIN_WIDTH = 84;
const TREE_INDENT = '    ';

/**
 * The open fold as text lines in a monospace block: each import with its size, and under it its modules, every one
 * with an arrow. A block, not a table, because GitHub stripes and borders every table row and cannot be styled.
 * With padding, the sizes end at the same column on every line.
 */
function treeLines(d, modulesPerImport) {
    const entries = [];
    for (const imp of d.imports) {
        entries.push({ text: `↳ ${esc(imp.name)}`, length: imp.name.length + 2, size: fmtSize(imp.bytes), bold: true });
        for (const m of imp.modules.slice(0, modulesPerImport))
            entries.push({ text: `${TREE_INDENT}↳ ${esc(m.name)}`, length: TREE_INDENT.length + m.name.length + 2, size: fmtSize(m.bytes) });
        const rest = imp.modules.slice(modulesPerImport);
        if (rest.length > 0) {
            const label = `and ${plural(rest.length, 'smaller module', 'smaller modules')}`;
            entries.push({ text: `${TREE_INDENT}↳ <em>${label}</em>`, length: TREE_INDENT.length + label.length + 2, size: fmtSize(rest.reduce((s, m) => s + m.bytes, 0)) });
        }
    }
    const width = Math.max(TREE_MIN_WIDTH, ...entries.map((e) => e.length + 2 + e.size.length));
    return entries
        .map((e) => {
            const line = `${e.text}${' '.repeat(width - e.length - e.size.length)}${e.size}`;
            return e.bold ? `<b>${line}</b>` : line;
        })
        .join('\n');
}

// The fold spans every column, so a closed fold is one clean line under its route.
function foldRow(d, modulesPerImport, open = false) {
    if (d.imports.length === 0) return '';
    return `<tr><td colspan="6"><details${open ? ' open' : ''}><summary>${importNames(d)}</summary>\n<pre>\n${treeLines(d, modulesPerImport)}\n</pre>\n</details></td></tr>`;
}

const table = (rows) => `<table>\n${TABLE_HEAD}\n<tbody>\n${rows.join('\n')}\n</tbody>\n</table>`;

function summaryLine(counts, total) {
    const parts = [];
    if (counts.regressed) parts.push(`⚠️ ${counts.regressed} regression(s)`);
    if (counts.improved) parts.push(`✅ ${counts.improved} improvement(s)`);
    if (counts.unchanged) parts.push(`➖ ${counts.unchanged} unchanged`);
    if (counts.skipped) parts.push(`${counts.skipped} skipped`);
    return parts.length ? `${parts.join(', ')} (of ${total} routes).` : '➖ No meaningful change on any route.';
}

function legend(meta) {
    const base = `\`${meta.sourceBranch}\` @ \`${String(meta.sourceCommit).slice(0, 10)}\``;
    return `> A route's **eager chunks** are the JavaScript files its page downloads as soon as it opens, before the user does anything. This compares them with ${base}, the commit this branch was cut from.`;
}

const HOW_TO_READ = `<details><summary>How to read the table</summary>

- **Eager chunks / Eager size:** how many chunks, and how much code, the route loads up front. **Δ** is the change against the base. A route is flagged when it grows or shrinks by more than ${CHUNK_COUNT_THRESHOLD} chunks or ${BYTES_THRESHOLD_PCT * 100}% in size.
- **imports …** (the line under a route) names the files the route imports with a normal \`import\`, which is what pulled the extra code in. Open it to see them with their size and, indented under each, the largest things it brings along (libraries or your own modules).
- A chunk is a file the build splits the app into. Large changes usually come from one import, so look at the biggest import first.

</details>`;

const HOW_TO_FIX = `<details><summary>How to fix a regression</summary>

The usual cause is a component that imports another component with a normal \`import\`, although that component is also loaded lazily with \`loadComponent: () => import(…)\`. The static import makes the bundler ship it with the route.

- Remove the static import, so the component is only downloaded when the route that needs it is opened.
- If you only need a type or a constant from that file, move it to a small separate file and import that instead.
- If the import is wanted, accept the growth and say so in the PR description. The report then shows the cost.

</details>`;

/** The PR comment. Grows route by route; when the budget runs out later routes lose their fold, then their row. */
function renderComment(diffs, counts, total, meta, maxChars) {
    const changed = diffs.filter((d) => d.skipped || d.status !== 'unchanged');
    const head = ['### Client Eager-Chunk Loading Report', summaryLine(counts, total), '', legend(meta), '', HOW_TO_READ, '', ''].join('\n');
    const tail = `\n\n${HOW_TO_FIX}`;
    if (changed.length === 0) return head.trimEnd();

    const reserve = 600; // the workflow appends a link line and a marker
    const units = changed.map((d) => ({ plain: routeRow(d), full: [routeRow(d), foldRow(d, COMMENT_MODULES_PER_IMPORT, false)].filter(Boolean).join('\n') }));
    const build = (shownUnits, omitted) => {
        const note = omitted > 0 ? `\n\n_${plural(omitted, 'more route', 'more routes')} not shown here, see the full report._` : '';
        return `${head}${table(shownUnits)}${note}${tail}`;
    };

    let shown = units.map((u) => u.full);
    let text = build(shown, 0);
    for (let i = shown.length - 1; i >= 0 && text.length + reserve > maxChars; i--) {
        shown[i] = units[i].plain;
        text = build(shown, 0);
    }
    while (shown.length > 1 && text.length + reserve > maxChars) {
        shown = shown.slice(0, -1);
        text = build(shown, units.length - shown.length);
    }
    return text;
}

// Style of the standalone full report: GitHub-like tables and code, light and dark. The comment cannot carry any
// style (GitHub strips it), so this is the one place the report may look the way it was designed.
const FULL_REPORT_STYLE = `
:root { --bg: #fff; --fg: #1f2328; --muted: #59636e; --line: #d1d9e0; --soft: #f6f8fa; --code: #eff1f3; color-scheme: light; }
@media (prefers-color-scheme: dark) { :root { --bg: #0d1117; --fg: #e6edf3; --muted: #9198a1; --line: #3d444d; --soft: #151b23; --code: #262c36; color-scheme: dark; } }
body { margin: 0; background: var(--bg); color: var(--fg); font: 14px/1.5 -apple-system, BlinkMacSystemFont, "Segoe UI", "Noto Sans", Helvetica, Arial, sans-serif; }
main { max-width: 1000px; margin: 0 auto; padding: 24px 16px 64px; }
h1 { font-size: 24px; margin: 0 0 12px; } h2 { font-size: 18px; margin: 32px 0 10px; padding-bottom: 6px; border-bottom: 1px solid var(--line); } h3 { font-size: 15px; margin: 24px 0 8px; }
blockquote { margin: 0 0 16px; padding: 0 1em; color: var(--muted); border-left: 4px solid var(--line); }
table { border-collapse: collapse; margin: 0 0 12px; } th, td { border: 1px solid var(--line); padding: 6px 13px; text-align: left; vertical-align: top; }
tr:nth-child(2n) > td { background: var(--soft); } td > details > summary { cursor: pointer; } td[colspan] { padding: 8px 13px; }
code { font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; font-size: 85%; background: var(--code); padding: 0.2em 0.4em; border-radius: 6px; }
pre { background: var(--soft); border-radius: 6px; padding: 12px 16px; overflow: auto; font: 12.5px/1.45 ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; }
details { margin: 0 0 8px; } summary { cursor: pointer; } ul { padding-left: 2em; } li { margin: 3px 0; } hr { border: 0; border-top: 1px solid var(--line); margin: 20px 0; }
.small { color: var(--muted); font-size: 12.5px; }
`;

const legendHtml = (meta) =>
    `<blockquote>A route's <b>eager chunks</b> are the JavaScript files its page downloads as soon as it opens, before the user does anything. This compares them with ${code(meta.sourceBranch)} @ ${code(String(meta.sourceCommit).slice(0, 10))}, the commit this branch was cut from.</blockquote>`;

/** The full report as one standalone HTML page: nothing shortened, and the style it was designed with. */
function renderFullHtml(diffs, unchangedDiffs, counts, total, meta) {
    const changed = diffs.filter((d) => d.skipped || d.status !== 'unchanged');
    const detailed = changed.filter((d) => !d.skipped);
    const out = [
        '<!doctype html>',
        '<html lang="en">',
        '<head>',
        '<meta charset="utf-8">',
        '<meta name="viewport" content="width=device-width, initial-scale=1">',
        '<title>Client Eager-Chunk Loading Report</title>',
        `<style>${FULL_REPORT_STYLE}</style>`,
        '</head>',
        '<body>',
        '<main>',
        '<h1>Client Eager-Chunk Loading Report</h1>',
        `<p>${esc(summaryLine(counts, total))}</p>`,
        legendHtml(meta),
        '<h2>Overview</h2>',
        changed.length > 0 ? table(changed.map(routeRow)) : '<p>No route changed.</p>',
    ];
    if (detailed.length > 0) {
        out.push(
            '<h2>Per route</h2>',
            '<p class="small">Open the imports line to see each import with its size and, under it, every module it brings along, largest first. A module is an npm package or one of your own files. Below each route table, the chunk list shows how the code is bundled, with file names.</p>',
        );
    }
    detailed.forEach((d, index) => {
        out.push(`<h3>${esc(d.route)}</h3>`, table([routeRow(d), foldRow(d, Infinity, index === 0)].filter(Boolean)));
        if (d.changedChunks.length > 0) {
            const verb = d.status === 'improved' ? 'no longer loaded' : 'new';
            out.push(`<details><summary>All ${d.changedChunks.length} ${verb} chunks of <b>${esc(d.route)}</b>, largest first, with file names</summary>`, '<ul>');
            for (const c of d.changedChunks) out.push(`<li><b>${fmtSize(c.bytes)}</b> ${code(c.file)}: ${c.modules.map((m) => code(m[0])).join(', ')}</li>`);
            out.push('</ul>', '</details>');
        }
        out.push('<hr>');
    });
    if (unchangedDiffs.length > 0) {
        out.push(`<h2>Unchanged routes (${unchangedDiffs.length})</h2>`, `<details><summary>Show all ${unchangedDiffs.length} routes</summary>`, '<table>');
        out.push(
            '<thead><tr><th align="left">Route</th><th align="right">Eager chunks</th><th align="right">Eager size</th><th align="right">Δ chunks</th></tr></thead>',
            '<tbody>',
        );
        for (const d of unchangedDiffs)
            out.push(
                `<tr><td>${esc(d.route)}</td><td align="right">${d.fresh.chunks}</td><td align="right">${fmtSize(d.fresh.bytes)}</td><td align="right">${fmtCountSigned(d.chunkCountDelta)}</td></tr>`,
            );
        out.push('</tbody>', '</table>', '</details>');
    }
    out.push('<p class="small">Generated by supporting_scripts/chunk-eager-loading.</p>', '</main>', '</body>', '</html>');
    return out.join('\n');
}

function main() {
    const { reportPath, baselinePath, outPath, fullOutPath, maxChars } = parseArgs(process.argv);
    if (!reportPath) {
        console.error(`Usage: ${USAGE}`);
        process.exit(1);
    }

    const fresh = loadReport(reportPath);
    const baseline = loadReport(baselinePath);

    const freshByRoute = Object.fromEntries(fresh.routes.map((r) => [r.route, r]));
    const baselineByRoute = Object.fromEntries(baseline.routes.map((r) => [r.route, r]));
    const allRoutes = [...new Set([...Object.keys(freshByRoute), ...Object.keys(baselineByRoute)])];
    const diffs = allRoutes.map((route) => diffRoute(route, baseline, fresh, baselineByRoute[route], freshByRoute[route]));

    const counts = { regressed: 0, improved: 0, unchanged: 0, skipped: 0 };
    for (const d of diffs) counts[d.skipped ? 'skipped' : d.status]++;
    const sorted = sortDiffs(diffs);
    const unchangedDiffs = sorted.filter((d) => !d.skipped && d.status === 'unchanged').sort((a, b) => a.route.localeCompare(b.route));

    const comment = renderComment(sorted, counts, diffs.length, baseline.meta, maxChars);

    if (outPath) {
        writeFileSync(outPath, comment);
        console.log(`Comment written to ${outPath} (${comment.length} characters)`);
    }
    if (fullOutPath) {
        const full = renderFullHtml(sorted, unchangedDiffs, counts, diffs.length, baseline.meta);
        writeFileSync(fullOutPath, full);
        console.log(`Full report written to ${fullOutPath} (${full.length} characters)`);
    }
    console.log(summaryLine(counts, diffs.length));
    for (const d of sorted.filter((diff) => !diff.skipped && diff.status !== 'unchanged')) {
        console.log(`${MARKER[d.status]} ${d.route}: ${fmtCountSigned(d.chunkCountDelta)} chunks, ${fmtSizeSigned(d.bytesDelta)} (${fmtPct(d.bytesDeltaPct)})`);
    }

    // Exit code reflects regressions only (never improvements) -- kept mainly so this is usable
    // as a merge gate later if desired; ci-build.yml's chunk-eager-quality job treats this as
    // advisory reporting (continue-on-error: true), not a gate.
    process.exitCode = counts.regressed > 0 ? 1 : 0;
}

main();
