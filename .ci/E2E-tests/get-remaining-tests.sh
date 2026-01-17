#!/bin/bash

# Returns test folders that were not run in phase 1.
# Takes two arguments:
#   $1: branch to compare (for getting relevant tests)
#   $2: space-separated list of phase 1 test folders (can be empty)
# Returns a space-separated list of remaining test folder paths.

BRANCH_TO_COMPARE="$1"
PHASE1_TESTS="$2"

# Get all test folders
ALL_TESTS=$(.ci/E2E-tests/get-all-test-folders.sh)

# If phase 1 tests is empty or all tests, return empty (no phase 2 needed)
if [ -z "$PHASE1_TESTS" ] || [ -z "$ALL_TESTS" ]; then
    echo ""
    exit 0
fi

# Convert phase 1 tests to array for comparison
declare -A PHASE1_MAP
for test in $PHASE1_TESTS; do
    PHASE1_MAP["$test"]=1
done

# Find remaining tests
REMAINING_TESTS=""
for test in $ALL_TESTS; do
    if [ -z "${PHASE1_MAP[$test]}" ]; then
        if [ -z "$REMAINING_TESTS" ]; then
            REMAINING_TESTS="$test"
        else
            REMAINING_TESTS="$REMAINING_TESTS $test"
        fi
    fi
done

echo "$REMAINING_TESTS"
