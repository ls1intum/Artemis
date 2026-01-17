#!/bin/bash
# Script to run Playwright tests with optional test path filtering
# Usage: ./run-tests.sh [test-paths...]
# If no test paths are provided, runs all tests in e2e/

set -e

TEST_PATHS="$@"

echo "=== Running Playwright Tests ==="

if [ -n "$TEST_PATHS" ]; then
    echo "Running filtered tests: $TEST_PATHS"
    
    # Run parallel tests (fast and slow projects)
    echo "--- Running parallel tests ---"
    PLAYWRIGHT_TEST_TYPE=parallel npx playwright test --project=fast-tests --project=slow-tests $TEST_PATHS || true
    
    # Run sequential tests
    echo "--- Running sequential tests ---"
    PLAYWRIGHT_TEST_TYPE=sequential npx playwright test --project=sequential-tests --workers 1 $TEST_PATHS || true
else
    echo "Running all tests"
    
    # Run parallel tests (fast and slow projects) 
    echo "--- Running parallel tests ---"
    PLAYWRIGHT_TEST_TYPE=parallel npx playwright test e2e --project=fast-tests --project=slow-tests || true
    
    # Run sequential tests
    echo "--- Running sequential tests ---"
    PLAYWRIGHT_TEST_TYPE=sequential npx playwright test e2e --project=sequential-tests --workers 1 || true
fi

# Merge reports
echo "--- Merging test reports ---"
npm run merge-junit-reports || true
npm run merge-coverage-reports || true

echo "=== Tests completed ==="
