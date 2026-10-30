#!/bin/bash
# Script to determine which e2e tests are relevant based on changed files
# Usage: ./determine-relevant-tests.sh <base-branch>
# Output: Sets RELEVANT_TESTS and REMAINING_TESTS environment variables
#
# In GitHub Actions, this script writes to GITHUB_OUTPUT
# For local testing, it prints the results to stdout

set -e

# This script uses associative arrays and `mapfile`, both of which need bash 4+. CI runners ship
# bash 5, but macOS still ships bash 3.2 as /bin/bash, where the script dies on `declare -A` with a
# misleading "invalid option" error. Re-exec under a newer bash when one is on PATH (Homebrew
# installs it as /opt/homebrew/bin/bash) so the script is usable locally, which is what the
# e2e-pr-check agent skill and anyone debugging test selection needs.
if [ -z "${DETERMINE_RELEVANT_TESTS_REEXEC:-}" ] && [ "${BASH_VERSINFO[0]}" -lt 4 ]; then
    for candidate in "$(command -v bash || true)" /opt/homebrew/bin/bash /usr/local/bin/bash; do
        # shellcheck disable=SC2016  # single quotes are required: the expansion must happen in the candidate shell
        if [ -x "$candidate" ] && [ "$("$candidate" -c 'echo ${BASH_VERSINFO[0]}')" -ge 4 ]; then
            DETERMINE_RELEVANT_TESTS_REEXEC=1 exec "$candidate" "${BASH_SOURCE[0]}" "$@"
        fi
    done
    echo "ERROR: this script needs bash 4 or newer (found $BASH_VERSION)." >&2
    echo "On macOS: brew install bash" >&2
    exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MAPPING_FILE="$SCRIPT_DIR/e2e-test-mapping.json"
REPO_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"

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

# Fail closed when the comparison or mapping cannot establish a safe selection.
run_all_tests() {
    echo "$1 Running all tests."
    write_output "RUN_ALL_TESTS" "true"
    write_output "RELEVANT_TESTS" ""
    write_output "REMAINING_TESTS" ""
    write_output "RELEVANT_COUNT" "0"
    write_output "REMAINING_COUNT" "0"
    exit 0
}

if [ ! -f "$MAPPING_FILE" ] || ! jq -e '
    (.mappings | type == "object") and
    (.alwaysRunTests | length > 0) and
    ([.mappings[].sourcePaths[], .mappings[].testPaths[], .alwaysRunTests[], .allTestPaths[], .runAllTestsPatterns[]]
        | all(.[]; type == "string" and length > 0 and (test("[[:space:]]") | not)))
' "$MAPPING_FILE" >/dev/null 2>&1; then
    run_all_tests "Mapping file is missing or invalid."
fi

# All e2e test directories/files (top-level)
# Source of truth: supporting_scripts/ci/E2E-tests/e2e-test-mapping.json (allTestPaths)
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

# Compare the complete PR diff with its merge base. Never silently substitute HEAD~1:
# that loses earlier commits. Disable rename detection to inspect both the removed and added
# paths, and use NUL delimiters so unusual file names cannot hide an unclassified change.
cd "$REPO_ROOT"
CHANGED_FILES_FILE=$(mktemp)
trap 'rm -f "$CHANGED_FILES_FILE"' EXIT
if ! git diff --no-renames --name-only -z "$BASE_BRANCH"...HEAD > "$CHANGED_FILES_FILE"; then
    run_all_tests "Could not compare against the requested base '$BASE_BRANCH'."
fi
CHANGED_FILES=()
while IFS= read -r -d '' file; do
    CHANGED_FILES+=("$file")
done < "$CHANGED_FILES_FILE"
if [ ${#CHANGED_FILES[@]} -eq 0 ]; then
    run_all_tests "No changed files detected."
fi

echo "Changed files:"
printf '  %s\n' "${CHANGED_FILES[@]}"
echo ""

# Cross-cutting source modules affect other modules' workflows. A narrow mapping in these
# modules is useful for ordering a full run, but cannot prove selective coverage is sufficient.
GLOBAL_SOURCE_PATHS=(
    "src/main/java/de/tum/cit/aet/artemis/core/"
    "src/main/java/de/tum/cit/aet/artemis/account/"
    "src/main/java/de/tum/cit/aet/artemis/exercise/"
    "src/main/java/de/tum/cit/aet/artemis/localci/"
    "src/main/java/de/tum/cit/aet/artemis/localvc/"
    "src/main/java/de/tum/cit/aet/artemis/buildagent/"
    "src/main/webapp/app/core/"
    "src/main/webapp/app/shared/"
)
RUN_ALL_PATTERNS=$(jq -r '.runAllTestsPatterns[]' "$MAPPING_FILE")
MODULES=$(get_modules)
declare -A RELEVANT_TEST_SET

for file in "${CHANGED_FILES[@]}"; do
    for pattern in $RUN_ALL_PATTERNS "${GLOBAL_SOURCE_PATHS[@]}"; do
        if [[ "$file" == "$pattern"* ]]; then
            run_all_tests "Found cross-cutting or infrastructure changes in '$pattern'."
        fi
    done

    if [[ "$file" == "src/test/playwright/"* ]]; then
        if [[ "$file" == "src/test/playwright/e2e/"*.spec.ts ]] && [[ "$file" != *[[:space:]]* ]]; then
            # Deleted specs are still classified, but are not valid Playwright arguments.
            if [ -f "$REPO_ROOT/$file" ]; then
                RELEVANT_TEST_SET["${file#src/test/playwright/}"]=1
            fi
            continue
        fi
        run_all_tests "Found Playwright infrastructure changes in '$file'."
    fi

    # Every changed file must have a non-empty mapping. Unknown paths, including modules
    # without E2E coverage, require the full suite rather than degrading to smoke tests.
    matched=false
    for module in $MODULES; do
        for source_path in $(get_source_paths "$module"); do
            if [[ "$file" == "$source_path"* ]]; then
                if [ -z "$(get_test_paths "$module")" ]; then
                    run_all_tests "Module '$module' has no selective E2E mapping."
                fi
                echo "Module '$module' has changes"
                add_test_paths_for_module "$module"
                matched=true
            fi
        done
    done
    if [ "$matched" != true ]; then
        run_all_tests "No selective E2E mapping for '$file'."
    fi
done

# Every selection includes authentication and health smoke coverage. A missing smoke spec
# means the mapping needs maintenance; do not pass a nonexistent path to Playwright.
ALWAYS_RUN_TESTS=$(jq -r '.alwaysRunTests[]' "$MAPPING_FILE")
for test in $ALWAYS_RUN_TESTS; do
    [ -f "$REPO_ROOT/src/test/playwright/$test" ] || run_all_tests "Always-run spec '$test' is missing."
    RELEVANT_TEST_SET["$test"]=1
done

RELEVANT_TESTS=()
for test in "${!RELEVANT_TEST_SET[@]}"; do
    [ -e "$REPO_ROOT/src/test/playwright/$test" ] || run_all_tests "Mapped E2E path '$test' is missing."
    RELEVANT_TESTS+=("$test")
done

# Always compute the complement, including for spec-only changes. The workflow's
# run_full_suite input decides whether Phase 2 is needed, so a full override stays full.
# Determine remaining tests (all tests minus relevant tests)
# When a relevant test is a child of an all-test path (e.g., relevant=e2e/exercise/quiz-exercise/
# and all-test=e2e/exercise/), we expand the parent into its direct children and only exclude
# the ones already covered by Phase 1.
REMAINING_TESTS=()

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
