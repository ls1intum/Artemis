#!/usr/bin/env python3
"""
format_slow_query_report.py
---------------------------
Reads the JSON produced by GET /api/core/admin/performance/slow-queries and
prints a GitHub-flavored Markdown summary suitable for appending to a PR comment.
Optionally also writes a self-contained, sortable HTML report (the full,
untruncated findings) to a separate file for upload as a CI artifact.

Usage:
    python3 format_slow_query_report.py slow-query-report.json [run_url] [html_output_path]

The Markdown summary is written to stdout; redirect to a .md file from the shell.
"""

import html
import json
import re
import sys
from datetime import timezone, datetime

# GitHub caps issue/PR comment bodies at 65536 characters. A "run all tests" pass can collect
# thousands of findings, so only the worst offenders are rendered inline; the rest are still
# available, untruncated, in the uploaded HTML artifact (see build_html_report).
MAX_ROWS_PER_SECTION = 20

# --- Endpoint Findings inclusion rule ---
# Absolute floors: a pattern must clear at least one to be shown at all. Deliberately absolute,
# not relative -- a purely percentile-based rule ("top 10%") would always surface *something*,
# even from a run where every endpoint is genuinely healthy. Ratio is safe to fix as an absolute
# number (already normalised, stable across environment speed); the ms floor is deliberately low
# and generous -- not "this is bad", just "this isn't literally noise"; the repeat-count floor
# reuses the existing N+1 threshold rather than inventing a new number.
ENDPOINT_FINDINGS_RATIO_FLOOR = 0.3
ENDPOINT_FINDINGS_DBTIME_FLOOR_MS = 20
ENDPOINT_FINDINGS_REPEAT_FLOOR = 5
# Among patterns clearing a floor, show the union of the top N% on each axis -- three separate
# axes because they catch different failure shapes (verified against real CI data: only ~10%
# overlap between the ratio-ranked and time-ranked sets).
ENDPOINT_FINDINGS_TOP_PERCENT = 10
# A pattern reproduced across many tests doesn't need one row per test to make its point.
ENDPOINT_FINDINGS_MAX_INSTANCES_SHOWN = 15


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def load_report(path: str) -> dict:
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def trunc(text: str, max_len: int = 120) -> str:
    """Truncate a SQL string for display in a Markdown table cell."""
    if text is None:
        return ""
    # Backticks can't be backslash-escaped inside a single-backtick code span (Markdown treats
    # code span content as verbatim), so a literal backtick would prematurely close the span and
    # corrupt the table. Substitute a visually similar character instead.
    text = text.replace("|", "\\|").replace("\n", " ").replace("`", "'")
    return text[:max_len] + "…" if len(text) > max_len else text


def trunc_plain(text: str, max_len: int = 100) -> str:
    """Truncate a SQL string for display in an HTML table cell (no Markdown escaping)."""
    if not text:
        return ""
    text = text.replace("\n", " ")
    return text[:max_len] + "…" if len(text) > max_len else text


# ---------------------------------------------------------------------------
# Static-finding correlation
# ---------------------------------------------------------------------------
# Cross-references find_slow_queries.py's static source-scan findings (wide @EntityGraph/JOIN
# FETCH definitions, wide eager-fetch association graphs) against the dynamic Slow Query / N+1
# tables, by table name. Heuristic, not proven: the table-name guess is Hibernate's default
# PascalCase -> snake_case naming convention, which this codebase follows in the common case but
# doesn't guarantee for every @Table(name = ...) override.

ROOT_TABLE_PATTERN = re.compile(r'\bfrom\s+(\w+)', re.IGNORECASE)


def guess_table_name(entity_class: str) -> str:
    """PascalCase entity class name -> snake_case table name guess (Hibernate's default physical
    naming strategy). E.g. "ExerciseGroup" -> "exercise_group"."""
    return re.sub(r'(?<!^)(?=[A-Z])', '_', entity_class).lower()


def extract_root_table(sql: str) -> str:
    """The table right after the query's own FROM clause -- deliberately not every table the
    query happens to JOIN, so a query merely joining `course` incidentally isn't badged as an
    eager-fetch risk just because `course` is a common join target elsewhere."""
    if not sql:
        return None
    match = ROOT_TABLE_PATTERN.search(sql)
    return match.group(1).lower() if match else None


def build_static_index(static_findings: list) -> dict:
    """table name guess -> list of (original index in static_findings, finding) pairs. The index
    is kept (not just the finding dict) so a badge can point at the *exact* row to highlight in
    build_static_findings_table_html, rather than a name a reader would have to search for --
    a plain substring match on "Course" hits ~60 unrelated rows in this codebase (file paths under
    the `course` package), so name-based matching is genuinely unreliable, not just less precise."""
    index = {}
    for i, f in enumerate(static_findings):
        entity_class = f.get("entityClass")
        if not entity_class:
            continue
        index.setdefault(guess_table_name(entity_class), []).append((i, f))
    return index


def static_finding_label(f: dict) -> str:
    if f["type"] == "wide_eager_fetch":
        return f"wide eager-fetch graph ({f['reachableEntityCount']} entities reachable)"
    if f["type"] == "wide_join_fetch":
        return f"wide JOIN FETCH ({f['fetchCount']} joins in one @Query)"
    if f["type"] == "wide_entitygraph":
        return f"wide @EntityGraph ({f['fetchCount']} attribute paths)"
    return f["type"]


def static_badge_html(sql: str, static_index: dict) -> str:
    table = extract_root_table(sql)
    matches = static_index.get(table, []) if table else []
    if not matches:
        return ""
    labels = "; ".join(static_finding_label(f) for _, f in matches)
    # data-finding-indices drives the click-to-navigate behaviour (see HTML_REPORT_JS): jumps to
    # the Static Findings tab, clears any active filter, and scrolls to + highlights these exact
    # row(s) by id -- precise, unlike matching on a name that could collide with unrelated rows.
    indices = ",".join(str(i) for i, _ in matches)
    return f'<button type="button" class="static-badge" title="{html.escape(labels)}" data-finding-indices="{indices}">🔍 static match</button>'


def fmt_endpoint(method: str, endpoint: str, thread_name: str = None) -> str:
    if not method and not endpoint:
        return f"*(background: `{thread_name}`)*" if thread_name else "*(background)*"
    return f"`{method or '?'} {endpoint or '?'}`"


def fmt_phase(phase: str, has_endpoint: bool = True) -> str:
    """Markdown for the setup-vs-action badge. A query with no `httpEndpoint` at all never ran
    inside a Playwright-driven request -- there's no setup/action phase to report, so it gets its
    own label rather than reusing the generic `?`, which otherwise looks identical to the (real,
    separately-diagnosed) header-corruption bug that also renders as `?`. `?` itself is now only
    reached when there IS an HTTP context but the phase value is neither `action` nor `setup`,
    e.g. a not-yet-fully-diagnosed edge case -- genuinely worth a reader's attention."""
    if not has_endpoint:
        return "⚙️ background"
    return {"action": "🎯 action", "setup": "🔧 setup"}.get(phase, "?")


