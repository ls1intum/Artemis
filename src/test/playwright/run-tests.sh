#!/bin/bash
# Script to run Playwright tests with optional test path filtering
# Usage: ./run-tests.sh [test-paths...]
# If no test paths are provided, runs all tests in e2e/

set -e

TEST_PATHS=("$@")
FAILED=0
IGNORE_ARGS=()

if [ -n "$PLAYWRIGHT_IGNORE_PATHS" ]; then
    read -r -a IGNORE_PATHS <<< "$PLAYWRIGHT_IGNORE_PATHS"
    for ignore_path in "${IGNORE_PATHS[@]}"; do
        IGNORE_ARGS+=("--ignore" "$ignore_path")
    done
    echo "Ignoring paths: ${IGNORE_PATHS[*]}"
fi

echo "=== Running Playwright Tests ==="

if [ ${#TEST_PATHS[@]} -gt 0 ]; then
    echo "Running filtered tests: ${TEST_PATHS[*]}"
    
    # Run parallel tests (fast and slow projects)
    echo "--- Running parallel tests ---"
    PLAYWRIGHT_TEST_TYPE=parallel npx playwright test --project=fast-tests --project=slow-tests "${TEST_PATHS[@]}" "${IGNORE_ARGS[@]}" || FAILED=1
    
    # Run sequential tests
    echo "--- Running sequential tests ---"
    PLAYWRIGHT_TEST_TYPE=sequential npx playwright test --project=sequential-tests --workers 1 "${TEST_PATHS[@]}" "${IGNORE_ARGS[@]}" || FAILED=1
else
    echo "Running all tests"
    
    # Run parallel tests (fast and slow projects) 
    echo "--- Running parallel tests ---"
    PLAYWRIGHT_TEST_TYPE=parallel npx playwright test e2e --project=fast-tests --project=slow-tests "${IGNORE_ARGS[@]}" || FAILED=1
    
    # Run sequential tests
    echo "--- Running sequential tests ---"
    PLAYWRIGHT_TEST_TYPE=sequential npx playwright test e2e --project=sequential-tests --workers 1 "${IGNORE_ARGS[@]}" || FAILED=1
fi

# Merge reports
echo "--- Merging test reports ---"
npm run merge-junit-reports || true
npm run merge-coverage-reports || true

echo "=== Tests completed ==="
exit $FAILED
