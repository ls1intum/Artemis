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

get_modules() {
    jq -r '.mappings | keys[]' "$MAPPING_FILE" 2>/dev/null || echo ""
}

get_source_paths() {
    local module="$1"
    jq -r ".mappings[\"$module\"].sourcePaths[]" "$MAPPING_FILE" 2>/dev/null || echo ""
}

get_test_paths() {
    local module="$1"
    jq -r ".mappings[\"$module\"].testPaths[]" "$MAPPING_FILE" 2>/dev/null || echo ""
}

module_matches_changes() {
    local module="$1"
    local source_paths
    local source_path
    local file

    source_paths=$(get_source_paths "$module")

    # Determine if any changed file has a literal prefix match to a module source path.
    for source_path in $source_paths; do
        if [ -n "$source_path" ]; then
            while IFS= read -r file; do
                if [[ "$file" == "$source_path"* ]]; then
                    return 0
                fi
            done <<< "$CHANGED_FILES"
        fi
    done

    return 1
}

add_test_paths_for_module() {
    local module="$1"
    local test_paths
    local test_path

    test_paths=$(get_test_paths "$module")
    for test_path in $test_paths; do
        if [ -n "$test_path" ] && [ "$test_path" != "null" ]; then
            RELEVANT_TEST_SET["$test_path"]=1
        fi
    done
}

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

# All e2e test directories/files (top-level)
# Source of truth: .ci/E2E-tests/e2e-test-mapping.json (allTestPaths)
# Safety net: auto-discover top-level e2e paths so new folders still run in remaining tests.
declare -A ALL_TEST_PATH_SET

ALL_TEST_PATHS_FROM_MAPPING=$(jq -r '.allTestPaths[]' "$MAPPING_FILE" 2>/dev/null || echo "")
for test_path in $ALL_TEST_PATHS_FROM_MAPPING; do
    if [ -n "$test_path" ] && [ "$test_path" != "null" ]; then
        ALL_TEST_PATH_SET["$test_path"]=1
    fi
done

while IFS= read -r path; do
    if [ -n "$path" ]; then
        if [ -d "$REPO_ROOT/src/test/playwright/$path" ]; then
            candidate="$path/"
        else
            candidate="$path"
        fi
        # Skip if mapping already has finer-grained children of this path
        is_refined=false
        for existing in "${!ALL_TEST_PATH_SET[@]}"; do
            if [[ "$existing" == "$candidate"* ]] && [ "$existing" != "$candidate" ]; then
                is_refined=true
                break
            fi
        done
        if [ "$is_refined" = "false" ]; then
            ALL_TEST_PATH_SET["$candidate"]=1
        fi
    fi
done < <(cd "$REPO_ROOT/src/test/playwright" && find e2e -maxdepth 1 -mindepth 1 \( -type d -o -name '*.spec.ts' \) -print)

ALL_TEST_PATHS=()
for test_path in "${!ALL_TEST_PATH_SET[@]}"; do
    ALL_TEST_PATHS+=("$test_path")
done

echo "=== Determining relevant e2e tests ==="
echo "Base branch: $BASE_BRANCH"
echo "Mapping file: $MAPPING_FILE"
echo ""

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

# Execution precedence (highest to lowest):
# 1) RUN_ALL_TESTS=true (no changes detected, mapping file missing, runAllTestsPatterns match,
#    or Playwright infrastructure change) => run the full suite.
# 2) Only Playwright spec changes (no infrastructure change) => run only RELEVANT_TESTS (skip REMAINING_TESTS).
# 3) All other changes run RELEVANT_TESTS first, then REMAINING_TESTS (excluding covered paths).
#
# Check if we should run all tests (config changes, playwright changes, etc.)
# Patterns are treated as literal path prefixes (not regex).
RUN_ALL_TESTS=false
RUN_ALL_PATTERNS=$(jq -r '.runAllTestsPatterns[]' "$MAPPING_FILE" 2>/dev/null || echo "")

# Track when changes are limited to Playwright test specs vs. test infrastructure.
ONLY_PLAYWRIGHT_TEST_CHANGES=true
PLAYWRIGHT_INFRA_CHANGE=false

for pattern in $RUN_ALL_PATTERNS; do
    while IFS= read -r file; do
        if [[ "$file" == "$pattern"* ]]; then
            echo "Found changes in '$pattern' - will run all tests"
            RUN_ALL_TESTS=true
            break 2
        fi
    done <<< "$CHANGED_FILES"
done

# Determine relevant test paths based on changed files
declare -A RELEVANT_TEST_SET