def phase_sort_key(finding: dict, metric: str):
    """Ranks action-phase findings ahead of setup-phase ones (worst-metric-first within
    each group) so the top-N cap in the Markdown comment surfaces genuine test-action
    findings before the test-setup noise they'd otherwise be drowned out by."""
    return (finding.get("phase") == "action", finding.get(metric, 0))


def iso_to_human(iso: str) -> str:
    try:
        dt = datetime.fromisoformat(iso.replace("Z", "+00:00"))
        return dt.astimezone(timezone.utc).strftime("%Y-%m-%d %H:%M:%S UTC")
    except Exception:
        return iso


# ---------------------------------------------------------------------------
# Markdown sections
# ---------------------------------------------------------------------------

def artifact_link(run_url: str) -> str:
    """Markdown for 'the uploaded slow-query report artifact (HTML)', linked to the run's
    Artifacts section when a run URL is available, plain text otherwise (e.g. local runs)."""
    if run_url:
        return f"[uploaded slow-query report artifact (HTML)]({run_url})"
    return "uploaded slow-query report artifact (HTML)"


def compute_slow_query_groups(slow_queries: list) -> list:
    """Groups raw slow-query captures by (endpoint, SQL template, background thread) -- the same
    query at the same endpoint crossing the threshold dozens of times across a handful of tests
    (a real, observed case: 179 raw captures, 1 distinct query, 3 tests) is one finding, not 179.
    Returns (key, group) tuples sorted worst-first by worst execution time; each group carries
    every individual occurrence (test, phase, duration) for the collapsible detail view."""
    groups = {}
    for q in slow_queries:
        key = (q.get("httpMethod"), q.get("httpEndpoint"), q.get("sql", ""), q.get("threadName"))
        g = groups.setdefault(key, {"count": 0, "worst_ms": 0, "join_count": q.get("joinCount", 0), "occurrences": []})
        g["count"] += 1
        g["worst_ms"] = max(g["worst_ms"], q.get("executionTimeMs", 0))
        g["occurrences"].append({"test": q.get("testName"), "phase": q.get("phase"), "ms": q.get("executionTimeMs", 0), "has_endpoint": bool(q.get("httpEndpoint"))})
    items = list(groups.items())
    items.sort(key=lambda kv: kv[1]["worst_ms"], reverse=True)
    return items


def build_slow_queries_section(slow_queries: list, threshold_ms: int, run_url: str = "") -> str:
    if not slow_queries:
        return f"✅ **No slow queries** detected (threshold: {threshold_ms} ms)\n"

    groups = compute_slow_query_groups(slow_queries)
    # Action-phase groups first (worst-first within each group): the Markdown comment can only
    # show MAX_ROWS_PER_SECTION rows, and test-setup traffic (page.request/context.request calls,
    # phase="setup") runs far more often than the action a test is actually verifying, so a plain
    # worst-first sort lets setup noise crowd out genuine action-phase findings. The full,
    # phase-unfiltered ranking is still in the untruncated HTML artifact.
    ranked = sorted(groups, key=lambda kv: (any(o["phase"] == "action" for o in kv[1]["occurrences"]), kv[1]["worst_ms"]), reverse=True)
    shown = ranked[:MAX_ROWS_PER_SECTION]
    hidden_count = len(ranked) - len(shown)

    lines = [
        f"### 🐢 Slow Queries ({len(groups)} distinct, {len(slow_queries)} occurrences, threshold: {threshold_ms} ms)\n",
        "| # | Worst Duration | Occurrences | Joins | Endpoint | SQL (truncated) |",
        "|---|-----------------|-------------|-------|----------|------------------|",
    ]
    for i, ((method, endpoint, sql, thread_name), g) in enumerate(shown, start=1):
        endpoint_fmt = fmt_endpoint(method, endpoint, thread_name)
        lines.append(f"| {i} | **{g['worst_ms']} ms** | {g['count']}× | {g['join_count']} | {endpoint_fmt} | `{trunc(sql)}` |")
    if hidden_count > 0:
        lines.append("")
        lines.append(f"_Showing the {len(shown)} worst (action-phase groups prioritized). {hidden_count} more not shown — see the {artifact_link(run_url)} for the full list._")
    return "\n".join(lines) + "\n"


# ---------------------------------------------------------------------------
# Endpoint Findings -- replaces the old separate N+1 Suspects / Endpoint Timing sections.
# ---------------------------------------------------------------------------
# Both were really the same underlying data (per-request query behaviour) answered at two
# different grain sizes; keeping them apart just meant a reader had to cross-reference two tables
# to see the full picture of one endpoint. Grouped by (endpoint, query-template-set) rather than
# raw request, so the same bug tested by many different tests collapses into one row instead of
# flooding the table with near-duplicates -- see ENDPOINT_FINDINGS_* constants above for the exact
# inclusion rule and why each threshold is what it is.

def compute_endpoint_findings(endpoint_timings: list) -> dict:
    """Groups per-request Endpoint Timing entries into distinct (endpoint, query-template-set)
    patterns, then applies the inclusion rule: Rule 1 (something repeats) -> floor gate (ratio,
    absolute DB time, or the classic N+1 repeat count) -> percentile ranking (top N% per axis,
    unioned). Returns the final sorted rows plus the funnel counts at each stage, so the report
    can show its own filtering honestly rather than silently dropping rows."""
    groups = {}
    for e in endpoint_timings:
        method = e.get("httpMethod") or "?"
        endpoint = e.get("httpEndpoint") or "?"
        queries = e.get("queries", [])
        templates = tuple(sorted(q.get("sql", "") for q in queries))
        key = (method, endpoint, templates)
        g = groups.setdefault(key, {"instances": 0, "worst_ratio": 0.0, "worst_dbtime": 0, "worst_qcount": 0, "max_repeat": 0, "queries": {}, "occurrences": []})

        total = e.get("totalDurationMs", 0)
        db = e.get("dbTimeMs", 0)
        ratio = (db / total) if total else 0
        qcount = e.get("queryCount", 0)
        g["instances"] += 1
        g["worst_ratio"] = max(g["worst_ratio"], ratio)
        g["worst_dbtime"] = max(g["worst_dbtime"], db)
        g["worst_qcount"] = max(g["worst_qcount"], qcount)
        g["occurrences"].append({"test": e.get("testName"), "phase": e.get("phase"), "ratio": ratio, "total_ms": total, "db_ms": db, "qcount": qcount})
        for q in queries:
            sql = q.get("sql", "")
            count = q.get("count", 0)
            qms = q.get("totalDurationMs", 0)
            g["max_repeat"] = max(g["max_repeat"], count)
            prev = g["queries"].get(sql, (0, 0))
            g["queries"][sql] = (max(prev[0], count), max(prev[1], qms))

    repeating = {k: v for k, v in groups.items() if v["max_repeat"] > 1}
    cleared = {
        k: v
        for k, v in repeating.items()
        if v["worst_ratio"] >= ENDPOINT_FINDINGS_RATIO_FLOOR or v["worst_dbtime"] >= ENDPOINT_FINDINGS_DBTIME_FLOOR_MS or v["max_repeat"] > ENDPOINT_FINDINGS_REPEAT_FLOOR
    }

    items = list(cleared.items())
    n = max(1, int(len(items) * ENDPOINT_FINDINGS_TOP_PERCENT / 100)) if items else 0
    top_ratio = {k for k, v in sorted(items, key=lambda kv: kv[1]["worst_ratio"], reverse=True)[:n]}
    top_dbtime = {k for k, v in sorted(items, key=lambda kv: kv[1]["worst_dbtime"], reverse=True)[:n]}
    top_repeat = {k for k, v in sorted(items, key=lambda kv: kv[1]["max_repeat"], reverse=True)[:n]}
    final_keys = top_ratio | top_dbtime | top_repeat

    final_rows = [(k, v) for k, v in items if k in final_keys]
    final_rows.sort(key=lambda kv: kv[1]["worst_ratio"], reverse=True)

    return {"rows": final_rows, "total_requests": len(endpoint_timings), "distinct_patterns": len(groups), "repeating_patterns": len(repeating), "floor_cleared": len(cleared)}


