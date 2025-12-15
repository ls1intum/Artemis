#!/bin/bash

# Check argument
if [ -z "$1" ]; then
  echo "Usage: $0 <path-to-test-file-or-folder> [number-of-runs]"
  echo "Example: $0 src/test/playwright/e2e/atlas/CompetencyExerciseInteractions.spec.ts 5"
  exit 1
fi

INPUT_PATH="$1"
NUM_RUNS="${2:-10}" # Default to 10 runs

# Save current directory to resolve relative paths later
ORIGINAL_PWD=$(pwd)

# Determine Artemis root path (assuming script is in supporting_scripts/playwright)
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ARTEMIS_PATH="$(cd "$SCRIPT_DIR/../.." && pwd)"
PLAYWRIGHT_DIR="$ARTEMIS_PATH/src/test/playwright"

# Handle relative paths for the test file
if [[ "$INPUT_PATH" = /* ]]; then
  TEST_PATH="$INPUT_PATH"
else
  TEST_PATH="$ORIGINAL_PWD/$INPUT_PATH"
fi

echo "--------------------------------------------------"
echo "Flakiness Check Script"
echo "--------------------------------------------------"
echo "Target: $TEST_PATH"
echo "Runs:   $NUM_RUNS headless + $((NUM_RUNS / 2)) headed"
echo "--------------------------------------------------"

cd "$PLAYWRIGHT_DIR" || exit

# Temporary file to store results (format: PASS|test_name or FAIL|test_name)
RESULTS_FILE=$(mktemp)

# Calculate headed runs (half of NUM_RUNS)
HEADED_RUNS=$((NUM_RUNS / 2))

echo ""
echo "========== HEADLESS MODE ($NUM_RUNS runs) =========="
echo ""

# Run all tests with --repeat-each to avoid startup overhead on each iteration
# --retries=0: Disable retries to detect true flakiness
# --repeat-each=N: Run each test N times in a single execution
OUTPUT=$(FORCE_COLOR=0 npx playwright test "$TEST_PATH" --reporter=list --retries=0 --repeat-each="$NUM_RUNS" 2>&1 | tee /dev/stderr)

# Parse individual test results (silently)
echo "$OUTPUT" | while IFS= read -r line; do
    # Check if line contains a test result (passed)
    if echo "$line" | grep -qE '^\s*✓\s+[0-9]+'; then
        # Extract test name: everything after first " › " and remove duration
        test_name=$(echo "$line" | sed -E 's/.*[0-9]+[^›]*› //' | sed -E 's/ \([0-9.]+m?s\)$//' | sed -E 's/^[[:space:]]+|[[:space:]]+$//g')
        if [ -n "$test_name" ]; then
            echo "PASS|$test_name" >> "$RESULTS_FILE"
        fi
    # Check if line contains a test result (failed) - various fail symbols
    elif echo "$line" | grep -qE '^\s*[✘×]\s+[0-9]+'; then
        test_name=$(echo "$line" | sed -E 's/.*[0-9]+[^›]*› //' | sed -E 's/ \([0-9.]+m?s\)$//' | sed -E 's/^[[:space:]]+|[[:space:]]+$//g')
        if [ -n "$test_name" ]; then
            echo "FAIL|$test_name" >> "$RESULTS_FILE"
        fi
    fi
done

echo ""
echo "========== HEADED MODE ($HEADED_RUNS runs) =========="
echo ""

# Run tests in headed mode
OUTPUT=$(FORCE_COLOR=0 npx playwright test "$TEST_PATH" --reporter=list --retries=0 --repeat-each="$HEADED_RUNS" --headed 2>&1 | tee /dev/stderr)

# Parse individual test results (silently)
echo "$OUTPUT" | while IFS= read -r line; do
    # Check if line contains a test result (passed)
    if echo "$line" | grep -qE '^\s*✓\s+[0-9]+'; then
        # Extract test name: everything after first " › " and remove duration
        test_name=$(echo "$line" | sed -E 's/.*[0-9]+[^›]*› //' | sed -E 's/ \([0-9.]+m?s\)$//' | sed -E 's/^[[:space:]]+|[[:space:]]+$//g')
        if [ -n "$test_name" ]; then
            echo "PASS|$test_name" >> "$RESULTS_FILE"
        fi
    # Check if line contains a test result (failed) - various fail symbols
    elif echo "$line" | grep -qE '^\s*[✘×]\s+[0-9]+'; then
        test_name=$(echo "$line" | sed -E 's/.*[0-9]+[^›]*› //' | sed -E 's/ \([0-9.]+m?s\)$//' | sed -E 's/^[[:space:]]+|[[:space:]]+$//g')
        if [ -n "$test_name" ]; then
            echo "FAIL|$test_name" >> "$RESULTS_FILE"
        fi
    fi
done

echo ""

echo "--------------------------------------------------"
echo "FLAKINESS REPORT"
echo "--------------------------------------------------"
printf "%-60s | %6s | %6s | %6s | %10s\n" "Test Name" "Runs" "Pass" "Fail" "Flakiness"
echo "-----------------------------------------------------------------------------------------------------"

# Process results using sort and uniq to aggregate
# First get unique test names
cut -d'|' -f2 "$RESULTS_FILE" | sort -u | while IFS= read -r test_name; do
    if [ -n "$test_name" ]; then
        # Count passes and fails for this test (use grep -F for fixed string matching)
        pass_count=$(grep -F "PASS|${test_name}" "$RESULTS_FILE" | wc -l | tr -d ' ')
        fail_count=$(grep -F "FAIL|${test_name}" "$RESULTS_FILE" | wc -l | tr -d ' ')
        total=$((pass_count + fail_count))
        
        if [ "$total" -gt 0 ]; then
            # Calculate flakiness percentage
            flakiness=$(awk "BEGIN {printf \"%.1f%%\", ($fail_count / $total) * 100}")
            
            # Truncate long names for display
            display_name="$test_name"
            if [ ${#display_name} -gt 57 ]; then
                display_name="${display_name:0:57}..."
            fi
            
            printf "%-60s | %6d | %6d | %6d | %10s\n" "$display_name" "$total" "$pass_count" "$fail_count" "$flakiness"
        fi
    fi
done

# Cleanup
rm -f "$RESULTS_FILE"

echo ""
echo "Done!"
