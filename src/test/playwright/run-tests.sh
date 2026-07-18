#!/bin/bash
# Script to run Playwright tests with optional test path filtering
# Usage: ./run-tests.sh [test-paths...]
# If no test paths are provided, runs all tests in e2e/

TEST_PATHS=("$@")
FAILED=0
REPORTER_FAILED=0

# Clean up stale markers, logs, and reports from previous runs (self-hosted runners have persistent workspaces)
mkdir -p ./test-reports
rm -f ./test-reports/.reporter-failed ./test-reports/pw-output-*.log ./test-reports/e2e-counts.env ./test-reports/results*.xml

if [ ${#TEST_PATHS[@]} -eq 0 ] && [ -n "$PLAYWRIGHT_TEST_PATHS" ]; then
    read -r -a TEST_PATHS <<< "$PLAYWRIGHT_TEST_PATHS"
fi

# Check JUnit XML to determine if actual test failures occurred.
# Returns 0 if tests passed (including when tests ran with no failures).
# Returns 1 if tests failed.
# Returns 2 if no tests were found (XML missing or has no testcases).
check_test_results() {
    local xml_file="$1"

    if [ ! -f "$xml_file" ]; then
        return 2
    fi

    if ! grep -q '<testcase' "$xml_file"; then
        return 2
    fi

    if grep -qE 'failures="[1-9]|errors="[1-9]' "$xml_file"; then
        return 1
    fi

    return 0
}

# Run a Playwright command and preserve its exit status. JUnit inspection only
# adds diagnostics; it must never turn a crashed or incomplete run green.
run_playwright() {
    local test_type="$1"
    local junit_file="./test-reports/results-${test_type}.xml"
    shift

    # A crash before the JUnit reporter starts must not be diagnosed from a
    # clean report left behind by an earlier invocation on a persistent runner.
    rm -f "$junit_file"

    # Raise the runner heap headroom: the single "run all" CI job executes the whole ~316-test suite in
    # one Playwright process, and its per-run accumulation (results, coverage, attachments) hit the old
    # 6144 MB cap mid-run and OOM'd a worker ("Reached heap limit — JavaScript heap out of memory"),
    # which spuriously failed the tests it was running. We APPEND the flag rather than rely on a default:
    # the root .npmrc sets `node-options=--max-old-space-size=6144`, which pnpm injects into NODE_OPTIONS,
    # so a `${NODE_OPTIONS:-…}` fallback would never fire. Node applies the LAST --max-old-space-size, so
    # appending 8192 raises the cap for this invocation only, without touching the global .npmrc value.
    # Tee the output to a per-type log (in addition to the console) so we can later aggregate
    # Playwright's own pass/flaky/fail/skip summary — the flaky count is not expressible in JUnit.
    # PIPESTATUS[0] preserves Playwright's real exit code across the pipe.
    NODE_OPTIONS="${NODE_OPTIONS:-} --max-old-space-size=8192" PLAYWRIGHT_TEST_TYPE="$test_type" pnpm exec playwright test "$@" 2>&1 | tee "./test-reports/pw-output-${test_type}.log"
    local exit_code=${PIPESTATUS[0]}

    if [ "$exit_code" -ne 0 ]; then
        check_test_results "$junit_file"
        local check_result=$?
        if [ $check_result -eq 0 ]; then
            echo "WARNING: Playwright exited with code $exit_code but JUnit XML shows no test failures."
            echo "This may indicate a reporter or worker failure; the run remains failed."
            REPORTER_FAILED=1
        elif [ $check_result -eq 2 ]; then
            echo "ERROR: Playwright exited with code $exit_code and produced no complete JUnit test results for '$test_type'."
        fi
        FAILED=1
    fi
}

echo "=== Running Playwright Tests ==="

# Hyperion runs in its own Playwright process. Reuse the existing sequential report channel so its
# Monocart coverage remains part of merge-coverage-reports.mjs without adding another merge path.
if [ ${#TEST_PATHS[@]} -gt 0 ]; then
    echo "Running filtered tests: ${TEST_PATHS[*]}"

    # Filtered paths may belong to only one process, so empty selections are expected.
    echo "--- Running parallel tests ---"
    run_playwright parallel --project=fast-tests --project=slow-tests --pass-with-no-tests "${TEST_PATHS[@]}"
    echo "--- Running Hyperion tests ---"
    run_playwright sequential --project=hyperion-tests --pass-with-no-tests "${TEST_PATHS[@]}"
else
    echo "Running all tests"

    echo "--- Running parallel tests ---"
    run_playwright parallel e2e --project=fast-tests --project=slow-tests
    echo "--- Running Hyperion tests ---"
    run_playwright sequential e2e --project=hyperion-tests
fi

# Run the @multi-node project only when the surrounding stack opts in via env var. The multi-node
# docker compose sets EXPECTED_CLUSTER_NODE_COUNT, the single-node compose does not. This keeps the
# cluster smoke-test out of every other Playwright run while still running it automatically when
# the multi-node stack is up.
if [ -n "$EXPECTED_CLUSTER_NODE_COUNT" ]; then
    echo "--- Running multi-node tests (cluster size $EXPECTED_CLUSTER_NODE_COUNT) ---"
    if [ ${#TEST_PATHS[@]} -gt 0 ]; then
        run_playwright multinode --project=multi-node-tests "${TEST_PATHS[@]}"
    else
        run_playwright multinode e2e --project=multi-node-tests
    fi
fi

# Aggregate Playwright's own pass/flaky/fail/skip counts across every project invocation above and
# write them to a small env file the CI workflow folds into the E2E commit-status description
# ("309 passed, 3 flaky, 0 failed"). Playwright's JUnit report cannot express "flaky" (a retried test
# that ultimately passes is just a passing <testcase>), so Playwright's own end-of-run summary is the
# authoritative source. Each summary count line looks like "  309 passed (34.0m)" / "  3 flaky".
echo "--- Aggregating test counts ---"
E2E_PASSED=0
E2E_FLAKY=0
E2E_FAILED=0
E2E_SKIPPED=0
for pw_log in ./test-reports/pw-output-*.log; do
    [ -f "$pw_log" ] || continue
    while read -r count kind; do
        case "$kind" in
            passed) E2E_PASSED=$((E2E_PASSED + count)) ;;
            flaky) E2E_FLAKY=$((E2E_FLAKY + count)) ;;
            failed) E2E_FAILED=$((E2E_FAILED + count)) ;;
            skipped) E2E_SKIPPED=$((E2E_SKIPPED + count)) ;;
        esac
    done < <(sed -E 's/\x1b\[[0-9;]*m//g' "$pw_log" | tr -d '\r' | grep -oE '^[[:space:]]+[0-9]+ (passed|flaky|failed|skipped)' | grep -oE '[0-9]+ (passed|flaky|failed|skipped)')
done
{
    echo "E2E_PASSED=${E2E_PASSED}"
    echo "E2E_FLAKY=${E2E_FLAKY}"
    echo "E2E_FAILED=${E2E_FAILED}"
    echo "E2E_SKIPPED=${E2E_SKIPPED}"
} > ./test-reports/e2e-counts.env
echo "E2E counts: ${E2E_PASSED} passed, ${E2E_FLAKY} flaky, ${E2E_FAILED} failed, ${E2E_SKIPPED} skipped"

# Remove any stale results.xml (e.g. from playwright:setup init test) before
# moving the real report into place, so CI never consumes an outdated report.
echo "--- Finalizing test reports ---"
rm -f ./test-reports/results.xml
JUNIT_REPORTS=()
for report in \
    ./test-reports/results-parallel.xml \
    ./test-reports/results-sequential.xml \
    ./test-reports/results-multinode.xml; do
    [ -f "$report" ] && JUNIT_REPORTS+=("$report")
done
if [ ${#JUNIT_REPORTS[@]} -gt 1 ]; then
    if ! pnpm exec junit-merge "${JUNIT_REPORTS[@]}" -o ./test-reports/results.xml; then
        echo "ERROR: Failed to merge Playwright JUnit reports."
        FAILED=1
    fi
elif [ ${#JUNIT_REPORTS[@]} -eq 1 ]; then
    if ! mv "${JUNIT_REPORTS[0]}" ./test-reports/results.xml; then
        echo "ERROR: Failed to finalize the Playwright JUnit report."
        FAILED=1
    fi
else
    echo "ERROR: Playwright produced no JUnit report."
    FAILED=1
fi
if [ -f ./test-reports/results.xml ]; then
    check_test_results ./test-reports/results.xml
    report_status=$?
    if [ "$report_status" -ne 0 ]; then
        echo "ERROR: Final Playwright JUnit report is incomplete or contains failures."
        FAILED=1
    fi
fi
pnpm run merge-coverage-reports || true

# Upload reports to E2E Reports Dashboard
if [ -n "$PLAYWRIGHT_REPORT_SERVER_URL" ] && [ -n "$PLAYWRIGHT_REPORT_TOKEN" ]; then
    echo "--- Uploading reports to E2E Reports Dashboard ---"

    PHASE="${PLAYWRIGHT_REPORT_PHASE:-all}"
    RUN_ID="${GITHUB_RUN_ID:-local}-${PHASE}"

    # Build file list dynamically — only include paths that actually exist
    UPLOAD_PATHS=()
    for p in \
        test-reports/results.xml \
        test-reports/monocart-report-parallel \
        test-reports/monocart-report-sequential \
        test-reports/client-coverage \
        test-results/; do
        [ -e "$p" ] && UPLOAD_PATHS+=("$p")
    done

    UPLOAD_ARCHIVE="/tmp/e2e-upload-${RUN_ID}.tar.gz"
    if [ ${#UPLOAD_PATHS[@]} -gt 0 ]; then
        tar -czf "$UPLOAD_ARCHIVE" "${UPLOAD_PATHS[@]}" 2>/dev/null
    fi

    if [ -f "$UPLOAD_ARCHIVE" ]; then
        echo "Uploading reports ($(du -h "$UPLOAD_ARCHIVE" | cut -f1))..."
        if ! curl --silent --show-error --fail-with-body \
            --connect-timeout 10 --max-time 300 \
            --request PUT "${PLAYWRIGHT_REPORT_SERVER_URL}/api/upload" \
            -H "Authorization: Bearer ${PLAYWRIGHT_REPORT_TOKEN}" \
            -F "archive=@${UPLOAD_ARCHIVE}" \
            -F "run_id=${RUN_ID}" \
            -F "github_run_id=${GITHUB_RUN_ID:-local}" \
            -F "branch=${PLAYWRIGHT_REPORT_BRANCH:-unknown}" \
            -F "commit_sha=${PLAYWRIGHT_REPORT_COMMIT_SHA:-unknown}" \
            -F "pr_number=${PLAYWRIGHT_REPORT_PR_NUMBER:-}" \
            -F "phase=${PHASE}" \
            -F "triggered_by=${PLAYWRIGHT_REPORT_TRIGGERED_BY:-unknown}"; then
            echo "WARNING: Failed to upload reports to E2E dashboard"
        fi
        rm -f "$UPLOAD_ARCHIVE"
    else
        echo "WARNING: No report artifacts found to upload"
    fi
fi

# Write a marker for CI diagnostics when Playwright failed despite clean JUnit testcases.
if [ "$REPORTER_FAILED" -eq 1 ]; then
    echo "Playwright failed even though completed JUnit testcases were clean; the run is incomplete." > ./test-reports/.reporter-failed
    echo "WARNING: Reporter failure detected. See ./test-reports/.reporter-failed"
fi

echo "=== Tests completed ==="
exit $FAILED
