#!/usr/bin/env python3
"""
format_slow_query_report.py
---------------------------
Turns the JSON produced by GET /api/admin/performance/slow-queries (plus, optionally, the
static findings of find_slow_queries.py) into
  * a GitHub-flavored Markdown summary for the PR comment (stdout), and
  * a self-contained, sortable HTML report with every finding (the CI artifact).

Every finding is a yes/no rule with a stable key, so a run can be compared against a baseline
run (the latest develop run): with --baseline, findings whose key the baseline does not contain
are marked new, and the PR comment lists only those. --keys-out writes this run's keys, which is
what a develop run uploads as the next baseline.

Usage:
    python3 format_slow_query_report.py slow-query-report.json [run_url] [html_output_path] [static_findings.json]
        [--baseline baseline-keys.json] [--keys-out keys.json]
"""

import argparse
import hashlib
import html
import json
import re
import sys
from datetime import timezone, datetime

# GitHub caps issue/PR comment bodies at 65536 characters; the HTML artifact has everything.
MAX_ROWS_PER_SECTION = 20
MAX_OCCURRENCES_SHOWN = 15

STATIC_RULE_LABELS = {
    "repository_call_in_loop": "Repository call in a loop",
    "multiple_collection_fetch": "Several collections fetched in one query",
    "pageable_collection_fetch": "Collection fetched in a paged query",
    "eager_to_many": "EAGER to-many association",
    "wide_join_fetch": "More than 5 JOIN FETCHes in one @Query",
    "wide_entitygraph": "More than 5 @EntityGraph attribute paths",
}


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def load_json(path: str):
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def trunc(text: str, max_len: int = 120) -> str:
    """Truncate text for a Markdown table cell."""
    if text is None:
        return ""
    # a backtick would close the surrounding code span early; backslash-escaping does not work there
    text = text.replace("|", "\\|").replace("\n", " ").replace("`", "'")
    return text[:max_len] + "…" if len(text) > max_len else text


def trunc_plain(text: str, max_len: int = 100) -> str:
    """Truncate text for an HTML table cell (no Markdown escaping)."""
    if not text:
        return ""
    text = text.replace("\n", " ")
    return text[:max_len] + "…" if len(text) > max_len else text


def iso_to_human(iso: str) -> str:
    try:
        dt = datetime.fromisoformat(iso.replace("Z", "+00:00"))
        return dt.astimezone(timezone.utc).strftime("%Y-%m-%d %H:%M:%S UTC")
    except Exception:
        return iso


def artifact_link(run_url: str) -> str:
    if run_url:
        return f"[HTML report artifact]({run_url})"
    return "HTML report artifact"


def static_key(f: dict) -> str:
    """The finding's own stable key; the two older count-based rules have none, so one is derived
    from the file and the query text (stable as long as that query is not edited)."""
    if f.get("key"):
        return f["key"]
    digest = hashlib.sha1((f.get("snippet") or "").encode("utf-8")).hexdigest()[:10]
    return f"{f['type']}:{(f.get('file') or '').replace(chr(92), '/').split('/')[-1]}:{digest}"


# ---------------------------------------------------------------------------
# Repeated queries (dynamic N+1 / duplicate findings)
# ---------------------------------------------------------------------------

def repeated_query_key(f: dict) -> str:
    """Identity of a repeated-query finding across runs: the shape, the endpoint, the code that
    issued it (repository method and caller -- the same repository method looped over by two
    different callers is two places to fix) and the query. Deliberately without the test name or
    the repetition count: both depend on what a test does and how much data it creates, while the
    shape itself does not."""
    return "|".join([
        f.get("type") or "",
        f"{f.get('httpMethod') or ''} {f.get('httpEndpoint') or ''}",
        f.get("repositoryMethod") or "",
        f.get("callerMethod") or "",
        f.get("normalizedSql") or "",
    ])


def group_repeated_queries(findings: list) -> list:
    """One group per key, with the individual (test, phase) occurrences it was seen in."""
    groups = {}
    for f in findings:
        key = repeated_query_key(f)
        g = groups.setdefault(key, {
            "key": key, "type": f.get("type"), "method": f.get("httpMethod"), "endpoint": f.get("httpEndpoint"), "sql": f.get("normalizedSql") or "",
            "repositoryMethod": f.get("repositoryMethod"), "callerMethod": f.get("callerMethod"), "max_executions": 0, "requests": 0, "tests": set(),
            "occurrences": [], "call_sites": set(),
        })
        g["requests"] += 1
        # a duplicate across several pieces of code lists them; which ones can differ per request
        g["call_sites"].update(source_label(site) for site in f.get("callSites") or [])
        g["max_executions"] = max(g["max_executions"], f.get("executions", 0))
        if f.get("testName"):
            g["tests"].add(f["testName"])
        g["occurrences"].append({"test": f.get("testName"), "phase": f.get("phase"), "executions": f.get("executions", 0), "distinct": f.get("distinctParameterSets", 0)})
    return list(groups.values())


def link_static_confirmations(groups: list, static_findings: list):
    """Attach to each repeated-query group the repository-call-in-loop findings for the same
    repository method inside the same calling method. An exact match on method names -- both
    sides name code by Class.method -- not a guess from table names. Only this rule describes a
    query issued once per item; a query-shape rule (several collections fetched, a collection
    fetched with paging) is about a single query and is not evidence of a repeated one, even
    when it is on the same repository method."""
    by_loop_call = {}
    for i, f in enumerate(static_findings):
        if f.get("type") == "repository_call_in_loop":
            by_loop_call.setdefault((f.get("member"), f.get("repositoryMethod")), []).append(i)
    for g in groups:
        g["static_matches"] = sorted(set(by_loop_call.get((g["callerMethod"], g["repositoryMethod"]), [])))


def source_label(g: dict) -> str:
    repository_method, caller_method = g.get("repositoryMethod"), g.get("callerMethod")
    if repository_method and caller_method:
        return f"{repository_method} ← {caller_method}"
    return repository_method or caller_method or "?"


# ---------------------------------------------------------------------------
# Problems: repeated queries grouped by the place a fix goes
# ---------------------------------------------------------------------------
# A loop is fixed in the method that loops, so N+1 findings and duplicates issued from one place are
# grouped by their caller. A duplicate without a single issuing place is fixed in the request as a
# whole, so it is grouped by its endpoint. Rows inserted one statement at a time are grouped by
# table: the SQL does not tell a save() in a loop from a cascade the database cannot batch.

INSERT_TABLE_PATTERN = re.compile(r"^\s*insert\s+into\s+(\w+)", re.IGNORECASE)