def worst_query_summary(queries: dict, template_count: int) -> str:
    if not queries:
        return f"{template_count} templates"
    worst_sql, (worst_count, _) = max(queries.items(), key=lambda kv: kv[1][0])
    return f"{trunc(worst_sql, 50)} ×{worst_count}" if worst_count > 1 else f"{template_count} templates"


def build_endpoint_findings_section(findings: dict, run_url: str = "") -> str:
    rows = findings["rows"]
    if not rows:
        return "✅ **No endpoint findings** above the inclusion floor (DB-time ratio, absolute DB time, or repeat count)\n"

    shown = rows[:MAX_ROWS_PER_SECTION]
    hidden_count = len(rows) - len(shown)

    lines = [
        f"### 🔁 Endpoint Findings ({len(rows)} shown, from {findings['distinct_patterns']} distinct patterns across {findings['total_requests']} requests)\n",
        "| # | Endpoint | What Repeats | Seen In | Worst DB Time | Worst Ratio | Max Repeat |",
        "|---|----------|--------------|---------|---------------|-------------|------------|",
    ]
    for i, ((method, endpoint, templates), v) in enumerate(shown, start=1):
        what = worst_query_summary(v["queries"], len(templates))
        lines.append(f"| {i} | `{method} {endpoint}` | `{what}` | {v['instances']} | {v['worst_dbtime']} ms | {v['worst_ratio'] * 100:.0f}% | {v['max_repeat']}× |")
    if hidden_count > 0:
        lines.append("")
        lines.append(f"_Showing the {len(shown)} worst by ratio. {hidden_count} more not shown — see the {artifact_link(run_url)} for the full list._")
    return "\n".join(lines) + "\n"


def build_report(report: dict, run_url: str = "") -> str:
    threshold_ms = report.get("thresholdMs", "?")
    generated_at = iso_to_human(report.get("generatedAt", ""))

    slow_queries = report.get("slowQueries", [])
    slow_groups = compute_slow_query_groups(slow_queries)
    slow_count = len(slow_groups)
    findings = compute_endpoint_findings(report.get("endpointTimings", []))
    findings_count = len(findings["rows"])

    # --- Header --- (counts are distinct groups, not raw occurrences -- the same query crossing
    # the threshold 179 times across 3 tests is one issue to look at, not 179)
    if slow_count == 0 and findings_count == 0:
        header_emoji = "✅"
        header_status = "No performance regressions detected"
    else:
        header_emoji = "⚠️"
        issues = []
        if slow_count > 0:
            issues.append(f"{slow_count} slow quer{'y' if slow_count == 1 else 'ies'} ({len(slow_queries)} occurrences)")
        if findings_count > 0:
            issues.append(f"{findings_count} endpoint finding{'s' if findings_count != 1 else ''}")
        header_status = "Performance issues found: " + " · ".join(issues)

    lines = [
        "",
        "---",
        f"## {header_emoji} Slow Query Report",
        "",
        f"**{header_status}**",
        "",
        f"> Generated at: {generated_at}  ",
        f"> Slow-query threshold: **{threshold_ms} ms**  ",
        f"> Endpoint findings from {findings['total_requests']} requests → {findings['distinct_patterns']} distinct patterns → "
        f"{findings['repeating_patterns']} with a repeated query → {findings['floor_cleared']} clearing a floor → **{findings_count} shown**",
        "",
        build_slow_queries_section(slow_queries, threshold_ms, run_url),
        build_endpoint_findings_section(findings, run_url),
        "",
        "<details>",
        "<summary>How to investigate</summary>",
        "",
        "**Slow queries** exceed the per-query execution time threshold.",
        "Common causes: missing index, unbounded result set, complex JOIN.",
        "",
        "**Endpoint findings** are requests where the same query template repeated within one",
        "request, grouped by endpoint and query shape (so the same bug tested many times shows up",
        "once, not once per test), and shown only if it clears at least one bar: a high share of",
        "the request's time spent in the database, a high absolute DB time, or a single query",
        "repeating more than 5 times — a strong indicator that related data is being loaded one",
        "entity at a time instead of in a single JOIN, or that an endpoint's overall DB engagement",
        "is high even though no single query is individually slow.",
        "",
        "See the [performance guidelines](https://docs.artemis.tum.de/developer/guidelines/performance)",
        "for remediation patterns.",
        "",
        "</details>",
        "",
        "<!-- Slow Query Report -->",
    ]
    return "\n".join(lines)


# ---------------------------------------------------------------------------
# HTML report (full, untruncated findings; uploaded as the CI artifact)
# ---------------------------------------------------------------------------
# Self-contained on purpose: no external CSS/JS files. A multi-file report (like Istanbul's
# lcov-report) breaks silently if only index.html survives being unzipped/copied around,
# because the sortable-table styling and click-to-sort script live in separate files that
# are easy to leave behind. Inlining both here means the single .html file downloaded from
# the CI artifact always renders and sorts correctly, with nothing else to fetch.