while IFS= read -r file; do
    if [[ "$file" == "src/test/playwright/"* ]]; then
        if [[ "$file" == "src/test/playwright/e2e/"* ]]; then
            RELEVANT_TEST_SET["${file#src/test/playwright/}"]=1
        else
            PLAYWRIGHT_INFRA_CHANGE=true
        fi
    else
        ONLY_PLAYWRIGHT_TEST_CHANGES=false
    fi
done <<< "$CHANGED_FILES"

if [ "$RUN_ALL_TESTS" = "true" ] || [ "$PLAYWRIGHT_INFRA_CHANGE" = "true" ]; then
    write_output "RUN_ALL_TESTS" "true"
    write_output "RELEVANT_TESTS" ""
    write_output "REMAINING_TESTS" ""
    write_output "RELEVANT_COUNT" "0"
    write_output "REMAINING_COUNT" "0"
    exit 0
fi

# Add always-run tests
ALWAYS_RUN_TESTS=$(jq -r '.alwaysRunTests[]' "$MAPPING_FILE" 2>/dev/null || echo "")
for test in $ALWAYS_RUN_TESTS; do
    if [ -n "$test" ]; then
        RELEVANT_TEST_SET["$test"]=1
    fi
done

# Determine which tests to run by mapping changed files to module source paths.
MODULES=$(get_modules)

for module in $MODULES; do
    if module_matches_changes "$module"; then
        echo "Module '$module' has changes"
        add_test_paths_for_module "$module"
    fi
done

# Convert relevant tests set to array
RELEVANT_TESTS=()
for test in "${!RELEVANT_TEST_SET[@]}"; do
    RELEVANT_TESTS+=("$test")
done

SKIP_REMAINING_TESTS=false
if [ "$ONLY_PLAYWRIGHT_TEST_CHANGES" = "true" ] && [ "$PLAYWRIGHT_INFRA_CHANGE" = "false" ]; then
    SKIP_REMAINING_TESTS=true
fi

# Determine remaining tests (all tests minus relevant tests)
# When a relevant test is a child of an all-test path (e.g., relevant=e2e/exercise/quiz-exercise/
# and all-test=e2e/exercise/), we expand the parent into its direct children and only exclude
# the ones already covered by Phase 1.
REMAINING_TESTS=()

if [ "$SKIP_REMAINING_TESTS" = "false" ]; then
    for test_path in "${ALL_TEST_PATHS[@]}"; do
        IS_COVERED=false
        HAS_PARTIAL_OVERLAP=false

        for relevant in "${RELEVANT_TESTS[@]}"; do
            if [ "$test_path" = "$relevant" ]; then
                IS_COVERED=true
                break
            elif [[ "$test_path" == "$relevant"* ]]; then
                # relevant is a parent of test_path
                IS_COVERED=true
                break
            elif [[ "$relevant" == "$test_path"* ]]; then
                # relevant is a child of test_path - partial overlap
                HAS_PARTIAL_OVERLAP=true
            fi
        done

        if [ "$IS_COVERED" = "true" ]; then
            continue
        fi

        if [ "$HAS_PARTIAL_OVERLAP" = "true" ]; then
            # Expand this parent directory into its direct children, excluding those covered by Phase 1
            local_dir="$REPO_ROOT/src/test/playwright/$test_path"
            if [ -d "$local_dir" ]; then
                while IFS= read -r child; do
                    [ -z "$child" ] && continue
                    if [ -d "$REPO_ROOT/src/test/playwright/$child" ]; then
                        child_path="$child/"
                    else
                        child_path="$child"
                    fi
                    # Check if this child is covered by any relevant test
                    CHILD_COVERED=false
                    for relevant in "${RELEVANT_TESTS[@]}"; do
                        if [ "$child_path" = "$relevant" ] || [[ "$child_path" == "$relevant"* ]]; then
                            CHILD_COVERED=true
                            break
                        fi
                    done
                    if [ "$CHILD_COVERED" = "false" ]; then
                        REMAINING_TESTS+=("$child_path")
                    fi
                done < <(cd "$REPO_ROOT/src/test/playwright" && find "$test_path" -maxdepth 1 -mindepth 1 \( -type d -o -name '*.spec.ts' \) -print)
            fi
        else
            REMAINING_TESTS+=("$test_path")
        fi
    done
fi

# Sort and deduplicate
if [ ${#RELEVANT_TESTS[@]} -gt 0 ]; then
    mapfile -t RELEVANT_TESTS_SORTED < <(printf '%s\n' "${RELEVANT_TESTS[@]}" | sort -u)
else
    RELEVANT_TESTS_SORTED=()
fi

if [ ${#REMAINING_TESTS[@]} -gt 0 ]; then
    mapfile -t REMAINING_TESTS_SORTED < <(printf '%s\n' "${REMAINING_TESTS[@]}" | sort -u)
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
