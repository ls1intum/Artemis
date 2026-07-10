#!/usr/bin/env bash
# analyze_slow_queries_runtime.sh
#
# Fetches the runtime slow-query report from the running Artemis server after
# Playwright E2E tests complete, then delegates to format_slow_query_report.py
# to produce a Markdown summary for PR commenting.
#
# Usage (called by the CI step in test-e2e.yml):
#   ARTEMIS_ADMIN_USERNAME=admin \
#   ARTEMIS_ADMIN_PASSWORD=<secret> \
#   ARTEMIS_BASE_URL=http://localhost:8080 \
#   bash supporting_scripts/analyze_slow_queries_runtime.sh
#
# Outputs:
#   slow-query-report.json   – raw JSON from the server endpoint
#   slow-query-summary.md    – Markdown summary for the PR comment
#
# Exit codes:
#   0  – success (even if slow queries were found; CI step is informational)
#   1  – could not reach the server (non-fatal: summary contains a warning)

set -euo pipefail

ADMIN_USER="${ARTEMIS_ADMIN_USERNAME:-admin}"
ADMIN_PASS="${ARTEMIS_ADMIN_PASSWORD:-}"
BASE_URL="${ARTEMIS_BASE_URL:-http://localhost:8080}"
ENDPOINT="${BASE_URL}/api/core/admin/performance/slow-queries"

REPORT_JSON="slow-query-report.json"
REPORT_MD="slow-query-summary.md"

echo "=== Slow-Query Report Collection ==="
echo "Endpoint: ${ENDPOINT}"

# ------------------------------------------------------------------
# Fetch the report — give Artemis a few seconds after tests finish
# ------------------------------------------------------------------
HTTP_STATUS=$(curl --silent --output "${REPORT_JSON}" \
    --write-out "%{http_code}" \
    --max-time 15 \
    --user "${ADMIN_USER}:${ADMIN_PASS}" \
    "${ENDPOINT}" || echo "000")

if [ "${HTTP_STATUS}" = "000" ] || [ ! -s "${REPORT_JSON}" ]; then
    echo "⚠️  Could not reach the slow-query endpoint (HTTP ${HTTP_STATUS})."
    echo "⚠️  Server may have already stopped or the e2e-performance profile was not active."
    echo "## ⚠️ Slow Query Report Unavailable" > "${REPORT_MD}"
    echo "" >> "${REPORT_MD}"
    echo "Could not retrieve the slow-query report (HTTP ${HTTP_STATUS})." >> "${REPORT_MD}"
    echo "Make sure the \`e2e-performance\` Spring profile is active." >> "${REPORT_MD}"
    exit 0
fi

if [ "${HTTP_STATUS}" != "200" ]; then
    echo "⚠️  Server returned HTTP ${HTTP_STATUS}. Report may be incomplete."
    echo "## ⚠️ Slow Query Report Error (HTTP ${HTTP_STATUS})" > "${REPORT_MD}"
    exit 0
fi

echo "✅ Report fetched (HTTP 200). Analyzing..."

# ------------------------------------------------------------------
# Delegate formatting to the Python script
# ------------------------------------------------------------------
python3 supporting_scripts/format_slow_query_report.py \
    "${REPORT_JSON}" > "${REPORT_MD}"

echo ""
echo "=== Summary preview ==="
head -30 "${REPORT_MD}"
echo "=== End of preview ==="