HTML_REPORT_CSS = """
:root {
    color-scheme: light dark;
    --bg: #ffffff;
    --fg: #1a1a1a;
    --muted: #6b7280;
    --border: #e5e7eb;
    --row-hover: #f3f4f6;
    --sev-high: #dc2626;
    --sev-medium: #d97706;
    --sev-low: #ca8a04;
    --tile-bg: #f9fafb;
    --sql-keyword: #1d4ed8;
    --sql-string: #15803d;
    --sql-number: #7c3aed;
    --sql-placeholder: #b45309;
}
@media (prefers-color-scheme: dark) {
    :root {
        --bg: #14161a;
        --fg: #e5e7eb;
        --muted: #9ca3af;
        --border: #2d323b;
        --row-hover: #1f232a;
        --tile-bg: #1c1f26;
        --sql-keyword: #7aa2f7;
        --sql-string: #7ee787;
        --sql-number: #d2a8ff;
        --sql-placeholder: #f2cc60;
    }
}
* { box-sizing: border-box; }
body { margin: 0; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; background: var(--bg); color: var(--fg); }
.wrapper { max-width: 100%; margin: 0; padding: 24px clamp(16px, 3vw, 48px) 64px; }
h1 { margin-bottom: 4px; }
h2 { margin-top: 0; }
.muted { color: var(--muted); }
.ok { color: #16a34a; font-weight: 600; }
.lead { max-width: 760px; color: var(--fg); line-height: 1.5; margin: 8px 0 28px; }
.stats { display: flex; gap: 16px; margin: 0 0 32px; flex-wrap: wrap; }
.stat-tile { background: var(--tile-bg); border: 1px solid var(--border); border-radius: 8px; padding: 12px 20px; min-width: 160px; }
.stat-value { display: block; font-size: 28px; font-weight: 700; }
.stat-label { display: block; font-size: 13px; color: var(--muted); }
.stat-sub { display: block; font-size: 12px; color: var(--muted); }
.tabs { display: flex; gap: 4px; border-bottom: 1px solid var(--border); margin-bottom: 24px; }
.tab-btn { appearance: none; background: none; border: none; border-bottom: 2px solid transparent; margin-bottom: -1px; padding: 10px 6px; font: inherit; font-size: 14px; font-weight: 600; color: var(--muted); cursor: pointer; display: flex; align-items: center; gap: 8px; }
.tab-btn:hover { color: var(--fg); }
.tab-btn.active { color: var(--fg); border-bottom-color: var(--sev-medium); }
.tab-count { background: var(--tile-bg); border: 1px solid var(--border); border-radius: 999px; padding: 1px 8px; font-size: 12px; font-weight: 600; }
.tab-panel[hidden] { display: none; }
.filter-box { width: 100%; max-width: 400px; padding: 8px 10px; margin: 8px 0 12px; border: 1px solid var(--border); border-radius: 6px; background: var(--bg); color: var(--fg); font-size: 14px; }
table { width: 100%; border-collapse: collapse; margin-bottom: 32px; font-size: 13px; }
th, td { text-align: left; padding: 8px 10px; border-bottom: 1px solid var(--border); vertical-align: top; }
th { cursor: pointer; user-select: none; white-space: nowrap; color: var(--muted); font-weight: 600; }
th:hover { color: var(--fg); }
th::after { content: "\\21C5"; opacity: .35; margin-left: 4px; font-size: 11px; }
tr:hover td { background: var(--row-hover); }
tr.sev-high td:first-child { border-left: 4px solid var(--sev-high); }
tr.sev-medium td:first-child { border-left: 4px solid var(--sev-medium); }
tr.sev-low td:first-child { border-left: 4px solid var(--sev-low); }
td.num { font-variant-numeric: tabular-nums; white-space: nowrap; }
td.sql-cell code, td.sql-cell pre { font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; }
td.sql-cell pre { white-space: pre-wrap; word-break: break-word; margin: 6px 0 0; padding: 8px; background: var(--tile-bg); border-radius: 6px; }
td.sql-cell summary { cursor: pointer; }
.phase-badge { display: inline-block; padding: 2px 8px; border-radius: 999px; font-size: 12px; font-weight: 600; white-space: nowrap; }
.phase-action { background: rgba(22, 163, 74, .15); color: #16a34a; }
.phase-setup { background: rgba(107, 114, 128, .15); color: var(--muted); }
.phase-background { background: rgba(107, 114, 128, .08); color: var(--muted); font-style: italic; }
table.nested-table { width: auto; min-width: 320px; margin: 6px 0 0; font-size: 12px; }
table.nested-table th, table.nested-table td { padding: 4px 8px; }
table.nested-table th { cursor: default; }
.query-breakdown summary { cursor: pointer; color: var(--muted); }
.static-badge { display: inline-block; margin-left: 6px; padding: 0; border: none; background: none; font: inherit; font-size: 11px; color: var(--sql-keyword); cursor: pointer; white-space: nowrap; text-decoration: underline; text-underline-offset: 2px; }
.static-badge:hover, .static-badge:focus-visible { color: var(--fg); }
/* transition lives on the base selector, not the .highlight-flash one -- removing the class only
   fades smoothly back to normal if the transitioning element still matches a rule that declares
   the transition property; scoping it to the class being removed would make it snap instantly. */
#static-findings-table tbody tr td { transition: background 2s ease-out; }
#static-findings-table tbody tr.highlight-flash td { background: color-mix(in srgb, var(--sev-medium) 25%, var(--bg)); }
@media (prefers-reduced-motion: reduce) { #static-findings-table tbody tr td { transition: none; } }
.sql-keyword { color: var(--sql-keyword); font-weight: 600; }
.sql-string { color: var(--sql-string); }
.sql-number { color: var(--sql-number); }
.sql-placeholder { color: var(--sql-placeholder); font-weight: 600; }
.group-row { cursor: pointer; }
.expand-cell { width: 20px; text-align: center; color: var(--muted); }
.detail-row td { background: var(--tile-bg); padding: 10px 10px 14px 30px; }
.detail-row table.nested-table { width: 100%; }
"""