CATEGORY_LABELS = {
    "n_plus_one": "N+1",
    "duplicate_one_place": "Duplicate · one place",
    "duplicate_several_places": "Duplicate · several places",
    "insert": "Row-by-row insert",
}


def avoidable_executions(shape: str, executions: int, distinct: int) -> int:
    """Executions one request could have saved: a duplicate needs one per distinct parameter set,
    anything else one in total."""
    if shape == "DUPLICATE":
        return max(executions - max(distinct, 1), 0)
    return max(executions - 1, 0)


def summarize_sql(sql: str) -> str:
    """'select u1_0.id, … from jhi_user u1_0 left join … where u1_0.id=?' -> 'select from jhi_user (+1 join) where id = ?'."""
    s = " ".join((sql or "").split())
    verb = s.split(" ", 1)[0].lower() if s else "?"
    table_patterns = {"select": r"\bfrom (\w+)", "delete": r"\bfrom (\w+)", "update": r"^update (\w+)", "insert": r"\binto (\w+)"}
    match = re.search(table_patterns[verb], s, re.IGNORECASE) if verb in table_patterns else None
    table = match.group(1) if match else "?"
    joins = len(re.findall(r"\bjoin \w+", s, re.IGNORECASE)) if verb == "select" else 0
    where_match = re.search(r"\bwhere (.*)$", s, re.IGNORECASE)
    where = ""
    if where_match:
        where = re.sub(r"\b\w+_\d+\.", "", where_match.group(1))
        where = re.sub(r"\s*(<>|>=|<=|=|\bin\b)\s*", r" \1 ", where, flags=re.IGNORECASE)
        where = trunc_plain(" ".join(where.split()), 70)
    preposition = {"select": "from ", "delete": "from ", "insert": "into "}.get(verb, "")
    summary = f"{verb} {preposition}{table}"
    if joins:
        summary += f" (+{joins} join{'s' if joins > 1 else ''})"
    return summary + (f" where {where}" if where else "")


def endpoint_label(method: str, endpoint: str) -> str:
    return f"{method or '?'} {endpoint or '?'}"


def build_problems(groups: list) -> list:
    """One problem per place to fix, most avoidable executions first."""
    problems = {}
    for g in groups:
        insert = INSERT_TABLE_PATTERN.match(g["sql"])
        if insert:
            kind, anchor = "insert", insert.group(1).lower()
        elif g["callerMethod"] or g["repositoryMethod"]:
            kind, anchor = "function", g["callerMethod"] or g["repositoryMethod"]
        else:
            kind, anchor = "endpoint", endpoint_label(g["method"], g["endpoint"])
        problem = problems.setdefault((kind, anchor), {"kind": kind, "anchor": anchor, "queries": {}})
        # rows of one table inserted by different code are different places to batch them
        query_key = (g["repositoryMethod"], g["callerMethod"] if kind == "insert" else None, g["sql"])
        query = problem["queries"].setdefault(query_key, {"repositoryMethod": g["repositoryMethod"], "callerMethod": g["callerMethod"], "sql": g["sql"], "groups": [],
                                                          "call_sites": set()})
        query["groups"].append(g)
        query["call_sites"].update(g["call_sites"])
    return sorted((summarize_problem(p) for p in problems.values()), key=lambda p: (-p["avoidable"], -len(p["endpoints"]), -len(p["tests"]), p["anchor"]))


def summarize_problem(problem: dict) -> dict:
    queries = []
    for q in problem["queries"].values():
        if problem["kind"] == "insert":
            q["shape"] = "INSERT"
        else:
            q["shape"] = "N_PLUS_ONE" if any(g["type"] == "N_PLUS_ONE" for g in q["groups"]) else "DUPLICATE"
        q["requests"] = sorted(({**o, "endpoint": endpoint_label(g["method"], g["endpoint"]), "avoidable": avoidable_executions(g["type"], o["executions"], o["distinct"])}
                                for g in q["groups"] for o in g["occurrences"]), key=lambda r: (-r["avoidable"], -r["executions"]))
        worst = q["requests"][0]
        q.update(executions=worst["executions"], distinct=worst["distinct"], avoidable=worst["avoidable"], summary=summarize_sql(q["sql"]))
        queries.append(q)
    queries.sort(key=lambda q: -q["avoidable"])

    endpoint_avoidable = {}
    for q in queries:
        for r in q["requests"]:
            endpoint_avoidable[r["endpoint"]] = max(endpoint_avoidable.get(r["endpoint"], 0), r["avoidable"])
    groups = [g for q in queries for g in q["groups"]]
    if problem["kind"] == "insert":
        category = "insert"
    elif problem["kind"] == "endpoint":
        category = "duplicate_several_places"
    else:
        category = "n_plus_one" if any(q["shape"] == "N_PLUS_ONE" for q in queries) else "duplicate_one_place"
    problem.update(
        queries=queries,
        category=category,
        # the biggest single repeat, not the sum: a delete cascade issues many small repeats that one fix would not remove
        avoidable=queries[0]["avoidable"],
        endpoints=sorted(endpoint_avoidable, key=lambda e: (-endpoint_avoidable[e], e)),
        methods=sorted({g["method"] or "?" for g in groups}),
        tests=sorted(set().union(*(g["tests"] for g in groups))),
        phases=sorted({r["phase"] for q in queries for r in q["requests"] if r.get("phase") in ("action", "setup")}),
        static_matches=sorted({i for g in groups for i in g.get("static_matches", [])}),
        is_new=any(g.get("is_new") for g in groups),
    )
    return problem


def loops_before_inserts(problems: list) -> list:
    """Inserts rank after every loop: their SQL does not tell whether a loop or the id generation is the cause."""
    return [p for p in problems if p["category"] != "insert"] + [p for p in problems if p["category"] == "insert"]


def query_counts_label(q: dict) -> str:
    if q["shape"] == "INSERT":
        return f"{q['executions']} rows, one statement each"
    if q["distinct"] <= 1:
        return f"{q['executions']}× same parameters"
    label = f"{q['executions']}× · {q['distinct']} distinct"
    if q["executions"] > q["distinct"]:
        label += f" (each ~{round(q['executions'] / q['distinct'])}×)"
    return label


# ---------------------------------------------------------------------------
# Slow queries (context only: timing in CI is too noisy to judge a PR by)
# ---------------------------------------------------------------------------

