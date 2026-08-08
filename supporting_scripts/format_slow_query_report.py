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
import sys
from datetime import timezone, datetime

# GitHub caps issue/PR comment bodies at 65536 characters. A "run all tests" pass can collect
# thousands of findings, so only the worst offenders are rendered inline; the rest are still
# available, untruncated, in the uploaded HTML artifact (see build_html_report).
MAX_ROWS_PER_SECTION = 20


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


def fmt_endpoint(method: str, endpoint: str) -> str:
    if not method and not endpoint:
        return "*(background)*"
    return f"`{method or '?'} {endpoint or '?'}`"


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


def build_slow_queries_section(slow_queries: list, threshold_ms: int, run_url: str = "") -> str:
    if not slow_queries:
        return f"✅ **No slow queries** detected (threshold: {threshold_ms} ms)\n"

    ranked = sorted(slow_queries, key=lambda q: q.get("executionTimeMs", 0), reverse=True)
    shown = ranked[:MAX_ROWS_PER_SECTION]
    hidden_count = len(ranked) - len(shown)

    lines = [
        f"### 🐢 Slow Queries ({len(slow_queries)} found, threshold: {threshold_ms} ms)\n",
        "| # | Duration | Endpoint | Test | SQL (truncated) |",
        "|---|----------|----------|------|-----------------|",
    ]
    for i, q in enumerate(shown, start=1):
        endpoint = fmt_endpoint(q.get("httpMethod"), q.get("httpEndpoint"))
        test = f"`{q['testName']}`" if q.get("testName") else "—"
        sql = trunc(q.get("sql", ""))
        lines.append(f"| {i} | **{q['executionTimeMs']} ms** | {endpoint} | {test} | `{sql}` |")
    if hidden_count > 0:
        lines.append("")
        lines.append(f"_Showing the {len(shown)} slowest. {hidden_count} more not shown — see the {artifact_link(run_url)} for the full list._")
    return "\n".join(lines) + "\n"


def build_n1_section(n1_suspects: list, n1_threshold: int, run_url: str = "") -> str:
    if not n1_suspects:
        return f"✅ **No N+1 patterns** detected (threshold: >{n1_threshold} occurrences per request)\n"

    ranked = sorted(n1_suspects, key=lambda s: s.get("occurrences", 0), reverse=True)
    shown = ranked[:MAX_ROWS_PER_SECTION]
    hidden_count = len(ranked) - len(shown)

    lines = [
        f"### 🔁 N+1 Query Suspects ({len(n1_suspects)} found, threshold: >{n1_threshold}×/request)\n",
        "| # | Occurrences | Endpoint | Test | SQL template (truncated) |",
        "|---|-------------|----------|------|--------------------------|",
    ]
    for i, s in enumerate(shown, start=1):
        endpoint = fmt_endpoint(s.get("httpMethod"), s.get("httpEndpoint"))
        test = f"`{s['testName']}`" if s.get("testName") else "—"
        sql = trunc(s.get("normalizedSql", ""))
        lines.append(f"| {i} | **{s['occurrences']}×** | {endpoint} | {test} | `{sql}` |")
    if hidden_count > 0:
        lines.append("")
        lines.append(f"_Showing the {len(shown)} worst. {hidden_count} more not shown — see the {artifact_link(run_url)} for the full list._")
    return "\n".join(lines) + "\n"