HTML_REPORT_JS = """
document.querySelectorAll('table.sortable').forEach(function (table) {
    var headers = Array.prototype.slice.call(table.querySelectorAll('th'));
    headers.forEach(function (th, colIndex) {
        th.addEventListener('click', function () {
            var tbody = table.tBodies[0];
            var rows = Array.prototype.slice.call(tbody.rows);
            var type = th.dataset.type || 'string';
            var asc = !(table.dataset.sortCol === String(colIndex) && table.dataset.sortDir === 'asc');
            rows.sort(function (a, b) {
                var x = a.cells[colIndex].dataset.sort !== undefined ? a.cells[colIndex].dataset.sort : a.cells[colIndex].textContent.trim();
                var y = b.cells[colIndex].dataset.sort !== undefined ? b.cells[colIndex].dataset.sort : b.cells[colIndex].textContent.trim();
                if (type === 'number') { x = parseFloat(x) || 0; y = parseFloat(y) || 0; }
                else { x = x.toLowerCase(); y = y.toLowerCase(); }
                if (x < y) return asc ? -1 : 1;
                if (x > y) return asc ? 1 : -1;
                return 0;
            });
            rows.forEach(function (row) { tbody.appendChild(row); });
            table.dataset.sortCol = String(colIndex);
            table.dataset.sortDir = asc ? 'asc' : 'desc';
        });
    });
});

document.querySelectorAll('.filter-box').forEach(function (input) {
    input.addEventListener('input', function () {
        var table = document.getElementById(input.dataset.target);
        if (!table) return;
        var query = input.value.toLowerCase();
        Array.prototype.forEach.call(table.tBodies[0].rows, function (row) {
            row.style.display = row.textContent.toLowerCase().indexOf(query) !== -1 ? '' : 'none';
        });
    });
});

function activateTab(controlsId) {
    document.querySelectorAll('.tab-btn').forEach(function (b) {
        var active = b.getAttribute('aria-controls') === controlsId;
        b.classList.toggle('active', active);
        b.setAttribute('aria-selected', active ? 'true' : 'false');
    });
    document.querySelectorAll('.tab-panel').forEach(function (p) { p.hidden = p.id !== controlsId; });
}

document.querySelectorAll('.tab-btn').forEach(function (btn) {
    btn.addEventListener('click', function () { activateTab(btn.getAttribute('aria-controls')); });
});

// Clicking a 🔍 static-match badge jumps to the Static Findings tab and scrolls to + highlights
// the exact matching row(s) by id -- NOT a text filter: a plain substring match on an entity name
// like "Course" hits ~60 unrelated rows in this codebase (file paths under the `course` package
// alone), so name-based matching would be actively misleading, not just less precise. Delegated
// on document since badges live inside dynamically shown/hidden detail rows -- a per-element
// listener would miss ones added after the fact, and delegation works uniformly regardless.
document.addEventListener('click', function (e) {
    var badge = e.target.closest('.static-badge');
    if (!badge) return;
    e.stopPropagation();
    var indices = (badge.dataset.findingIndices || '').split(',').filter(Boolean);
    if (!indices.length) return;
    activateTab('panel-static-findings');
    // Clear any active filter first -- a stale filter term from earlier could otherwise hide the
    // exact row(s) this click is trying to reveal.
    var filterBox = document.querySelector('input.filter-box[data-target="static-findings-table"]');
    if (filterBox && filterBox.value) {
        filterBox.value = '';
        filterBox.dispatchEvent(new Event('input'));
    }
    var firstRow = null;
    indices.forEach(function (i) {
        var row = document.getElementById('static-finding-' + i);
        if (!row) return;
        if (!firstRow) firstRow = row;
        row.classList.add('highlight-flash');
        setTimeout(function () { row.classList.remove('highlight-flash'); }, 2500);
    });
    if (firstRow) firstRow.scrollIntoView({ behavior: 'smooth', block: 'center' });
});

// Grouped tables (Slow Queries, Endpoint Findings): rows are expand/collapse-able, and sorted
// with this dedicated function rather than the generic sort above. The generic sort reorders
// every <tr> in the tbody by index, which would scramble group/detail-row pairing (a detail row
// has one colspan cell, not one per column, so `cells[colIndex]` would misalign). This moves each
// group-row and re-attaches its detail-row by id right after it, so pairing survives sorting.
// Nested tables *inside* a detail row (the instance list, the query breakdown) are deliberately
// plain `table.sortable` elements instead -- no group/detail pairing to protect, so the generic
// sort script above already handles them correctly with no extra code.
function setupGroupedTable(tableId, colIndex) {
    var table = document.getElementById(tableId);
    if (!table) return;
    var tbody = table.tBodies[0];
    table.querySelectorAll('.group-row').forEach(function (row) {
        row.addEventListener('click', function (e) {
            if (e.target.closest('details') || e.target.closest('table.sortable') || e.target.closest('.static-badge')) return;
            var detail = document.getElementById(row.dataset.detailTarget);
            var arrow = row.querySelector('.expand-arrow');
            detail.hidden = !detail.hidden;
            arrow.innerHTML = detail.hidden ? '&#9654;' : '&#9660;';
        });
    });
    table.querySelectorAll('th[data-col]').forEach(function (th) {
        th.style.cursor = 'pointer';
        th.addEventListener('click', function () {
            var col = th.dataset.col;
            var idx = colIndex[col];
            var type = th.dataset.type;
            var groupRows = Array.prototype.slice.call(tbody.querySelectorAll('.group-row'));
            var asc = !(table.dataset.sortCol === col && table.dataset.sortDir === 'asc');
            groupRows.sort(function (a, b) {
                var x = a.cells[idx].dataset.sort !== undefined ? a.cells[idx].dataset.sort : a.cells[idx].textContent.trim();
                var y = b.cells[idx].dataset.sort !== undefined ? b.cells[idx].dataset.sort : b.cells[idx].textContent.trim();
                if (type === 'number') { x = parseFloat(x) || 0; y = parseFloat(y) || 0; }
                else { x = x.toLowerCase(); y = y.toLowerCase(); }
                if (x < y) return asc ? -1 : 1;
                if (x > y) return asc ? 1 : -1;
                return 0;
            });
            groupRows.forEach(function (row) {
                tbody.appendChild(row);
                tbody.appendChild(document.getElementById(row.dataset.detailTarget));
            });
            table.dataset.sortCol = col;
            table.dataset.sortDir = asc ? 'asc' : 'desc';
        });
    });
}

setupGroupedTable('slow-queries-table', { endpoint: 1, occurrences: 3, joins: 4, duration: 5 });
setupGroupedTable('endpoint-findings-table', { endpoint: 1, repeats: 2, instances: 3, dbtime: 4, ratio: 5, repeat: 6, qcount: 7 });
"""


def severity_class(ratio: float) -> str:
    """CSS class for how far a finding is past its threshold (all rows here already exceed
    it, so this bands *how bad*, not good-vs-bad like a coverage report's colors)."""
    if ratio >= 5:
        return "sev-high"
    if ratio >= 2:
        return "sev-medium"
    return "sev-low"


def phase_sort_value(phase: str) -> int:
    """Numeric rank for the Phase column's `data-sort` (must live on the <td>, not the badge
    <span> inside it — the HTML report's click-to-sort script reads `cells[colIndex].dataset.sort`,
    i.e. the cell element itself)."""
    return 1 if phase == "action" else 0


def html_phase(phase: str, has_endpoint: bool = True) -> str:
    """Badge for the setup-vs-action split (see phase_sort_key). A query with no `httpEndpoint`
    never ran inside a Playwright-driven request, so it gets its own badge rather than sharing the
    generic `?` with the (separately-diagnosed) header-corruption bug, which also renders as `?`.
    `?` itself now only covers findings that DO have an HTTP context but an unrecognised phase
    value -- e.g. a not-yet-diagnosed edge case, genuinely worth a reader's attention."""
    if not has_endpoint:
        return '<span class="phase-badge phase-background">background</span>'
    if phase == "action":
        return '<span class="phase-badge phase-action">action</span>'
    if phase == "setup":
        return '<span class="phase-badge phase-setup">setup</span>'
    return '<span class="muted">?</span>'


def html_endpoint(method: str, endpoint: str, thread_name: str = None) -> str:
    if not method and not endpoint:
        label = f"(background: {thread_name})" if thread_name else "(background)"
        return f'<span class="muted">{html.escape(label)}</span>'
    return html.escape(f"{method or '?'} {endpoint or '?'}")


SQL_KEYWORDS = (
    "SELECT|FROM|WHERE|LEFT|RIGHT|INNER|OUTER|JOIN|ON|AND|OR|NOT|IN|IS|NULL|ORDER|BY|GROUP|HAVING|"
    "INSERT|INTO|VALUES|UPDATE|SET|DELETE|AS|DISTINCT|LIMIT|OFFSET|EXISTS|UNION|ALL|ASC|DESC|LIKE|"
    "BETWEEN|COUNT|SUM|AVG|MAX|MIN|CASE|WHEN|THEN|ELSE|END|FETCH|FIRST|ROWS|ONLY"
)
SQL_TOKEN_PATTERN = re.compile(
    r"(?P<string>'[^']*')"
    r"|(?P<placeholder>\?)"
    r"|(?P<number>\b\d+\.?\d*\b)"
    rf"|(?P<keyword>\b(?:{SQL_KEYWORDS})\b)",
    re.IGNORECASE,
)


