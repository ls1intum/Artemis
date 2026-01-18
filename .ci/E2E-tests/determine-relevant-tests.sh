#!/bin/bash
# Script to determine which e2e tests are relevant based on changed files
# Usage: ./determine-relevant-tests.sh <base-branch>
# Output: Sets RELEVANT_TESTS and REMAINING_TESTS environment variables
#
# In GitHub Actions, this script writes to GITHUB_OUTPUT
# For local testing, it prints the results to stdout

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MAPPING_FILE="$SCRIPT_DIR/e2e-test-mapping.json"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

# Base branch for comparison (default: origin/develop)
BASE_BRANCH="${1:-origin/develop}"

# Helper function to write output (works both in CI and locally)
write_output() {
    local key="$1"
    local value="$2"
    
    if [ -n "$GITHUB_OUTPUT" ]; then
        echo "$key=$value" >> "$GITHUB_OUTPUT"
    fi
    echo "OUTPUT: $key=$value"
}

# All e2e test directories/files (top-level)
ALL_TEST_PATHS=(
    "e2e/atlas/"
    "e2e/course/"
    "e2e/exam/"
    "e2e/exercise/"
    "e2e/lecture/"
    "e2e/Login.spec.ts"
    "e2e/Logout.spec.ts"
    "e2e/SystemHealth.spec.ts"
)

echo "=== Determining relevant e2e tests ==="
echo "Base branch: $BASE_BRANCH"
echo "Mapping file: $MAPPING_FILE"
echo ""

# Verify mapping file exists
if [ ! -f "$MAPPING_FILE" ]; then
    echo "ERROR: Mapping file not found: $MAPPING_FILE"
    write_output "RUN_ALL_TESTS" "true"
    write_output "RELEVANT_TESTS" ""
    write_output "REMAINING_TESTS" ""
    write_output "RELEVANT_COUNT" "0"
    write_output "REMAINING_COUNT" "0"
    exit 0
fi

# Get list of changed files
cd "$REPO_ROOT"
CHANGED_FILES=$(git diff --name-only "$BASE_BRANCH"...HEAD 2>/dev/null || git diff --name-only HEAD~1 2>/dev/null || echo "")

if [ -z "$CHANGED_FILES" ]; then
    echo "No changed files detected. Running all tests."
    write_output "RUN_ALL_TESTS" "true"
    write_output "RELEVANT_TESTS" ""
    write_output "REMAINING_TESTS" ""
    write_output "RELEVANT_COUNT" "0"
    write_output "REMAINING_COUNT" "0"
    exit 0
fi

echo "Changed files:"
echo "$CHANGED_FILES" | head -20
TOTAL_CHANGED=$(echo "$CHANGED_FILES" | wc -l | tr -d ' ')
if [ "$TOTAL_CHANGED" -gt 20 ]; then
    echo "... and $((TOTAL_CHANGED - 20)) more files"
fi
echo ""

# Check if we should run all tests (config changes, playwright changes, etc.)
RUN_ALL_TESTS=false
RUN_ALL_PATTERNS=$(jq -r '.runAllTestsPatterns[]' "$MAPPING_FILE" 2>/dev/null || echo "")

for pattern in $RUN_ALL_PATTERNS; do
    while IFS= read -r file; do
        if [[ "$file" == "$pattern"* ]]; then
            echo "Found changes in '$pattern' - will run all tests"
            RUN_ALL_TESTS=true
            break 2
        fi
    done <<< "$CHANGED_FILES"
done

if [ "$RUN_ALL_TESTS" = "true" ]; then
    write_output "RUN_ALL_TESTS" "true"
    write_output "RELEVANT_TESTS" ""
    write_output "REMAINING_TESTS" ""
    write_output "RELEVANT_COUNT" "0"
    write_output "REMAINING_COUNT" "0"
    exit 0
fi

# Determine relevant test paths based on changed files
declare -A RELEVANT_TEST_SET

# Add always-run tests
ALWAYS_RUN_TESTS=$(jq -r '.alwaysRunTests[]' "$MAPPING_FILE" 2>/dev/null || echo "")
for test in $ALWAYS_RUN_TESTS; do
    if [ -n "$test" ]; then
        RELEVANT_TEST_SET["$test"]=1
    fi
done

# Get all module mappings
MODULES=$(jq -r '.mappings | keys[]' "$MAPPING_FILE" 2>/dev/null || echo "")

