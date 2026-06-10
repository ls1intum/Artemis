#!/usr/bin/env bash
# Render the changed-file coverage table from the already-downloaded coverage artifacts and expose it
# as the `coverage_table` + `success` step outputs. Shared by ci.yml's in-run `coverage-report` job
# and pullrequest-coverage-reporter.yml (the fork path) so the invocation can't drift.
# Env: CHANGED_FILES, CLIENT_MODULES, SERVER_MODULES, HAS_CLIENT, HAS_SERVER.
set -uo pipefail

# --changed-files (from the API) replaces git-diff detection, so the script never needs a PR-tree
# diff; --skip-tests makes it read only the downloaded coverage data.
ARGS=(--skip-tests --print --changed-files "$CHANGED_FILES")
if [ "$HAS_CLIENT" = "true" ] && [ "$HAS_SERVER" != "true" ]; then
    ARGS+=(--client-only)
elif [ "$HAS_SERVER" = "true" ] && [ "$HAS_CLIENT" != "true" ]; then
    ARGS+=(--server-only)
fi
if [ -n "$CLIENT_MODULES" ]; then ARGS+=(--client-modules "$CLIENT_MODULES"); fi
if [ -n "$SERVER_MODULES" ]; then ARGS+=(--server-modules "$SERVER_MODULES"); fi

if ! COVERAGE_OUTPUT=$(node supporting_scripts/code-coverage/local-pr-coverage/local-pr-coverage.mjs "${ARGS[@]}" 2>&1); then
    echo "$COVERAGE_OUTPUT"
    echo "success=false" >> "$GITHUB_OUTPUT"
    exit 0
fi
echo "$COVERAGE_OUTPUT"
echo "success=true" >> "$GITHUB_OUTPUT"

# Keep only the rows between the script's ─ borders (the `1d;$d` drops the two border lines).
COVERAGE_TABLE=$(echo "$COVERAGE_OUTPUT" | sed -n '/^─/,/^─/p' | sed '1d;$d')
{
    echo 'coverage_table<<COVERAGE_EOF'
    echo "$COVERAGE_TABLE"
    echo 'COVERAGE_EOF'
} >> "$GITHUB_OUTPUT"
