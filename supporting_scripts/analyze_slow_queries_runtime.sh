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
#   slow-query-report.json   – raw JSON copied from the teardown hook's output
#   slow-query-summary.md    – Markdown summary for the PR comment
#
# Exit code is always 0 (informational step; a missing/failed report is reported via Markdown,
# never fails the CI job).

set -euo pipefail

REPORT_SOURCE="src/test/playwright/test-reports/slow-query-report.json"
REPORT_JSON="slow-query-report.json"
REPORT_MD="slow-query-summary.md"

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

cp "${REPORT_SOURCE}" "${REPORT_JSON}"
echo "✅ Report found. Analyzing..."

# ------------------------------------------------------------------
# Delegate formatting to the Python script
# ------------------------------------------------------------------
python3 supporting_scripts/format_slow_query_report.py \
    "${REPORT_JSON}" > "${REPORT_MD}"

echo ""
echo "=== Summary preview ==="
head -30 "${REPORT_MD}"
echo "=== End of preview ==="
