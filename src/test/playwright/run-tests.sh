#!/bin/bash
# Script to run Playwright tests with optional test path filtering
# Usage: ./run-tests.sh [test-paths...]
# If no test paths are provided, runs all tests in e2e/

TEST_PATHS=("$@")
FAILED=0
REPORTER_FAILED=0

if [ ${#TEST_PATHS[@]} -eq 0 ] && [ -n "$PLAYWRIGHT_TEST_PATHS" ]; then
    read -r -a TEST_PATHS <<< "$PLAYWRIGHT_TEST_PATHS"
fi

# Check JUnit XML to determine if actual test failures occurred.
# Returns 0 if tests passed, 1 if tests failed or results are missing.
# This distinguishes real test failures from reporter crashes (e.g., monocart OOM).
check_test_results() {
    local xml_file="$1"

    if [ ! -f "$xml_file" ]; then
        return 1
    fi

    if ! grep -q '<testcase' "$xml_file"; then
        return 1
    fi

    if grep -qE 'failures="[1-9]|errors="[1-9]' "$xml_file"; then
        return 1
    fi

    return 0
}

# Run a playwright test command and evaluate the result.
# Sets FAILED=1 on real test failures, REPORTER_FAILED=1 on reporter-only failures.
run_playwright() {
    local test_type="$1"
    shift

    PLAYWRIGHT_TEST_TYPE="$test_type" npx playwright test "$@"
    local exit_code=$?

    if [ $exit_code -ne 0 ]; then
        local junit_file="./test-reports/results-${test_type}.xml"
        if check_test_results "$junit_file"; then
            echo "WARNING: Playwright exited with code $exit_code but JUnit XML shows no test failures."
            echo "This likely indicates a reporter failure (e.g., monocart OOM). Tests themselves passed."
            REPORTER_FAILED=1
        else
            FAILED=1
        fi
    fi
}

echo "=== Running Playwright Tests ==="

if [ ${#TEST_PATHS[@]} -gt 0 ]; then
    echo "Running filtered tests: ${TEST_PATHS[*]}"

    # Run parallel tests (fast and slow projects)
    echo "--- Running parallel tests ---"
    run_playwright parallel --project=fast-tests --project=slow-tests "${TEST_PATHS[@]}"

    # Run sequential tests
    echo "--- Running sequential tests ---"
    run_playwright sequential --project=sequential-tests --workers 1 "${TEST_PATHS[@]}"
else
    echo "Running all tests"

    # Run parallel tests (fast and slow projects)
    echo "--- Running parallel tests ---"
    run_playwright parallel e2e --project=fast-tests --project=slow-tests

    # Run sequential tests
    echo "--- Running sequential tests ---"
    run_playwright sequential e2e --project=sequential-tests --workers 1
fi

# Merge reports
echo "--- Merging test reports ---"
npm run merge-junit-reports || true
npm run merge-coverage-reports || true

# Write marker file if reporter failed but tests passed (picked up by execute.sh for CI reporting).
# When tests also fail, the test failure is the primary signal — no need to add reporter noise.
if [ "$REPORTER_FAILED" -eq 1 ] && [ "$FAILED" -eq 0 ]; then
    echo "Reporter process failed (likely monocart OOM). Test results were not affected." > ./test-reports/.reporter-failed
    echo "WARNING: Reporter failure detected. See ./test-reports/.reporter-failed"
fi

echo "=== Tests completed ==="
exit $FAILED