for module in $MODULES; do
    # Get source paths for this module
    SOURCE_PATHS=$(jq -r ".mappings[\"$module\"].sourcePaths[]" "$MAPPING_FILE" 2>/dev/null || echo "")
    
    # Check if any changed file matches a source path (using literal prefix matching)
    MATCHED=false
    for source_path in $SOURCE_PATHS; do
        if [ -n "$source_path" ]; then
            while IFS= read -r file; do
                if [[ "$file" == "$source_path"* ]]; then
                    MATCHED=true
                    break 2
                fi
            done <<< "$CHANGED_FILES"
        fi
    done
    
    if [ "$MATCHED" = "true" ]; then
        echo "Module '$module' has changes"
        # Get test paths for this module
        TEST_PATHS=$(jq -r ".mappings[\"$module\"].testPaths[]" "$MAPPING_FILE" 2>/dev/null || echo "")
        for test_path in $TEST_PATHS; do
            if [ -n "$test_path" ] && [ "$test_path" != "null" ]; then
                RELEVANT_TEST_SET["$test_path"]=1
            fi
        done
    fi
done

# Convert relevant tests set to array
RELEVANT_TESTS=()
for test in "${!RELEVANT_TEST_SET[@]}"; do
    RELEVANT_TESTS+=("$test")
done

# Determine remaining tests (all tests minus relevant tests)
# This is a bit tricky because we need to handle partial overlaps
REMAINING_TESTS=()

for test_path in "${ALL_TEST_PATHS[@]}"; do
    IS_COVERED=false
    
    for relevant in "${RELEVANT_TESTS[@]}"; do
        # Check if this test path is covered by a relevant test
        # A test path is covered if:
        # 1. It equals a relevant path
        # 2. It starts with a relevant path (e.g., e2e/exam/ covers e2e/exam/*)
        # 3. A relevant path starts with it (more specific test path covers general)
        if [ "$test_path" = "$relevant" ]; then
            IS_COVERED=true
            break
        elif [[ "$test_path" == "$relevant"* ]]; then
            # relevant is a parent of test_path
            IS_COVERED=true
            break
        elif [[ "$relevant" == "$test_path"* ]]; then
            # relevant is a child of test_path - partial coverage
            # We still need to add remaining to catch other tests in this folder
            continue
        fi
    done
    
    if [ "$IS_COVERED" = "false" ]; then
        REMAINING_TESTS+=("$test_path")
    fi
done

# Sort and deduplicate
if [ ${#RELEVANT_TESTS[@]} -gt 0 ]; then
    IFS=$'\n' RELEVANT_TESTS_SORTED=($(printf '%s\n' "${RELEVANT_TESTS[@]}" | sort -u))
    unset IFS
else
    RELEVANT_TESTS_SORTED=()
fi

if [ ${#REMAINING_TESTS[@]} -gt 0 ]; then
    IFS=$'\n' REMAINING_TESTS_SORTED=($(printf '%s\n' "${REMAINING_TESTS[@]}" | sort -u))
    unset IFS
else
    REMAINING_TESTS_SORTED=()
fi

echo ""
echo "=== Results ==="
echo "Relevant tests (Phase 1):"
if [ ${#RELEVANT_TESTS_SORTED[@]} -gt 0 ]; then
    printf '  %s\n' "${RELEVANT_TESTS_SORTED[@]}"
else
    echo "  (none)"
fi
echo ""
echo "Remaining tests (Phase 2):"
if [ ${#REMAINING_TESTS_SORTED[@]} -gt 0 ]; then
    printf '  %s\n' "${REMAINING_TESTS_SORTED[@]}"
else
    echo "  (none)"
fi
echo ""

# Build space-separated strings for output (Playwright accepts multiple paths)
RELEVANT_TESTS_STRING=""
for test in "${RELEVANT_TESTS_SORTED[@]}"; do
    if [ -n "$RELEVANT_TESTS_STRING" ]; then
        RELEVANT_TESTS_STRING="$RELEVANT_TESTS_STRING $test"
    else
        RELEVANT_TESTS_STRING="$test"
    fi
done

REMAINING_TESTS_STRING=""
for test in "${REMAINING_TESTS_SORTED[@]}"; do
    if [ -n "$REMAINING_TESTS_STRING" ]; then
        REMAINING_TESTS_STRING="$REMAINING_TESTS_STRING $test"
    else
        REMAINING_TESTS_STRING="$test"
    fi
done

write_output "RUN_ALL_TESTS" "false"
write_output "RELEVANT_TESTS" "$RELEVANT_TESTS_STRING"
write_output "REMAINING_TESTS" "$REMAINING_TESTS_STRING"
write_output "RELEVANT_COUNT" "${#RELEVANT_TESTS_SORTED[@]}"
write_output "REMAINING_COUNT" "${#REMAINING_TESTS_SORTED[@]}"

echo ""
echo "Relevant test count: ${#RELEVANT_TESTS_SORTED[@]}"
echo "Remaining test count: ${#REMAINING_TESTS_SORTED[@]}"
