#!/usr/bin/env python3
"""
format_slow_query_report.py
---------------------------
Reads the JSON produced by GET /api/core/admin/performance/slow-queries and
prints a GitHub-flavored Markdown summary suitable for appending to a PR comment.

Usage:
    python3 format_slow_query_report.py slow-query-report.json

Output is written to stdout; redirect to a .md file from the shell.
"""

import json
import sys
from datetime import timezone, datetime

# GitHub caps issue/PR comment bodies at 65536 characters. A "run all tests" pass can collect
# thousands of findings, so only the worst offenders are rendered inline; the rest are still
# available in the uploaded JSON artifact.
MAX_ROWS_PER_SECTION = 20


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def load_report(path: str) -> dict:
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def trunc(text: str, max_len: int = 120) -> str:
    """Truncate a SQL string for display in a table cell."""
    if text is None:
        return ""
    # Backticks can't be backslash-escaped inside a single-backtick code span (Markdown treats
    # code span content as verbatim), so a literal backtick would prematurely close the span and
    # corrupt the table. Substitute a visually similar character instead.
    text = text.replace("|", "\\|").replace("\n", " ").replace("`", "'")
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
    """Markdown for 'the uploaded slow-query report artifact (JSON)', linked to the run's
    Artifacts section when a run URL is available, plain text otherwise (e.g. local runs)."""
    if run_url:
        return f"[uploaded slow-query report artifact (JSON)]({run_url})"
    return "uploaded slow-query report artifact (JSON)"


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
# Entry point
# ---------------------------------------------------------------------------

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: format_slow_query_report.py <report.json>", file=sys.stderr)
        sys.exit(1)

    report_path = sys.argv[1]
    run_url = sys.argv[2] if len(sys.argv) > 2 else ""
    try:
        report = load_report(report_path)
    except (json.JSONDecodeError, FileNotFoundError) as exc:
        print(f"## ⚠️ Slow Query Report Parse Error\n\n{exc}", file=sys.stderr)
        sys.exit(1)

    print(build_report(report, run_url))