def build_report(report: dict, run_url: str = "") -> str:
    threshold_ms = report.get("thresholdMs", "?")
    n1_threshold = report.get("n1Threshold", "?")
    slow_count = report.get("slowQueryCount", 0)
    n1_count = report.get("n1SuspectCount", 0)
    generated_at = iso_to_human(report.get("generatedAt", ""))

    slow_queries = report.get("slowQueries", [])
    n1_suspects = report.get("n1Suspects", [])

    # --- Header ---
    if slow_count == 0 and n1_count == 0:
        header_emoji = "✅"
        header_status = "No performance regressions detected"
    else:
        header_emoji = "⚠️"
        issues = []
        if slow_count > 0:
            issues.append(f"{slow_count} slow quer{'y' if slow_count == 1 else 'ies'}")
        if n1_count > 0:
            issues.append(f"{n1_count} N+1 suspect{'s' if n1_count != 1 else ''}")
        header_status = "Performance issues found: " + " · ".join(issues)

    lines = [
        "",
        "---",
        f"## {header_emoji} Slow Query Report",
        "",
        f"**{header_status}**",
        "",
        f"> Generated at: {generated_at}  ",
        f"> Slow-query threshold: **{threshold_ms} ms** · N+1 detection: **>{n1_threshold}×/request**",
        "",
        build_slow_queries_section(slow_queries, threshold_ms, run_url),
        build_n1_section(n1_suspects, n1_threshold, run_url),
        "",
        "<details>",
        "<summary>How to investigate</summary>",
        "",
        "**Slow queries** exceed the per-query execution time threshold.",
        "Common causes: missing index, unbounded result set, complex JOIN.",
        "",
        "**N+1 suspects** are queries whose *normalised* SQL template was executed more than",
        f"{n1_threshold} times within a single HTTP request — a strong indicator that",
        "related data is being loaded one entity at a time instead of in a single JOIN.",
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
}
@media (prefers-color-scheme: dark) {
    :root {
        --bg: #14161a;
        --fg: #e5e7eb;
        --muted: #9ca3af;
        --border: #2d323b;
        --row-hover: #1f232a;
        --tile-bg: #1c1f26;
    }
}
* { box-sizing: border-box; }
body { margin: 0; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; background: var(--bg); color: var(--fg); }
.wrapper { max-width: 100%; margin: 0; padding: 24px clamp(16px, 3vw, 48px) 64px; }
h1 { margin-bottom: 4px; }
h2 { margin-top: 40px; }
.muted { color: var(--muted); }
.ok { color: #16a34a; font-weight: 600; }
.stats { display: flex; gap: 16px; margin: 20px 0 32px; flex-wrap: wrap; }
.stat-tile { background: var(--tile-bg); border: 1px solid var(--border); border-radius: 8px; padding: 12px 20px; min-width: 160px; }
.stat-value { display: block; font-size: 28px; font-weight: 700; }
.stat-label { display: block; font-size: 13px; color: var(--muted); }
.stat-sub { display: block; font-size: 12px; color: var(--muted); }
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
"""


def severity_class(ratio: float) -> str:
    """CSS class for how far a finding is past its threshold (all rows here already exceed
    it, so this bands *how bad*, not good-vs-bad like a coverage report's colors)."""
    if ratio >= 5:
        return "sev-high"
    if ratio >= 2:
        return "sev-medium"
    return "sev-low"


def html_endpoint(method: str, endpoint: str) -> str:
    if not method and not endpoint:
        return '<span class="muted">(background)</span>'
    return html.escape(f"{method or '?'} {endpoint or '?'}")


def html_sql_cell(sql: str) -> str:
    """Short queries render inline; long ones collapse behind <details> so the table stays
    scannable while the full, untruncated SQL is still one click away (impossible in the
    Markdown comment, which can only ever show the truncated form)."""
    if not sql:
        return ""
    escaped_full = html.escape(sql)
    if len(sql) <= 100:
        return f"<code>{escaped_full}</code>"
    summary = html.escape(trunc_plain(sql, 100))
    return f"<details><summary><code>{summary}</code></summary><pre>{escaped_full}</pre></details>"


def build_slow_queries_table_html(slow_queries: list, threshold_ms: int) -> str:
    if not slow_queries:
        return f'<p class="ok">✅ No slow queries detected (threshold: {threshold_ms} ms)</p>'

    ranked = sorted(slow_queries, key=lambda q: q.get("executionTimeMs", 0), reverse=True)
    rows = []
    for q in ranked:
        duration = q.get("executionTimeMs", 0)
        ratio = duration / threshold_ms if threshold_ms else 0
        test = html.escape(q["testName"]) if q.get("testName") else '<span class="muted">—</span>'
        rows.append(
            f'<tr class="{severity_class(ratio)}">'
            f'<td class="num" data-sort="{duration}">{duration} ms</td>'
            f"<td>{html_endpoint(q.get('httpMethod'), q.get('httpEndpoint'))}</td>"
            f"<td>{test}</td>"
            f'<td class="sql-cell">{html_sql_cell(q.get("sql", ""))}</td>'
            f"</tr>"
        )

    return (
        '<input class="filter-box" type="search" placeholder="Filter by endpoint, test, or SQL…" data-target="slow-queries-table">\n'
        '<table class="sortable" id="slow-queries-table">\n'
        "<thead><tr>"
        '<th data-type="number">Duration</th>'
        '<th data-type="string">Endpoint</th>'
        '<th data-type="string">Test</th>'
        '<th data-type="string">SQL</th>'
        "</tr></thead>\n"
        f"<tbody>{''.join(rows)}</tbody>\n"
        "</table>"
    )


def build_n1_table_html(n1_suspects: list, n1_threshold: int) -> str:
    if not n1_suspects:
        return f'<p class="ok">✅ No N+1 patterns detected (threshold: &gt;{n1_threshold} occurrences per request)</p>'

    ranked = sorted(n1_suspects, key=lambda s: s.get("occurrences", 0), reverse=True)
    rows = []
    for s in ranked:
        occurrences = s.get("occurrences", 0)
        ratio = occurrences / n1_threshold if n1_threshold else 0
        test = html.escape(s["testName"]) if s.get("testName") else '<span class="muted">—</span>'
        rows.append(
            f'<tr class="{severity_class(ratio)}">'
            f'<td class="num" data-sort="{occurrences}">{occurrences}×</td>'
            f"<td>{html_endpoint(s.get('httpMethod'), s.get('httpEndpoint'))}</td>"
            f"<td>{test}</td>"
            f'<td class="sql-cell">{html_sql_cell(s.get("normalizedSql", ""))}</td>'
            f"</tr>"
        )

    return (
        '<input class="filter-box" type="search" placeholder="Filter by endpoint, test, or SQL…" data-target="n1-suspects-table">\n'
        '<table class="sortable" id="n1-suspects-table">\n'
        "<thead><tr>"
        '<th data-type="number">Occurrences</th>'
        '<th data-type="string">Endpoint</th>'
        '<th data-type="string">Test</th>'
        '<th data-type="string">SQL template</th>'
        "</tr></thead>\n"
        f"<tbody>{''.join(rows)}</tbody>\n"
        "</table>"
    )


def build_html_report(report: dict, run_url: str = "") -> str:
    # Unlike build_report's "?" placeholder (display-only), these two feed severity_class's
    # division below, so a missing key needs a numeric fallback rather than a string one.
    threshold_ms = report.get("thresholdMs") if isinstance(report.get("thresholdMs"), (int, float)) else 100
    n1_threshold = report.get("n1Threshold") if isinstance(report.get("n1Threshold"), (int, float)) else 5
    slow_count = report.get("slowQueryCount", 0)
    n1_count = report.get("n1SuspectCount", 0)
    generated_at = iso_to_human(report.get("generatedAt", ""))
    slow_queries = report.get("slowQueries", [])
    n1_suspects = report.get("n1Suspects", [])

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
<div class="stats">
<div class="stat-tile"><span class="stat-value">{slow_count}</span><span class="stat-label">Slow Queries</span><span class="stat-sub">threshold {threshold_ms} ms</span></div>
<div class="stat-tile"><span class="stat-value">{n1_count}</span><span class="stat-label">N+1 Suspects</span><span class="stat-sub">threshold &gt;{n1_threshold}×/request</span></div>
</div>

<h2>🐢 Slow Queries</h2>
{build_slow_queries_table_html(slow_queries, threshold_ms)}

<h2>🔁 N+1 Query Suspects</h2>
{build_n1_table_html(n1_suspects, n1_threshold)}
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
        print("Usage: format_slow_query_report.py <report.json> [run_url] [html_output_path]", file=sys.stderr)
        sys.exit(1)

    report_path = sys.argv[1]
    run_url = sys.argv[2] if len(sys.argv) > 2 else ""
    html_output_path = sys.argv[3] if len(sys.argv) > 3 else ""
    try:
        report = load_report(report_path)
    except (json.JSONDecodeError, FileNotFoundError) as exc:
        print(f"## ⚠️ Slow Query Report Parse Error\n\n{exc}", file=sys.stderr)
        sys.exit(1)

    print(build_report(report, run_url))

    if html_output_path:
        with open(html_output_path, "w", encoding="utf-8") as f:
            f.write(build_html_report(report, run_url))
