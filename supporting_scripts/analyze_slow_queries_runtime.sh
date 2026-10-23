#!/usr/bin/env bash
# analyze_slow_queries_runtime.sh
#
# Reads the runtime slow-query report written by Playwright's global teardown
# (src/test/playwright/init/global-teardown.ts) and delegates to
# format_slow_query_report.py to produce a Markdown summary for PR commenting.
#
# The report is NOT fetched from a live server here: by the time this CI step runs, the
# Artemis server container has already been torn down (docker compose stops the whole stack
# the moment the Playwright container exits). The teardown hook fetches the report itself,
# while the server is still up, and writes it to a location that survives container teardown
# because the whole repo is bind-mounted into the Playwright container.
#
# Usage (called by the CI steps in ci-e2e.yml):
#   bash supporting_scripts/analyze_slow_queries_runtime.sh
#
# Outputs:
#   slow-query-report.html   – full, sortable, self-contained HTML report (uploaded as the CI artifact)
#   slow-query-summary.md    – Markdown summary (top 20 per section) for the PR comment
#
# Also runs find_slow_queries.py --json here (a second, independent invocation from the one
# ci-quality.yml's separate "query-quality" job already runs for its own gate) to produce the
# static findings this step's HTML report cross-references. It's a pure source scan with no
# database/app dependency, so re-running it costs only the ~1-2s it takes to scan the codebase --
# far cheaper than plumbing an artifact across CI jobs to share one run's output.
#
# Exit code is always 0 (informational step; a missing/failed report is reported via Markdown,
# never fails the CI job).

set -euo pipefail

REPORT_SOURCE="src/test/playwright/test-reports/slow-query-report.json"
REPORT_HTML="slow-query-report.html"
REPORT_MD="slow-query-summary.md"
STATIC_FINDINGS="static-findings.json"

echo "=== Slow-Query Report Collection ==="
echo "Source: ${REPORT_SOURCE}"

if [ ! -s "${REPORT_SOURCE}" ]; then
    echo "⚠️  No slow-query report found at ${REPORT_SOURCE}."
    echo "⚠️  Either the e2e-performance Spring profile was not active, or the global-teardown hook"
    echo "⚠️  could not reach the admin endpoint before the server stopped."
    echo "## ⚠️ Slow Query Report Unavailable" > "${REPORT_MD}"
    echo "" >> "${REPORT_MD}"
    echo "Could not find a slow-query report. Make sure the \`e2e-performance\` Spring profile is active." >> "${REPORT_MD}"
    exit 0
fi

echo "✅ Report found. Analyzing..."

# Link the "more not shown" note straight to this run's Artifacts section, so readers don't
# have to guess where the full report lives. Only set when running under GitHub Actions.
RUN_URL=""
if [ -n "${GITHUB_RUN_ID:-}" ] && [ -n "${GITHUB_REPOSITORY:-}" ]; then
    RUN_URL="${GITHUB_SERVER_URL:-https://github.com}/${GITHUB_REPOSITORY}/actions/runs/${GITHUB_RUN_ID}"
fi

# Static source scan -- informational only, never fails this step even if it errors (that's
# ci-quality.yml's job; this one just wants the findings for cross-referencing, if available).
python3 supporting_scripts/find_slow_queries.py --json "${STATIC_FINDINGS}" > /dev/null 2>&1 || echo "⚠️  Static scan failed; report will render without static-finding correlation."

# ------------------------------------------------------------------
# Delegate formatting to the Python script: Markdown summary to stdout (for the PR comment),
# full untruncated HTML report to a file (for the CI artifact).
# ------------------------------------------------------------------
python3 supporting_scripts/format_slow_query_report.py \
    "${REPORT_SOURCE}" "${RUN_URL}" "${REPORT_HTML}" "${STATIC_FINDINGS}" > "${REPORT_MD}"

echo ""
echo "=== Summary preview ==="
head -30 "${REPORT_MD}"
echo "=== End of preview ==="