def compute_slow_query_groups(slow_queries: list) -> list:
    """Groups raw slow-query captures by (endpoint, SQL template) -- the same query crossing the
    threshold many times across a few tests is one row, not hundreds. The thread only tells
    background queries apart; for a request it is whichever server thread happened to serve it."""
    groups = {}
    for q in slow_queries:
        thread_name = None if q.get("httpEndpoint") else q.get("threadName")
        key = (q.get("httpMethod"), q.get("httpEndpoint"), q.get("sql", ""), thread_name)
        g = groups.setdefault(key, {"count": 0, "worst_ms": 0, "join_count": q.get("joinCount", 0), "occurrences": []})
        g["count"] += 1
        g["worst_ms"] = max(g["worst_ms"], q.get("executionTimeMs", 0))
        g["occurrences"].append({"test": q.get("testName"), "phase": q.get("phase"), "ms": q.get("executionTimeMs", 0), "has_endpoint": bool(q.get("httpEndpoint"))})
    items = list(groups.items())
    items.sort(key=lambda kv: kv[1]["worst_ms"], reverse=True)
    return items


# ---------------------------------------------------------------------------
# Baseline comparison
# ---------------------------------------------------------------------------

def collect_keys(groups: list, static_findings: list) -> dict:
    return {"repeatedQueries": sorted(g["key"] for g in groups), "static": sorted({static_key(f) for f in static_findings})}


def mark_new(groups: list, static_findings: list, baseline: dict):
    """Marks what the baseline does not contain. Without a baseline nothing is marked: absent
    evidence is not the same as new."""
    if baseline is None:
        return
    repeated = set(baseline.get("repeatedQueries", []))
    static = set(baseline.get("static", []))
    for g in groups:
        g["is_new"] = g["key"] not in repeated
    for f in static_findings:
        f["is_new"] = static_key(f) not in static


# ---------------------------------------------------------------------------
# Markdown (PR comment)
# ---------------------------------------------------------------------------

def problem_rows_md(problems: list) -> list:
    lines = ["| Where to fix | Shape | Avoidable / request | Biggest repeat | Endpoints | Tests |", "|---|---|---|---|---|---|"]
    for p in problems:
        confirmed = " · 🔍 static rule agrees" if p["static_matches"] else ""
        biggest = p["queries"][0]
        more = f" +{len(p['endpoints']) - 1} more" if len(p["endpoints"]) > 1 else ""
        lines.append(f"| `{trunc(p['anchor'], 90)}` | {CATEGORY_LABELS[p['category']]}{confirmed} | {p['avoidable']} "
                     f"| `{trunc(biggest['summary'], 80)}` {query_counts_label(biggest)} | `{trunc(p['endpoints'][0], 80)}`{more} | {len(p['tests'])} |")
    return lines


def static_rows_md(findings: list) -> list:
    lines = ["| Rule | Where | Detail |", "|---|---|---|"]
    for f in findings:
        where = f.get("member") or (f.get("file") or "").replace("\\", "/").split("/")[-1]
        lines.append(f"| {STATIC_RULE_LABELS.get(f['type'], f['type'])} | `{trunc(where, 90)}` | {trunc(f.get('detail') or '', 140)} |")
    return lines


def capped(items: list, lines_for, run_url: str, what: str) -> list:
    shown = items[:MAX_ROWS_PER_SECTION]
    lines = lines_for(shown)
    if len(items) > len(shown):
        lines += ["", f"_{len(items) - len(shown)} more {what} not shown — see the {artifact_link(run_url)}._"]
    return lines


def build_report(report: dict, groups: list, static_findings: list, baseline: dict, run_url: str = "") -> str:
    generated_at = iso_to_human(report.get("generatedAt", ""))
    slow_groups = compute_slow_query_groups(report.get("slowQueries", []))
    problems = loops_before_inserts(build_problems(groups))

    lines = ["", "---"]
    if baseline is not None:
        new_problems = loops_before_inserts(build_problems([g for g in groups if g.get("is_new")]))
        new_static = [f for f in static_findings if f.get("is_new")]
        emoji = "✅" if not new_problems and not new_static else "⚠️"
        status = ("No new query anti-patterns compared to develop" if emoji == "✅" else
                  f"{len(new_problems)} new repeated-query problem{'' if len(new_problems) == 1 else 's'} · {len(new_static)} new static finding{'' if len(new_static) == 1 else 's'} compared to develop")
        lines += [f"## {emoji} Query Report", "", f"**{status}**", "",
                  f"> Generated at {generated_at}, compared against the latest develop run ({iso_to_human(baseline.get('generatedAt', '?'))})."]
        if new_problems:
            lines += ["", f"### 🆕 New repeated queries ({len(new_problems)})", ""] + capped(new_problems, problem_rows_md, run_url, "new problems")
        if new_static:
            lines += ["", f"### 🆕 New static findings ({len(new_static)})", ""] + capped(new_static, static_rows_md, run_url, "new static findings")
    else:
        lines += ["## ℹ️ Query Report", "", "**No develop baseline available, so nothing is classified as new; the most relevant findings overall are listed instead.**", "",
                  f"> Generated at {generated_at}."]
        if problems:
            lines += ["", "### 🔁 Repeated queries", ""] + capped(problems, problem_rows_md, run_url, "problems")

    static_counts = {}
    for f in static_findings:
        static_counts[f["type"]] = static_counts.get(f["type"], 0) + 1
    lines += ["", "<details>", "<summary>All findings in this run</summary>", "", "| Finding | Count |", "|---|---|",
              f"| N+1 (a method queries once per item) | {sum(1 for p in problems if p['category'] == 'n_plus_one')} |",
              f"| Duplicate, one place (the same data loaded again by the same code) | {sum(1 for p in problems if p['category'] == 'duplicate_one_place')} |",
              f"| Duplicate, several places (the same data loaded again within a request) | {sum(1 for p in problems if p['category'] == 'duplicate_several_places')} |",
              f"| Row-by-row insert (one statement per inserted row) | {sum(1 for p in problems if p['category'] == 'insert')} |"]
    lines += [f"| Static: {STATIC_RULE_LABELS.get(t, t)} | {c} |" for t, c in sorted(static_counts.items())]
    lines += [f"| Slow queries over {report.get('thresholdMs', '?')} ms (context only, timing in CI is not compared) | {len(slow_groups)} |", "", "</details>", "",
              "<details>", "<summary>How to read this</summary>", "",
              "Every finding is a yes/no rule, without a count threshold. **N+1**: one query per item where one query for all items would do. "
              "**Duplicate**: the same data loaded again within the same request. **Row-by-row insert**: rows written one statement at a time. "
              "Findings are grouped by where the fix goes: the method that loops, or the endpoint for data loaded again by several places. "
              "**Avoidable / request** is the biggest number of executions one fix would save in a single request. "
              "🔍 means a static rule flags that same code. A finding is identified by its shape, endpoint, issuing code and query — not by the test or the "
              "repetition count, which depend on how much data a test creates.", "",
              "See the [performance guidelines](https://docs.artemis.tum.de/developer/guidelines/performance) for remediation patterns.", "", "</details>", "",
              "<!-- Slow Query Report -->"]
    return "\n".join(lines)


