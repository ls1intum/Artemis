#!/usr/bin/env bash
# Verification script for e2e test determination logic.
# Simulates the determine-relevant-tests.sh logic for a given set of modules
# and validates that every spec file appears in at least one phase.
#
# NOTE: This script intentionally reimplements the logic from determine-relevant-tests.sh
# using Bash 3.2-compatible constructs (file-based sets instead of associative arrays)
# so it can run locally on macOS. The CI script uses Bash 4+ features (declare -A, mapfile).
#
# Usage:
#   ./verify-test-determination.sh <module1> [module2] ...
#   ./verify-test-determination.sh --all
#
# Examples:
#   ./verify-test-determination.sh assessment
#   ./verify-test-determination.sh quiz assessment
#   ./verify-test-determination.sh core
#   ./verify-test-determination.sh --all

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MAPPING_FILE="$SCRIPT_DIR/e2e-test-mapping.json"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
PLAYWRIGHT_DIR="$REPO_ROOT/src/test/playwright"

TMPDIR_VERIFY=$(mktemp -d)
trap 'rm -rf "$TMPDIR_VERIFY"' EXIT

if [ ! -f "$MAPPING_FILE" ]; then
    echo "ERROR: Mapping file not found: $MAPPING_FILE"
    exit 1
fi

if [ $# -eq 0 ]; then
    echo "Usage: $0 <module1> [module2] ..."
    echo "       $0 --all"
    echo ""
    echo "Available modules:"
    jq -r '.mappings | keys[]' "$MAPPING_FILE" | sed 's/^/  /'
    exit 1
fi

# --- Helper functions ---

# Add a line to a file if not already present
add_unique() {
    local file="$1"
    local value="$2"
    if ! grep -qxF "$value" "$file" 2>/dev/null; then
        echo "$value" >> "$file"
    fi
}

# Read lines from a file into a bash array (Bash 3.2 compatible)
read_file_to_array() {
    local file="$1"
    local varname="$2"
    eval "$varname=()"
    while IFS= read -r line; do
        [ -n "$line" ] && eval "$varname+=(\"$line\")"
    done < "$file"
}

# Sort and deduplicate an array, writing results to a file
sort_unique_to_file() {
    local output_file="$1"
    shift
    local items=("$@")
    if [ ${#items[@]} -gt 0 ]; then
        printf '%s\n' "${items[@]}" | sort -u > "$output_file"
    else
        touch "$output_file"
    fi
}

# Expand test paths (directories and spec files) into individual spec files
expand_paths_to_specs() {
    local input_file="$1"
    local output_file="$2"
    touch "$output_file"
    while IFS= read -r path; do
        [ -z "$path" ] && continue
        if [[ "$path" == *.spec.ts ]]; then
            echo "$path" >> "$output_file"
        elif [ -d "$PLAYWRIGHT_DIR/$path" ]; then
            (cd "$PLAYWRIGHT_DIR" && find "$path" -name '*.spec.ts' -print) >> "$output_file"
        fi
    done < "$input_file"
    sort -u "$output_file" -o "$output_file"
}

# --- Build relevant test set ---

build_relevant_tests() {
    local output_file="$1"
    touch "$output_file"

    # Always-run tests
    jq -r '.alwaysRunTests[]' "$MAPPING_FILE" 2>/dev/null | while IFS= read -r test; do
        [ -n "$test" ] && add_unique "$output_file" "$test"
    done

    # Add test paths for each selected module
    for module in "${MODULES[@]}"; do
        jq -r ".mappings[\"$module\"].testPaths[]" "$MAPPING_FILE" 2>/dev/null | while IFS= read -r test_path; do
            if [ -n "$test_path" ] && [ "$test_path" != "null" ]; then
                add_unique "$output_file" "$test_path"
            fi
        done
    done
}

# --- Build all test paths ---

build_all_test_paths() {
    local output_file="$1"
    touch "$output_file"

    # From mapping file
    jq -r '.allTestPaths[]' "$MAPPING_FILE" 2>/dev/null | while IFS= read -r test_path; do
        if [ -n "$test_path" ] && [ "$test_path" != "null" ]; then
            add_unique "$output_file" "$test_path"
        fi
    done

    # Auto-discover from filesystem, skipping paths already refined in mapping
    (cd "$PLAYWRIGHT_DIR" && find e2e -maxdepth 1 -mindepth 1 \( -type d -o -name '*.spec.ts' \) -print) | while IFS= read -r path; do
        if [ -n "$path" ]; then
            if [ -d "$PLAYWRIGHT_DIR/$path" ]; then
                candidate="$path/"
            else
                candidate="$path"
            fi
            # Skip if mapping already has finer-grained children of this path
            if grep -q "^${candidate}" "$output_file" 2>/dev/null && \
               ! grep -qxF "$candidate" "$output_file" 2>/dev/null; then
                continue
            fi
            add_unique "$output_file" "$candidate"
        fi
    done
}

# --- Determine remaining tests ---

determine_remaining_tests() {
    REMAINING_TESTS=()

    for test_path in "${ALL_TEST_PATHS[@]}"; do
        IS_COVERED=false
        HAS_PARTIAL_OVERLAP=false

        for relevant in "${RELEVANT_TESTS[@]}"; do
            if [ "$test_path" = "$relevant" ]; then
                IS_COVERED=true
                break
            elif [[ "$test_path" == "$relevant"* ]]; then
                IS_COVERED=true
                break
            elif [[ "$relevant" == "$test_path"* ]]; then
                HAS_PARTIAL_OVERLAP=true
            fi
        done

        if [ "$IS_COVERED" = "true" ]; then
            continue
        fi

        if [ "$HAS_PARTIAL_OVERLAP" = "true" ]; then
            local_dir="$PLAYWRIGHT_DIR/$test_path"
            if [ -d "$local_dir" ]; then
                while IFS= read -r child; do
                    [ -z "$child" ] && continue
                    if [ -d "$PLAYWRIGHT_DIR/$child" ]; then
                        child_path="$child/"
                    else
                        child_path="$child"
                    fi
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
                done < <(cd "$PLAYWRIGHT_DIR" && find "$test_path" -maxdepth 1 -mindepth 1 \( -type d -o -name '*.spec.ts' \) -print)
            fi
        else
            REMAINING_TESTS+=("$test_path")
        fi
    done
}

# --- Validate coverage ---

validate_coverage() {
    local phase1_specs="$1"
    local phase2_specs="$2"

    ALL_SPECS_FILE="$TMPDIR_VERIFY/all_specs.txt"
    (cd "$PLAYWRIGHT_DIR" && find e2e -name '*.spec.ts' -print | sort) > "$ALL_SPECS_FILE"

    BOTH_FILE="$TMPDIR_VERIFY/both.txt"
    comm -12 "$phase1_specs" "$phase2_specs" > "$BOTH_FILE"

    COVERED_FILE="$TMPDIR_VERIFY/covered.txt"
    sort -u "$phase1_specs" "$phase2_specs" > "$COVERED_FILE"
    MISSING_FILE="$TMPDIR_VERIFY/missing.txt"
    comm -23 "$ALL_SPECS_FILE" "$COVERED_FILE" > "$MISSING_FILE"

    local both_count all_spec_count p1_count p2_count missing_count
    both_count=$(wc -l < "$BOTH_FILE" | tr -d ' ')
    missing_count=$(wc -l < "$MISSING_FILE" | tr -d ' ')
    all_spec_count=$(wc -l < "$ALL_SPECS_FILE" | tr -d ' ')
    p1_count=$(wc -l < "$phase1_specs" | tr -d ' ')
    p2_count=$(wc -l < "$phase2_specs" | tr -d ' ')

    if [ "$both_count" -gt 0 ]; then
        echo "Specs in BOTH phases (acceptable overlap): $both_count"
        sed 's/^/  /' "$BOTH_FILE"
        echo ""
    fi

    if [ "$missing_count" -gt 0 ]; then
        echo "MISSING specs (in NEITHER phase): $missing_count"
        sed 's/^/  *** /' "$MISSING_FILE"
        echo ""
    fi

    echo "Total spec files: $all_spec_count"
    echo "Phase 1 covers: $p1_count specs"
    echo "Phase 2 covers: $p2_count specs"
    echo ""

    if [ "$missing_count" -eq 0 ]; then
        echo "RESULT: PASS - All spec files are covered by at least one phase."
    else
        echo "RESULT: FAIL - $missing_count spec file(s) would not run in either phase!"
        exit 1
    fi
}

# --- Main ---

MODULES=()
if [ "$1" = "--all" ]; then
    while IFS= read -r m; do
        MODULES+=("$m")
    done < <(jq -r '.mappings | keys[]' "$MAPPING_FILE")
else
    MODULES=("$@")
fi

echo "=== E2E Test Determination Verification ==="
echo "Modules: ${MODULES[*]}"
echo ""

RELEVANT_FILE="$TMPDIR_VERIFY/relevant.txt"
build_relevant_tests "$RELEVANT_FILE"

ALL_PATHS_FILE="$TMPDIR_VERIFY/all_paths.txt"
build_all_test_paths "$ALL_PATHS_FILE"

RELEVANT_TESTS=()
read_file_to_array "$RELEVANT_FILE" RELEVANT_TESTS

ALL_TEST_PATHS=()
read_file_to_array "$ALL_PATHS_FILE" ALL_TEST_PATHS

determine_remaining_tests

# Sort and deduplicate
RELEVANT_SORTED_FILE="$TMPDIR_VERIFY/relevant_sorted.txt"
REMAINING_SORTED_FILE="$TMPDIR_VERIFY/remaining_sorted.txt"
sort_unique_to_file "$RELEVANT_SORTED_FILE" "${RELEVANT_TESTS[@]}"
sort_unique_to_file "$REMAINING_SORTED_FILE" "${REMAINING_TESTS[@]}"

RELEVANT_COUNT=$(wc -l < "$RELEVANT_SORTED_FILE" | tr -d ' ')
REMAINING_COUNT=$(wc -l < "$REMAINING_SORTED_FILE" | tr -d ' ')

echo "--- Phase 1 (Relevant Tests): $RELEVANT_COUNT paths ---"
sed 's/^/  /' "$RELEVANT_SORTED_FILE"
echo ""
echo "--- Phase 2 (Remaining Tests): $REMAINING_COUNT paths ---"
if [ "$REMAINING_COUNT" -gt 0 ]; then
    sed 's/^/  /' "$REMAINING_SORTED_FILE"
else
    echo "  (none)"
fi
echo ""

echo "=== Coverage Validation ==="

PHASE1_SPECS_FILE="$TMPDIR_VERIFY/phase1_specs.txt"
expand_paths_to_specs "$RELEVANT_SORTED_FILE" "$PHASE1_SPECS_FILE"

PHASE2_SPECS_FILE="$TMPDIR_VERIFY/phase2_specs.txt"
expand_paths_to_specs "$REMAINING_SORTED_FILE" "$PHASE2_SPECS_FILE"

validate_coverage "$PHASE1_SPECS_FILE" "$PHASE2_SPECS_FILE"