def highlight_sql(sql: str) -> str:
    """Lightweight, self-contained SQL syntax highlighting -- no external library (the report has
    no external assets by design), just a single-pass regex tokenizer wrapping recognized pieces
    in spans styled via the --sql-* CSS tokens."""
    if not sql:
        return ""
    out = []
    last_end = 0
    for m in SQL_TOKEN_PATTERN.finditer(sql):
        out.append(html.escape(sql[last_end:m.start()]))
        out.append(f'<span class="sql-{m.lastgroup}">{html.escape(m.group(0))}</span>')
        last_end = m.end()
    out.append(html.escape(sql[last_end:]))
    return "".join(out)


def html_sql_cell(sql: str) -> str:
    """Short queries render inline; long ones collapse behind <details> so the table stays
    scannable while the full, untruncated SQL is still one click away (impossible in the
    Markdown comment, which can only ever show the truncated form)."""
    if not sql:
        return ""
    if len(sql) <= 100:
        return f"<code>{highlight_sql(sql)}</code>"
    summary = highlight_sql(trunc_plain(sql, 100))
    return f"<details><summary><code>{summary}</code></summary><pre>{highlight_sql(sql)}</pre></details>"


def slow_query_instance_rows_html(occurrences: list) -> str:
    """One row per individual threshold-crossing execution that collapsed into this group --
    duration, phase, test. Same capping/sortable-nested-table pattern as instance_rows_html."""
    ranked = sorted(occurrences, key=lambda o: o["ms"], reverse=True)
    hidden_count = max(0, len(ranked) - ENDPOINT_FINDINGS_MAX_INSTANCES_SHOWN)
    ranked = ranked[:ENDPOINT_FINDINGS_MAX_INSTANCES_SHOWN]
    rows = []
    for o in ranked:
        test = html.escape(o["test"]) if o.get("test") else '<span class="muted">—</span>'
        rows.append(
            "<tr>"
            f'<td class="num" data-sort="{o["ms"]}">{o["ms"]} ms</td>'
            f'<td data-sort="{phase_sort_value(o.get("phase"))}">{html_phase(o.get("phase"), o.get("has_endpoint", True))}</td>'
            f"<td>{test}</td>"
            "</tr>"
        )
    note = ""
    if hidden_count:
        note = f'<p class="muted" style="margin:6px 0 0;font-size:12px;">+ {hidden_count} more occurrence{"s" if hidden_count != 1 else ""} not shown</p>'
    return (
        '<table class="nested-table sortable"><thead><tr>'
        '<th data-type="number">Duration</th><th data-type="number">Phase</th><th data-type="string">Test</th>'
        f"</tr></thead><tbody>{''.join(rows)}</tbody></table>{note}"
    )


def build_slow_queries_table_html(slow_queries: list, threshold_ms: int, static_index: dict = None) -> str:
    """Grouped, collapsible Slow Queries table -- the same query at the same endpoint crossing
    the threshold many times across a handful of tests collapses into one row (real example: 179
    raw captures, 1 distinct query, 3 tests) instead of flooding the table with near-duplicates.
    Click a row to see the individual occurrences it was grouped from."""
    if not slow_queries:
        return f'<p class="ok">✅ No slow queries detected (threshold: {threshold_ms} ms)</p>'
    static_index = static_index or {}

    groups = compute_slow_query_groups(slow_queries)
    rows_html = []
    for idx, ((method, endpoint, sql, thread_name), g) in enumerate(groups):
        ratio = g["worst_ms"] / threshold_ms if threshold_ms else 0
        badge = static_badge_html(sql, static_index)
        detail_id = f"slow-query-detail-{idx}"
        rows_html.append(
            f'<tr class="{severity_class(ratio)} group-row" data-detail-target="{detail_id}">'
            '<td class="expand-cell"><span class="expand-arrow">&#9654;</span></td>'
            f"<td>{html_endpoint(method, endpoint, thread_name)}</td>"
            f'<td class="sql-cell">{highlight_sql(trunc_plain(sql, 70))}{badge}</td>'
            f'<td class="num" data-sort="{g["count"]}">{g["count"]}×</td>'
            f'<td class="num" data-sort="{g["join_count"]}">{g["join_count"]}</td>'
            f'<td class="num" data-sort="{g["worst_ms"]}">{g["worst_ms"]} ms</td>'
            "</tr>"
            f'<tr class="detail-row" id="{detail_id}" hidden><td></td><td colspan="5">{slow_query_instance_rows_html(g["occurrences"])}</td></tr>'
        )

    return (
        '<input class="filter-box" type="search" placeholder="Filter by endpoint, test, phase, or SQL…" data-target="slow-queries-table">\n'
        # Deliberately NOT class="sortable" -- see build_endpoint_findings_table_html for why
        # grouped tables use the dedicated setupGroupedTable() sort instead.
        '<table class="grouped-table" id="slow-queries-table">\n'
        "<thead><tr>"
        "<th></th>"
        '<th data-type="string" data-col="endpoint">Endpoint</th>'
        "<th>SQL</th>"
        '<th data-type="number" data-col="occurrences">Occurrences</th>'
        '<th data-type="number" data-col="joins" title="Tables joined in this one query -- structural, doesn\'t depend on environment speed">Joins</th>'
        '<th data-type="number" data-col="duration">Worst Duration</th>'
        "</tr></thead>\n"
        f"<tbody>{''.join(rows_html)}</tbody>\n"
        "</table>"
    )


def shared_query_breakdown_html(queries: dict, static_index: dict) -> str:
    """The query breakdown for a pattern, rendered ONCE per group rather than once per instance:
    every instance in a group has the identical query *set* by construction (that's the grouping
    key), so per-instance timing/count can differ slightly but repeating the full SQL text per
    instance would just be duplication, not new information.

    A plain `sortable` table, not a group/detail-row pair: there's no pairing to protect here, so
    the shared generic click-to-sort script (see HTML_REPORT_JS) already handles it correctly."""
    ranked = sorted(queries.items(), key=lambda kv: kv[1][1], reverse=True)
    rows = []
    for sql, (count, qms) in ranked:
        badge = static_badge_html(sql, static_index)
        rows.append(f'<tr><td class="num" data-sort="{count}">{count}×</td><td class="num" data-sort="{qms}">{qms} ms</td><td class="sql-cell">{html_sql_cell(sql)}{badge}</td></tr>')
    label = f"{len(ranked)} distinct quer{'y' if len(ranked) == 1 else 'ies'}"
    return (
        f'<details class="query-breakdown"><summary>{label}</summary>'
        '<table class="nested-table sortable"><thead><tr><th data-type="number">Count</th><th data-type="number">Total Time</th><th data-type="string">SQL</th></tr></thead>'
        f"<tbody>{''.join(rows)}</tbody></table></details>"
    )