# ---------------------------------------------------------------------------
# HTML report (full findings; uploaded as the CI artifact)
# ---------------------------------------------------------------------------
# Self-contained on purpose: a multi-file report breaks silently if only the .html survives being
# unzipped and copied around, so all CSS and JS is inlined.

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
    --new: #2563eb;
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
        --new: #7aa2f7;
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
.muted { color: var(--muted); }
.ok { color: #16a34a; font-weight: 600; }
.lead { max-width: 820px; line-height: 1.5; margin: 8px 0 28px; }
.stats { display: flex; gap: 16px; margin: 0 0 32px; flex-wrap: wrap; }
.stat-tile { background: var(--tile-bg); border: 1px solid var(--border); border-radius: 8px; padding: 12px 20px; min-width: 160px; }
.stat-value { display: block; font-size: 28px; font-weight: 700; }
.stat-label { display: block; font-size: 13px; color: var(--muted); }
.stat-sub { display: block; font-size: 12px; color: var(--muted); }
.tabs { display: flex; gap: 4px; border-bottom: 1px solid var(--border); margin-bottom: 24px; flex-wrap: wrap; }
.tab-btn { appearance: none; background: none; border: none; border-bottom: 2px solid transparent; margin-bottom: -1px; padding: 10px 6px; font: inherit; font-size: 14px; font-weight: 600; color: var(--muted); cursor: pointer; display: flex; align-items: center; gap: 8px; }
.tab-btn:hover { color: var(--fg); }
.tab-btn.active { color: var(--fg); border-bottom-color: var(--sev-medium); }
.tab-count { background: var(--tile-bg); border: 1px solid var(--border); border-radius: 999px; padding: 1px 8px; font-size: 12px; font-weight: 600; }
.tab-panel[hidden] { display: none; }
.filter-box { width: 100%; max-width: 400px; padding: 8px 10px; margin: 8px 0 12px; border: 1px solid var(--border); border-radius: 6px; background: var(--bg); color: var(--fg); font-size: 14px; }
.table-scroll { overflow-x: auto; }
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
td.code, td.sql-cell code, td.sql-cell pre { font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; }
td.code { font-size: 12px; word-break: break-word; }
td.sql-cell pre { white-space: pre-wrap; word-break: break-word; margin: 6px 0 0; padding: 8px; background: var(--tile-bg); border-radius: 6px; }
td.sql-cell summary { cursor: pointer; }
.badge { display: inline-block; padding: 2px 8px; border-radius: 999px; font-size: 12px; font-weight: 600; white-space: nowrap; }
.badge-new { background: color-mix(in srgb, var(--new) 18%, transparent); color: var(--new); }
.badge-existing { background: rgba(107, 114, 128, .12); color: var(--muted); }
.phase-action { background: rgba(22, 163, 74, .15); color: #16a34a; }
.phase-setup { background: rgba(107, 114, 128, .15); color: var(--muted); }
.phase-background { background: rgba(107, 114, 128, .08); color: var(--muted); font-style: italic; }
table.nested-table { width: auto; min-width: 320px; margin: 6px 0 0; font-size: 12px; }
table.nested-table th, table.nested-table td { padding: 4px 8px; }
.static-badge { display: inline-block; margin-top: 4px; padding: 0; border: none; background: none; font: inherit; font-size: 12px; color: var(--sql-keyword); cursor: pointer; text-decoration: underline; text-underline-offset: 2px; }
.static-badge:hover, .static-badge:focus-visible { color: var(--fg); }
/* the transition sits on the base selector so removing the class fades back instead of snapping */
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
.problem-controls { position: sticky; top: 0; z-index: 2; background: var(--bg); display: flex; flex-wrap: wrap; gap: 8px; align-items: center; padding: 10px 0; margin-bottom: 8px; border-bottom: 1px solid var(--border); }
.problem-controls .filter-box { margin: 0; flex: 1 1 240px; }
.filter-chip { appearance: none; border: 1px solid var(--border); background: var(--tile-bg); color: var(--fg); border-radius: 999px; padding: 4px 11px; font: inherit; font-size: 12.5px; cursor: pointer; }
.filter-chip[aria-pressed="false"] { opacity: .45; text-decoration: line-through; }
.filter-chip.only-chip[aria-pressed="false"] { opacity: 1; text-decoration: none; }
.filter-chip.only-chip[aria-pressed="true"] { border-color: var(--new); color: var(--new); }
.section-note { max-width: 820px; line-height: 1.5; }
.problem-cards { display: grid; gap: 14px; margin-bottom: 32px; }
.problem-card { border: 1px solid var(--border); border-radius: 10px; padding: 14px 16px; }
.cat-n_plus_one { border-left: 4px solid var(--sev-high); }
.cat-duplicate_one_place, .cat-duplicate_several_places { border-left: 4px solid var(--sev-medium); }
.cat-insert { border-left: 4px solid var(--sev-low); }
.problem-head { display: flex; gap: 12px; align-items: flex-start; }
.problem-rank { color: var(--muted); font-weight: 600; padding-top: 2px; }
.problem-anchor { flex: 1; min-width: 0; }
.anchor-kind, .foot-label { display: block; font-size: 11px; text-transform: uppercase; letter-spacing: .04em; color: var(--muted); }
.problem-anchor code { font-size: 14px; font-weight: 600; word-break: break-word; }
.problem-impact { display: flex; gap: 8px; align-items: center; }
.impact-value { font-size: 26px; font-weight: 700; font-variant-numeric: tabular-nums; }
.impact-label { font-size: 11px; color: var(--muted); line-height: 1.2; }
.problem-tags { margin: 8px 0 10px; display: flex; flex-wrap: wrap; gap: 6px; align-items: center; }
.badge-n_plus_one { background: color-mix(in srgb, var(--sev-high) 15%, transparent); color: var(--sev-high); }
.badge-duplicate_one_place, .badge-duplicate_several_places { background: color-mix(in srgb, var(--sev-medium) 15%, transparent); color: var(--sev-medium); }
.badge-insert { background: color-mix(in srgb, var(--sev-low) 15%, transparent); color: var(--sev-low); }
.query-list { list-style: none; margin: 0; padding: 0; }
.query-item { display: grid; grid-template-columns: minmax(0, 1fr) auto auto; gap: 4px 12px; align-items: baseline; padding: 7px 0; border-top: 1px solid var(--border); font-size: 13px; }
.query-source { grid-column: 1 / -1; font-size: 12px; color: var(--muted); font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; word-break: break-word; }
.query-sql { min-width: 0; }
.query-sql summary { cursor: pointer; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.query-sql code, .query-sql pre { font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; font-size: 12px; }
.query-sql pre { white-space: pre-wrap; word-break: break-word; margin: 6px 0; padding: 8px; background: var(--tile-bg); border-radius: 6px; }
.query-counts { white-space: nowrap; }
.query-avoidable { font-size: 12px; color: var(--muted); white-space: nowrap; }
.problem-foot { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; margin-top: 10px; padding-top: 10px; border-top: 1px solid var(--border); font-size: 13px; }
.foot-list > code, .foot-list > span, .more-list > code, .more-list > span { display: block; margin-top: 3px; word-break: break-word; }
details.more-list summary { cursor: pointer; color: var(--new); font-size: 12.5px; margin-top: 3px; }
td.where-cell summary { cursor: pointer; }
td.where-cell .query-list, td.where-cell .foot-list { margin-top: 6px; }
.problem.filtered-out { display: none; }
@media (max-width: 640px) { .problem-foot { grid-template-columns: 1fr; } .query-item { grid-template-columns: minmax(0, 1fr) auto; } .query-avoidable { display: none; } }
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

// A filter hides non-matching rows; in a grouped table the group row decides for its detail row,
// and matches on the detail row's text too (test names and phases are only listed there).
document.querySelectorAll('.filter-box').forEach(function (input) {
    input.addEventListener('input', function () {
        var table = document.getElementById(input.dataset.target);
        if (!table) return;
        var query = input.value.toLowerCase();
        Array.prototype.forEach.call(table.tBodies[0].rows, function (row) {
            if (row.classList.contains('detail-row')) return;
            var detail = row.dataset.detailTarget ? document.getElementById(row.dataset.detailTarget) : null;
            var text = row.textContent + (detail ? ' ' + detail.textContent : '');
            var visible = text.toLowerCase().indexOf(query) !== -1;
            row.style.display = visible ? '' : 'none';
            if (detail && !visible) detail.hidden = true;
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

// A "static rule agrees" link jumps to the Static Findings tab and highlights the exact rows by id.
document.addEventListener('click', function (e) {
    var badge = e.target.closest('.static-badge');
    if (!badge) return;
    e.stopPropagation();
    var indices = (badge.dataset.findingIndices || '').split(',').filter(Boolean);
    if (!indices.length) return;
    activateTab('panel-static-findings');
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

// Grouped tables: a group row expands its detail row; sorting moves each detail row along with its
// group row (the generic sort above would split them, since a detail row has one colspan cell).
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

// Repeated queries: the search box and the chips filter cards and rows together. A chip that is
// pressed includes its problems; "only new" narrows to new ones instead.
(function () {
    var panel = document.getElementById('panel-repeated-queries');
    var search = panel && panel.querySelector('.problem-search');
    if (!search) return;
    var problems = Array.prototype.slice.call(panel.querySelectorAll('.problem'));
    var excluded = { category: {}, phase: {}, method: {} };
    var onlyNew = false;
    function apply() {
        var query = search.value.toLowerCase();
        var shown = 0;
        problems.forEach(function (el) {
            var phases = el.dataset.phases ? el.dataset.phases.split(' ') : [];
            var methods = el.dataset.methods.split(' ');
            var visible = !excluded.category[el.dataset.category]
                && (phases.length === 0 || phases.some(function (p) { return !excluded.phase[p]; }))
                && !(excluded.method.DELETE && methods.every(function (m) { return m === 'DELETE'; }))
                && (!onlyNew || el.dataset.new === 'true')
                && (!query || el.textContent.toLowerCase().indexOf(query) !== -1);
            el.classList.toggle('filtered-out', !visible);
            if (visible) shown++;
        });
        document.getElementById('problems-empty').hidden = shown > 0;
    }
    search.addEventListener('input', apply);
    panel.querySelectorAll('.filter-chip').forEach(function (chip) {
        chip.addEventListener('click', function () {
            var pressed = chip.getAttribute('aria-pressed') !== 'true';
            chip.setAttribute('aria-pressed', pressed ? 'true' : 'false');
            if (chip.dataset.filter === 'new') onlyNew = pressed;
            else excluded[chip.dataset.filter][chip.dataset.value] = !pressed;
            apply();
        });
    });
})();
"""

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
    """Single-pass regex SQL highlighting, styled via the --sql-* CSS tokens (no external library)."""
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
    """Short queries inline; long ones behind <details>, with the full text one click away."""
    if not sql:
        return ""
    if len(sql) <= 100:
        return f"<code>{highlight_sql(sql)}</code>"
    return f"<details><summary><code>{highlight_sql(trunc_plain(sql, 100))}</code></summary><pre>{highlight_sql(sql)}</pre></details>"


def html_phase(phase: str, has_endpoint: bool = True) -> str:
    """setup = test fixture traffic (page.request/context.request), action = what the browser page did."""
    if not has_endpoint:
        return '<span class="badge phase-background">background</span>'
    if phase == "action":
        return '<span class="badge phase-action">action</span>'
    if phase == "setup":
        return '<span class="badge phase-setup">setup</span>'
    return '<span class="muted">?</span>'


def html_endpoint(method: str, endpoint: str, thread_name: str = None) -> str:
    if not method and not endpoint:
        label = f"(background: {thread_name})" if thread_name else "(background)"
        return f'<span class="muted">{html.escape(label)}</span>'
    return html.escape(f"{method or '?'} {endpoint or '?'}")


def html_status(item: dict, has_baseline: bool) -> str:
    if not has_baseline:
        return '<td data-sort="0"><span class="muted">—</span></td>'
    if item.get("is_new"):
        return '<td data-sort="1"><span class="badge badge-new">new</span></td>'
    return '<td data-sort="0"><span class="badge badge-existing">existing</span></td>'


def capped_note(hidden: int, what: str) -> str:
    return f'<p class="muted" style="margin:6px 0 0;font-size:12px;">+ {hidden} more {what} not shown</p>' if hidden > 0 else ""


TOP_PROBLEMS = 20
FOOT_ITEMS_SHOWN = 3


def html_more_list(items: list, shown: int, render) -> str:
    """The first few items, the rest behind a '+ n more' toggle."""
    rest = items[shown:]
    more = f'<details class="more-list"><summary>+ {len(rest)} more</summary>{"".join(render(i) for i in rest)}</details>' if rest else ""
    return "".join(render(i) for i in items[:shown]) + more


def query_requests_html(q: dict) -> str:
    """The individual requests a query repeated in, the most avoidable executions first."""
    shown = q["requests"][:MAX_OCCURRENCES_SHOWN]
    rows = "".join(
        "<tr>"
        f'<td>{html.escape(r["endpoint"])}</td>'
        f'<td class="num" data-sort="{r["executions"]}">{r["executions"]}×</td>'
        f'<td class="num" data-sort="{r["distinct"]}">{r["distinct"]}</td>'
        f'<td data-sort="{1 if r.get("phase") == "action" else 0}">{html_phase(r.get("phase"))}</td>'
        f'<td>{html.escape(r["test"]) if r.get("test") else "<span class=muted>—</span>"}</td>'
        "</tr>" for r in shown)
    return ('<div class="table-scroll"><table class="nested-table sortable"><thead><tr><th data-type="string">Endpoint</th><th data-type="number">Executions</th>'
            '<th data-type="number">Distinct parameters</th><th data-type="number">Phase</th><th data-type="string">Test</th></tr></thead>'
            f'<tbody>{rows}</tbody></table></div>{capped_note(len(q["requests"]) - len(shown), "requests")}')


def query_item_html(q: dict, problem: dict) -> str:
    if problem["kind"] == "insert":
        source = source_label(q)
    elif problem["kind"] == "function":
        source = q["repositoryMethod"] or ""
    else:
        source = "loaded by " + ", ".join(sorted(q["call_sites"])) if q["call_sites"] else ""
    source_html = f'<div class="query-source">{html.escape(source)}</div>' if source else ""
    return (f'<li class="query-item">{source_html}'
            f'<details class="query-sql"><summary><code>{html.escape(q["summary"])}</code></summary><pre>{highlight_sql(q["sql"])}</pre>{query_requests_html(q)}</details>'
            f'<span class="query-counts">{html.escape(query_counts_label(q))}</span>'
            f'<span class="query-avoidable" title="executions one request could have saved">−{q["avoidable"]}</span></li>')


def problem_tags_html(p: dict, static_findings: list, has_baseline: bool) -> str:
    tags = [f'<span class="badge badge-{p["category"]}">{CATEGORY_LABELS[p["category"]]}</span>']
    if has_baseline and p["is_new"]:
        tags.append('<span class="badge badge-new">new</span>')
    tags += [html_phase(phase) for phase in p["phases"]]
    if p["static_matches"]:
        labels = "; ".join(STATIC_RULE_LABELS.get(static_findings[i]["type"], static_findings[i]["type"]) for i in p["static_matches"])
        indices = ",".join(str(i) for i in p["static_matches"])
        tags.append(f'<button type="button" class="static-badge" title="{html.escape(labels)}" data-finding-indices="{indices}">🔍 static rule agrees</button>')
    return "".join(tags)


def problem_attributes(p: dict) -> str:
    return (f'data-category="{p["category"]}" data-phases="{" ".join(p["phases"])}" data-methods="{html.escape(" ".join(p["methods"]))}" '
            f'data-new="{"true" if p["is_new"] else "false"}"')


def problem_card_html(p: dict, rank: int, static_findings: list, has_baseline: bool) -> str:
    anchor_kind = {"function": "Method", "endpoint": "Endpoint", "insert": "Table"}[p["kind"]]
    tests = f'{len(p["tests"])} test{"" if len(p["tests"]) == 1 else "s"}'
    endpoints = f'Seen at {len(p["endpoints"])} endpoint{"" if len(p["endpoints"]) == 1 else "s"}'
    return (f'<article class="problem problem-card cat-{p["category"]}" {problem_attributes(p)}>'
            f'<div class="problem-head"><span class="problem-rank">#{rank}</span>'
            f'<div class="problem-anchor"><span class="anchor-kind">{anchor_kind}</span><code>{html.escape(p["anchor"])}</code></div>'
            f'<div class="problem-impact"><span class="impact-value">{p["avoidable"]}</span><span class="impact-label">avoidable executions<br>per request</span></div></div>'
            f'<div class="problem-tags">{problem_tags_html(p, static_findings, has_baseline)}</div>'
            f'<ul class="query-list">{html_more_list(p["queries"], 4, lambda q: query_item_html(q, p))}</ul>'
            f'<div class="problem-foot"><div><span class="foot-label">{endpoints}</span><div class="foot-list">'
            f'{html_more_list(p["endpoints"], FOOT_ITEMS_SHOWN, lambda e: f"<code>{html.escape(e)}</code>")}</div></div>'
            f'<div><span class="foot-label">{tests}</span><div class="foot-list">'
            f'{html_more_list(p["tests"], FOOT_ITEMS_SHOWN - 1, lambda t: f"<span>{html.escape(t)}</span>")}</div></div></div>'
            '</article>')


def problem_row_html(p: dict, rank: int, static_findings: list, has_baseline: bool) -> str:
    count = len(p["queries"])
    if p["kind"] == "insert":
        what = f'{count} caller{"" if count == 1 else "s"}'
    else:
        what = f'{count} quer{"y" if count == 1 else "ies"}'
    return (f'<tr class="problem" {problem_attributes(p)}>'
            f'<td class="num muted">#{rank}</td>'
            f'<td class="num"><strong>{p["avoidable"]}</strong></td>'
            f'<td>{problem_tags_html(p, static_findings, has_baseline)}</td>'
            f'<td class="where-cell"><details><summary><code>{html.escape(p["anchor"])}</code> '
            f'<span class="muted">· {what} · {len(p["endpoints"])} endpoint{"" if len(p["endpoints"]) == 1 else "s"}</span></summary>'
            f'<ul class="query-list">{"".join(query_item_html(q, p) for q in p["queries"])}</ul>'
            f'<div class="foot-list">{"".join(f"<code>{html.escape(e)}</code>" for e in p["endpoints"])}</div></details></td>'
            f'<td class="num">{len(p["tests"])}</td></tr>')


def problem_table_html(problems: list, first_rank: int, static_findings: list, has_baseline: bool) -> str:
    rows = "".join(problem_row_html(p, first_rank + i, static_findings, has_baseline) for i, p in enumerate(problems))
    return ('<div class="table-scroll"><table class="problem-table"><thead><tr><th>#</th><th title="executions one request could have saved">Avoidable</th>'
            f'<th>Shape</th><th>Where to fix</th><th>Tests</th></tr></thead><tbody>{rows}</tbody></table></div>')


def build_problems_html(problems: list, static_findings: list, has_baseline: bool) -> str:
    if not problems:
        return '<p class="ok">✅ No query ran more than once within a request.</p>'
    ordered = loops_before_inserts(problems)
    loops = [p for p in ordered if p["category"] != "insert"]
    inserts = ordered[len(loops):]
    top, rest = loops[:TOP_PROBLEMS], loops[TOP_PROBLEMS:]
    chips = "".join(f'<button type="button" class="filter-chip" data-filter="category" data-value="{c}" aria-pressed="true">{label}</button>'
                    for c, label in CATEGORY_LABELS.items())
    chips += ('<button type="button" class="filter-chip" data-filter="phase" data-value="action" aria-pressed="true">action</button>'
              '<button type="button" class="filter-chip" data-filter="phase" data-value="setup" aria-pressed="true">setup</button>'
              '<button type="button" class="filter-chip" data-filter="method" data-value="DELETE" aria-pressed="true" title="problems seen only at DELETE endpoints">DELETE</button>')
    if has_baseline:
        chips += '<button type="button" class="filter-chip only-chip" data-filter="new" aria-pressed="false">only new</button>'
    parts = [f'<div class="problem-controls"><input class="filter-box problem-search" type="search" placeholder="Filter by method, endpoint, test, table, SQL…">{chips}</div>']
    if top:
        parts.append(f'<h2>Top {len(top)} problems</h2><p class="muted section-note">Ranked by the biggest repeat: the executions one fix would save in a single request.</p>'
                     f'<div class="problem-cards">{"".join(problem_card_html(p, i + 1, static_findings, has_baseline) for i, p in enumerate(top))}</div>')
    if rest:
        parts.append(f'<h2>All other problems <span class="muted">({len(rest)})</span></h2>{problem_table_html(rest, len(top) + 1, static_findings, has_baseline)}')
    if inserts:
        parts.append(f'<h2>Row-by-row inserts <span class="muted">({len(inserts)})</span></h2>'
                     '<p class="muted section-note">Rows inserted one statement at a time: either <code>save()</code> in a loop, or one <code>save()</code>/<code>saveAll()</code> '
                     'that Hibernate cannot batch because the database generates the ids. The SQL does not tell the two apart, so these are listed apart from the loops.</p>'
                     f'{problem_table_html(inserts, 1, static_findings, has_baseline)}')
    parts.append('<p id="problems-empty" class="muted" hidden>No problem matches the filters.</p>')
    return "\n".join(parts)


def slow_query_occurrences_html(occurrences: list) -> str:
    ranked = sorted(occurrences, key=lambda o: o["ms"], reverse=True)
    shown = ranked[:MAX_OCCURRENCES_SHOWN]
    rows = "".join(
        "<tr>"
        f'<td class="num" data-sort="{o["ms"]}">{o["ms"]} ms</td>'
        f'<td data-sort="{1 if o.get("phase") == "action" else 0}">{html_phase(o.get("phase"), o.get("has_endpoint", True))}</td>'
        f'<td>{html.escape(o["test"]) if o.get("test") else "<span class=muted>—</span>"}</td>'
        "</tr>" for o in shown)
    return ('<table class="nested-table sortable"><thead><tr><th data-type="number">Duration</th><th data-type="number">Phase</th><th data-type="string">Test</th></tr></thead>'
            f'<tbody>{rows}</tbody></table>{capped_note(len(ranked) - len(shown), "occurrences")}')


def build_slow_queries_table_html(slow_queries: list, threshold_ms) -> str:
    if not slow_queries:
        return f'<p class="ok">✅ No query slower than {threshold_ms} ms.</p>'
    rows = []
    for idx, ((method, endpoint, sql, thread_name), g) in enumerate(compute_slow_query_groups(slow_queries)):
        detail_id = f"slow-query-detail-{idx}"
        rows.append(
            '<tr class="group-row" data-detail-target="' + detail_id + '">'
            '<td class="expand-cell"><span class="expand-arrow">&#9654;</span></td>'
            f"<td>{html_endpoint(method, endpoint, thread_name)}</td>"
            f'<td class="sql-cell">{html_sql_cell(sql)}</td>'
            f'<td class="num" data-sort="{g["count"]}">{g["count"]}×</td>'
            f'<td class="num" data-sort="{g["join_count"]}">{g["join_count"]}</td>'
            f'<td class="num" data-sort="{g["worst_ms"]}">{g["worst_ms"]} ms</td>'
            "</tr>"
            f'<tr class="detail-row" id="{detail_id}" hidden><td></td><td colspan="5">{slow_query_occurrences_html(g["occurrences"])}</td></tr>'
        )
    return (
        '<input class="filter-box" type="search" placeholder="Filter by endpoint, test, phase, or SQL…" data-target="slow-queries-table">\n'
        '<div class="table-scroll"><table class="grouped-table" id="slow-queries-table">\n<thead><tr><th></th>'
        '<th data-type="string" data-col="endpoint">Endpoint</th><th>Query</th>'
        '<th data-type="number" data-col="occurrences">Occurrences</th>'
        '<th data-type="number" data-col="joins" title="Tables joined in this one query">Joins</th>'
        '<th data-type="number" data-col="duration">Worst duration</th>'
        f"</tr></thead>\n<tbody>{''.join(rows)}</tbody>\n</table></div>"
    )


def build_static_findings_table_html(static_findings: list, has_baseline: bool) -> str:
    if not static_findings:
        return '<p class="ok">No static findings.</p>'
    rows = []
    for i, f in enumerate(static_findings):
        # the id is what a "static rule agrees" link points at (see link_static_confirmations)
        location = (f.get("file") or "").replace("\\", "/").split("/src/main/java/")[-1]
        if f.get("line"):
            location += f":{f['line']}"
        rows.append(
            f'<tr id="static-finding-{i}">'
            f"{html_status(f, has_baseline)}"
            f"<td>{html.escape(STATIC_RULE_LABELS.get(f['type'], f['type']))}</td>"
            f'<td class="code">{html.escape(f.get("member") or f.get("entityClass") or "—")}</td>'
            f"<td>{html.escape(f.get('detail') or '')}</td>"
            f'<td class="sql-cell">{html_sql_cell(f.get("snippet") or "")}</td>'
            f'<td class="code">{html.escape(location)}</td>'
            "</tr>"
        )
    return (
        '<input class="filter-box" type="search" placeholder="Filter by rule, class, method, or file…" data-target="static-findings-table">\n'
        '<div class="table-scroll"><table class="sortable" id="static-findings-table">\n<thead><tr>'
        '<th data-type="number">Status</th><th data-type="string">Rule</th><th data-type="string">Where</th><th data-type="string">Detail</th>'
        '<th data-type="string">Code</th><th data-type="string">File</th>'
        f"</tr></thead>\n<tbody>{''.join(rows)}</tbody>\n</table></div>"
    )


def build_html_report(report: dict, groups: list, static_findings: list, baseline: dict, run_url: str = "") -> str:
    has_baseline = baseline is not None
    threshold_ms = report.get("thresholdMs", "?")
    slow_queries = report.get("slowQueries", [])
    slow_count = len(compute_slow_query_groups(slow_queries))
    problems = build_problems(groups)
    n_plus_one = sum(1 for p in problems if p["category"] == "n_plus_one")
    duplicates = sum(1 for p in problems if p["category"] in ("duplicate_one_place", "duplicate_several_places"))
    inserts = sum(1 for p in problems if p["category"] == "insert")
    confirmed = sum(1 for p in problems if p["static_matches"])
    run_link = f'<p><a href="{html.escape(run_url)}">View CI run</a></p>' if run_url else ""
    if has_baseline:
        new_count = sum(1 for p in problems if p["is_new"]) + sum(1 for f in static_findings if f.get("is_new"))
        comparison = (f"Compared against the latest develop run ({html.escape(iso_to_human(baseline.get('generatedAt', '?')))}): "
                      f"<strong>{new_count} finding{'' if new_count == 1 else 's'} are new</strong>. Use <em>only new</em> above the repeated queries, "
                      "or type <code>new</code> into a filter box, to show only those.")
    else:
        comparison = "No develop baseline was available, so findings are not classified as new or existing."

    return f"""<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Query Report</title>
<style>{HTML_REPORT_CSS}</style>
</head>
<body>
<div class="wrapper">
<h1>Query Report</h1>
<p class="muted">Generated at {html.escape(iso_to_human(report.get("generatedAt", "")))}</p>
{run_link}
<p class="lead">Every finding is a yes/no rule without a count threshold. <strong>Repeated queries</strong> were observed while the E2E tests ran: a query that ran
more than once in one request, either with different parameters (<strong>N+1</strong>) or with the same ones (<strong>duplicate</strong>), or rows inserted one
statement at a time (<strong>row-by-row insert</strong>). They are grouped by where the fix goes: the method that loops, the endpoint for data that several places
load again, or the table for inserts. <strong>Static findings</strong> come from scanning the source code. A problem marked 🔍 is flagged by a static rule
for the very same code. <strong>Slow queries</strong> are shown for context only: query timing in CI is too noisy to judge a change by. {comparison}</p>
<div class="stats">
<div class="stat-tile"><span class="stat-value">{n_plus_one}</span><span class="stat-label">N+1</span><span class="stat-sub">a method queries once per item</span></div>
<div class="stat-tile"><span class="stat-value">{duplicates}</span><span class="stat-label">Duplicate</span><span class="stat-sub">the same data loaded again</span></div>
<div class="stat-tile"><span class="stat-value">{inserts}</span><span class="stat-label">Row-by-row insert</span><span class="stat-sub">one statement per row</span></div>
<div class="stat-tile"><span class="stat-value">{confirmed}</span><span class="stat-label">Confirmed statically</span><span class="stat-sub">a static rule flags the same code</span></div>
<div class="stat-tile"><span class="stat-value">{len(static_findings)}</span><span class="stat-label">Static findings</span><span class="stat-sub">from the source scan</span></div>
</div>

<div class="tabs" role="tablist">
<button class="tab-btn active" type="button" role="tab" aria-selected="true" aria-controls="panel-repeated-queries">🔁 Repeated Queries <span class="tab-count">{len(problems)}</span></button>
<button class="tab-btn" type="button" role="tab" aria-selected="false" aria-controls="panel-static-findings">🔍 Static Findings <span class="tab-count">{len(static_findings)}</span></button>
<button class="tab-btn" type="button" role="tab" aria-selected="false" aria-controls="panel-slow-queries">🐢 Slow Queries <span class="tab-count">{slow_count}</span></button>
</div>

<section class="tab-panel" id="panel-repeated-queries" role="tabpanel">
{build_problems_html(problems, static_findings, has_baseline)}
</section>
<section class="tab-panel" id="panel-static-findings" role="tabpanel" hidden>
{build_static_findings_table_html(static_findings, has_baseline)}
</section>
<section class="tab-panel" id="panel-slow-queries" role="tabpanel" hidden>
<p class="muted">Queries slower than {html.escape(str(threshold_ms))} ms. Context only, not compared against develop.</p>
{build_slow_queries_table_html(slow_queries, threshold_ms)}
</section>
</div>
<script>{HTML_REPORT_JS}</script>
</body>
</html>
"""


# ---------------------------------------------------------------------------
# Entry point
# ---------------------------------------------------------------------------

def main():
    parser = argparse.ArgumentParser(description="Format the E2E query report")
    parser.add_argument("report")
    parser.add_argument("run_url", nargs="?", default="")
    parser.add_argument("html_output_path", nargs="?", default="")
    parser.add_argument("static_findings", nargs="?", default="")
    parser.add_argument("--baseline", help="keys of a baseline run (written by --keys-out of that run)")
    parser.add_argument("--keys-out", help="write this run's finding keys here, for use as a later baseline")
    args = parser.parse_args()

    try:
        report = load_json(args.report)
    except (json.JSONDecodeError, FileNotFoundError) as exc:
        print(f"## ⚠️ Query Report Parse Error\n\n{exc}", file=sys.stderr)
        sys.exit(1)

    static_findings = []
    if args.static_findings:
        try:
            static_findings = load_json(args.static_findings)
        except (json.JSONDecodeError, FileNotFoundError) as exc:
            print(f"Warning: could not load static findings from {args.static_findings}: {exc}", file=sys.stderr)

    baseline = None
    if args.baseline:
        try:
            baseline = load_json(args.baseline)
        except (json.JSONDecodeError, FileNotFoundError) as exc:
            print(f"Warning: could not load baseline from {args.baseline}: {exc}", file=sys.stderr)

    groups = group_repeated_queries(report.get("repeatedQueries", []))
    link_static_confirmations(groups, static_findings)
    mark_new(groups, static_findings, baseline)

    print(build_report(report, groups, static_findings, baseline, args.run_url))
    if args.html_output_path:
        with open(args.html_output_path, "w", encoding="utf-8") as f:
            f.write(build_html_report(report, groups, static_findings, baseline, args.run_url))
    if args.keys_out:
        keys = collect_keys(groups, static_findings)
        keys["generatedAt"] = report.get("generatedAt")
        with open(args.keys_out, "w", encoding="utf-8") as f:
            json.dump(keys, f, indent=1)


if __name__ == "__main__":
    main()