def instance_rows_html(occurrences: list) -> str:
    """Reproduces the columns the old, ungrouped Endpoint Timing row used to show -- ratio,
    total, DB time, query count, phase, test -- as a nested table under the pattern it belongs
    to. Capped worst-first: a pattern seen in dozens of tests doesn't need one row per test to
    make its point, same capping philosophy the rest of the report already uses.

    A plain `sortable` table (see shared_query_breakdown_html for why that's safe here)."""
    ranked = sorted(occurrences, key=lambda o: o["ratio"], reverse=True)
    hidden_count = max(0, len(ranked) - ENDPOINT_FINDINGS_MAX_INSTANCES_SHOWN)
    ranked = ranked[:ENDPOINT_FINDINGS_MAX_INSTANCES_SHOWN]
    rows = []
    for o in ranked:
        has_endpoint = True  # every EndpointTimingRecord comes from a real HTTP request by construction
        test = html.escape(o["test"]) if o.get("test") else '<span class="muted">—</span>'
        rows.append(
            "<tr>"
            f'<td class="num" data-sort="{o["ratio"]:.4f}">{o["ratio"] * 100:.0f}%</td>'
            f'<td class="num" data-sort="{o["total_ms"]}">{o["total_ms"]} ms</td>'
            f'<td class="num" data-sort="{o["db_ms"]}">{o["db_ms"]} ms</td>'
            f'<td class="num" data-sort="{o["qcount"]}">{o["qcount"]}</td>'
            f'<td data-sort="{phase_sort_value(o.get("phase"))}">{html_phase(o.get("phase"), has_endpoint)}</td>'
            f"<td>{test}</td>"
            "</tr>"
        )
    note = ""
    if hidden_count:
        note = f'<p class="muted" style="margin:6px 0 0;font-size:12px;">+ {hidden_count} more instance{"s" if hidden_count != 1 else ""} not shown</p>'
    return (
        '<table class="nested-table sortable"><thead><tr>'
        '<th data-type="number">Ratio</th><th data-type="number">Total</th><th data-type="number">DB Time</th>'
        '<th data-type="number">Queries</th><th data-type="number">Phase</th><th data-type="string">Test</th>'
        f"</tr></thead><tbody>{''.join(rows)}</tbody></table>{note}"
    )


def endpoint_findings_severity(ratio: float, max_repeat: int) -> str:
    if ratio >= 0.6 or max_repeat > 20:
        return "sev-high"
    if ratio >= 0.4 or max_repeat > 10:
        return "sev-medium"
    return "sev-low"


def build_endpoint_findings_table_html(findings: dict, static_index: dict = None) -> str:
    """Grouped, collapsible Endpoint Findings table -- replaces the old separate N+1 Suspects and
    Endpoint Timing tables. See compute_endpoint_findings for the grouping/inclusion rule."""
    static_index = static_index or {}
    rows = findings["rows"]
    if not rows:
        return '<p class="ok">✅ No endpoint findings above the inclusion floor.</p>'

    rows_html = []
    for idx, ((method, endpoint, templates), v) in enumerate(rows):
        what_repeats = worst_query_summary_html(v["queries"], len(templates))
        detail_id = f"endpoint-finding-detail-{idx}"
        rows_html.append(
            f'<tr class="{endpoint_findings_severity(v["worst_ratio"], v["max_repeat"])} group-row" data-detail-target="{detail_id}">'
            '<td class="expand-cell"><span class="expand-arrow">&#9654;</span></td>'
            f"<td>{html.escape(f'{method} {endpoint}')}</td>"
            f'<td class="sql-cell">{what_repeats}</td>'
            f'<td class="num" data-sort="{v["instances"]}">{v["instances"]}</td>'
            f'<td class="num" data-sort="{v["worst_dbtime"]}">{v["worst_dbtime"]} ms</td>'
            f'<td class="num" data-sort="{v["worst_ratio"]:.4f}">{v["worst_ratio"] * 100:.0f}%</td>'
            f'<td class="num" data-sort="{v["max_repeat"]}">{v["max_repeat"]}×</td>'
            f'<td class="num" data-sort="{v["worst_qcount"]}">{v["worst_qcount"]}</td>'
            "</tr>"
            f'<tr class="detail-row" id="{detail_id}" hidden><td></td><td colspan="7">'
            f'{instance_rows_html(v["occurrences"])}<div style="margin-top:10px;">{shared_query_breakdown_html(v["queries"], static_index)}</div>'
            "</td></tr>"
        )

    return (
        '<input class="filter-box" type="search" placeholder="Filter by endpoint or SQL…" data-target="endpoint-findings-table">\n'
        # Deliberately NOT class="sortable": the shared click-to-sort script reorders every <tr>
        # in the tbody generically, which would scramble group/detail-row pairing. See the
        # dedicated sort script scoped to #endpoint-findings-table in HTML_REPORT_JS instead.
        '<table class="grouped-table" id="endpoint-findings-table">\n'
        "<thead><tr>"
        "<th></th>"
        '<th data-type="string" data-col="endpoint">Endpoint</th>'
        '<th data-type="string" data-col="repeats">What Repeats</th>'
        '<th data-type="number" data-col="instances">Seen In</th>'
        '<th data-type="number" data-col="dbtime">Worst DB Time</th>'
        '<th data-type="number" data-col="ratio">Worst Ratio</th>'
        '<th data-type="number" data-col="repeat">Max Repeat</th>'
        '<th data-type="number" data-col="qcount">Total Queries</th>'
        "</tr></thead>\n"
        f"<tbody>{''.join(rows_html)}</tbody>\n"
        "</table>"
    )


def worst_query_summary_html(queries: dict, template_count: int) -> str:
    if not queries:
        return f"{template_count} templates"
    worst_sql, (worst_count, _) = max(queries.items(), key=lambda kv: kv[1][0])
    if worst_count <= 1:
        return f"{template_count} templates"
    return f'{highlight_sql(trunc_plain(worst_sql, 70))} <strong>×{worst_count}</strong>'


def reachable_path_html(paths: list) -> str:
    """`reachablePath` is a list of already-complete chains -- one per reachable entity (e.g.
    "studentExam", "studentExam -> exam", "studentExam -> exam -> course" are three SEPARATE
    entries, each a full chain to a *different* entity, not three fragments of one chain. Joining
    them together with another "->" (the previous bug here) makes independent findings look like
    one impossibly-repeating chain -- render each as its own list item instead."""
    if not paths:
        return ""
    items = "".join(f"<li>{html.escape(p)}</li>" for p in paths)
    return f'<ul style="margin:0;padding-left:18px;">{items}</ul>'


def build_static_findings_table_html(static_findings: list) -> str:
    """Every static finding, unfiltered -- including ones with no dynamic match, which are
    exactly the interesting case: a structural risk E2E's traffic never happened to exercise."""
    if not static_findings:
        return '<p class="ok">No static findings.</p>'

    rows = []
    for i, f in enumerate(static_findings):
        # id must match the index build_static_index assigns (same list, same order, same
        # enumerate) -- that's what a badge's data-finding-indices points at to highlight this
        # exact row rather than searching for it by name.
        entity = f.get("entityClass") or "—"
        detail = html_sql_cell(f["snippet"]) if f.get("snippet") else reachable_path_html(f.get("reachablePath", []))
        rows.append(
            f'<tr id="static-finding-{i}">'
            f"<td>{html.escape(f['type'])}</td>"
            f"<td>{html.escape(entity)}</td>"
            f"<td>{html.escape(static_finding_label(f))}</td>"
            f'<td class="sql-cell">{detail}</td>'
            f"<td>{html.escape(f.get('file', ''))}</td>"
            "</tr>"
        )

    return (
        '<input class="filter-box" type="search" placeholder="Filter by type, entity, or file…" data-target="static-findings-table">\n'
        '<table class="sortable" id="static-findings-table">\n'
        "<thead><tr>"
        '<th data-type="string">Type</th>'
        '<th data-type="string">Entity</th>'
        '<th data-type="string">Detail</th>'
        '<th data-type="string">Path / Snippet</th>'
        '<th data-type="string">File</th>'
        "</tr></thead>\n"
        f"<tbody>{''.join(rows)}</tbody>\n"
        "</table>"
    )


def build_html_report(report: dict, run_url: str = "", static_findings: list = None) -> str:
    static_findings = static_findings or []
    static_index = build_static_index(static_findings)
    # Unlike build_report's "?" placeholder (display-only), this feeds severity_class's division
    # below, so a missing key needs a numeric fallback rather than a string one.
    threshold_ms = report.get("thresholdMs") if isinstance(report.get("thresholdMs"), (int, float)) else 100
    generated_at = iso_to_human(report.get("generatedAt", ""))
    slow_queries = report.get("slowQueries", [])
    slow_count = len(compute_slow_query_groups(slow_queries))  # distinct groups, not raw occurrences -- see build_report
    findings = compute_endpoint_findings(report.get("endpointTimings", []))
    findings_count = len(findings["rows"])
    action_count = sum(1 for f in slow_queries if f.get("phase") == "action")
    setup_count = sum(1 for f in slow_queries if f.get("phase") == "setup")

    run_link_html = f'<p><a href="{html.escape(run_url)}">View CI run</a></p>' if run_url else ""

    return f"""<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Slow Query Report</title>
<style>{HTML_REPORT_CSS}</style>
</head>
<body>
<div class="wrapper">
<h1>Slow Query Report</h1>
<p class="muted">Generated at {html.escape(generated_at)}</p>
{run_link_html}
<p class="lead">Findings captured automatically during this E2E run: individual database queries whose execution time exceeded the configured threshold ("Slow Queries"), and endpoints whose queries repeat within a request and clear at least one significance floor ("Endpoint Findings") -- grouped by endpoint and query shape, so the same bug tested many times shows up once, not once per test.</p>
<div class="stats">
<div class="stat-tile"><span class="stat-value">{slow_count}</span><span class="stat-label">Slow Queries</span><span class="stat-sub">distinct, from {len(slow_queries)} occurrences · threshold {threshold_ms} ms</span></div>
<div class="stat-tile"><span class="stat-value">{findings_count}</span><span class="stat-label">Endpoint Findings</span><span class="stat-sub">from {findings['distinct_patterns']} distinct patterns</span></div>
<div class="stat-tile"><span class="stat-value">{action_count}</span><span class="stat-label">Action-Phase Occurrences</span><span class="stat-sub">real UI-driven traffic — start here</span></div>
<div class="stat-tile"><span class="stat-value">{setup_count}</span><span class="stat-label">Setup-Phase Occurrences</span><span class="stat-sub">test fixture traffic, lower priority</span></div>
</div>
<p class="muted">Type "action" or "setup" into a table's filter box to isolate findings by phase.</p>

<div class="tabs" role="tablist">
<button class="tab-btn active" type="button" role="tab" aria-selected="true" aria-controls="panel-slow-queries">🐢 Slow Queries <span class="tab-count">{slow_count}</span></button>
<button class="tab-btn" type="button" role="tab" aria-selected="false" aria-controls="panel-endpoint-findings">🔁 Endpoint Findings <span class="tab-count">{findings_count}</span></button>
<button class="tab-btn" type="button" role="tab" aria-selected="false" aria-controls="panel-static-findings">🔍 Static Findings <span class="tab-count">{len(static_findings)}</span></button>
</div>

<section class="tab-panel" id="panel-slow-queries" role="tabpanel">
{build_slow_queries_table_html(slow_queries, threshold_ms, static_index)}
</section>
<section class="tab-panel" id="panel-endpoint-findings" role="tabpanel" hidden>
<p class="muted">{findings['total_requests']} requests captured → {findings['distinct_patterns']} distinct (endpoint, query-shape) patterns → {findings['repeating_patterns']} with a repeated query → {findings['floor_cleared']} clearing a floor (DB-time ratio ≥ 30%, DB time ≥ 20ms, or a query repeating &gt;5×) → <strong>{findings_count} shown</strong> (top 10% per axis, unioned). Click a row to see the individual requests it was grouped from, and the shared query breakdown.</p>
{build_endpoint_findings_table_html(findings, static_index)}
</section>
<section class="tab-panel" id="panel-static-findings" role="tabpanel" hidden>
<p class="muted">Findings from a pure source scan (no test run needed) -- wide @EntityGraph/JOIN FETCH definitions, and entities whose eager-fetch association graph transitively touches many other entities. A 🔍 badge on a row means its root table matches one of these by name (heuristic: PascalCase → snake_case), not a proven link. Findings with no badge anywhere are just as worth a look -- they're structural risks no test happened to trigger yet.</p>
{build_static_findings_table_html(static_findings)}
</section>
</div>
<script>{HTML_REPORT_JS}</script>
</body>
</html>
"""


# ---------------------------------------------------------------------------
# Entry point
# ---------------------------------------------------------------------------

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: format_slow_query_report.py <report.json> [run_url] [html_output_path] [static_findings.json]", file=sys.stderr)
        sys.exit(1)

    report_path = sys.argv[1]
    run_url = sys.argv[2] if len(sys.argv) > 2 else ""
    html_output_path = sys.argv[3] if len(sys.argv) > 3 else ""
    static_findings_path = sys.argv[4] if len(sys.argv) > 4 else ""
    try:
        report = load_report(report_path)
    except (json.JSONDecodeError, FileNotFoundError) as exc:
        print(f"## ⚠️ Slow Query Report Parse Error\n\n{exc}", file=sys.stderr)
        sys.exit(1)

    static_findings = []
    if static_findings_path:
        try:
            static_findings = load_report(static_findings_path)
        except (json.JSONDecodeError, FileNotFoundError) as exc:
            print(f"Warning: could not load static findings from {static_findings_path}: {exc}", file=sys.stderr)

    print(build_report(report, run_url))

    if html_output_path:
        with open(html_output_path, "w", encoding="utf-8") as f:
            f.write(build_html_report(report, run_url, static_findings))
