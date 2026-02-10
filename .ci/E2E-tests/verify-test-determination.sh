#!/usr/bin/env bash
# Verification script for e2e test determination logic.
# Simulates the determine-relevant-tests.sh logic for a given set of modules
# and validates that every spec file appears in at least one phase.
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

# Helper: add a line to a file if not already present
add_unique() {
    local file="$1"
    local value="$2"
    if ! grep -qxF "$value" "$file" 2>/dev/null; then
        echo "$value" >> "$file"
    fi
}

# Collect modules
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

# Build relevant test set from selected modules (using a file as a set)
RELEVANT_FILE="$TMPDIR_VERIFY/relevant.txt"
touch "$RELEVANT_FILE"

# Always-run tests
jq -r '.alwaysRunTests[]' "$MAPPING_FILE" 2>/dev/null | while IFS= read -r test; do
    [ -n "$test" ] && add_unique "$RELEVANT_FILE" "$test"
done

# Add test paths for each selected module
for module in "${MODULES[@]}"; do
    jq -r ".mappings[\"$module\"].testPaths[]" "$MAPPING_FILE" 2>/dev/null | while IFS= read -r test_path; do
        if [ -n "$test_path" ] && [ "$test_path" != "null" ]; then
            add_unique "$RELEVANT_FILE" "$test_path"
        fi
    done
done

# Build ALL_TEST_PATHS (using a file as a set)
ALL_PATHS_FILE="$TMPDIR_VERIFY/all_paths.txt"
touch "$ALL_PATHS_FILE"

jq -r '.allTestPaths[]' "$MAPPING_FILE" 2>/dev/null | while IFS= read -r test_path; do
    if [ -n "$test_path" ] && [ "$test_path" != "null" ]; then
        add_unique "$ALL_PATHS_FILE" "$test_path"
    fi
done

# Also discover from filesystem
(cd "$PLAYWRIGHT_DIR" && find e2e -maxdepth 1 -mindepth 1 \( -type d -o -name '*.spec.ts' \) -print) | while IFS= read -r path; do
    if [ -n "$path" ]; then
        if [ -d "$PLAYWRIGHT_DIR/$path" ]; then
            add_unique "$ALL_PATHS_FILE" "$path/"
        else
            add_unique "$ALL_PATHS_FILE" "$path"
        fi
    fi
done

# Read arrays from files
RELEVANT_TESTS=()
while IFS= read -r line; do
    [ -n "$line" ] && RELEVANT_TESTS+=("$line")
done < "$RELEVANT_FILE"

ALL_TEST_PATHS=()
while IFS= read -r line; do
    [ -n "$line" ] && ALL_TEST_PATHS+=("$line")
done < "$ALL_PATHS_FILE"

# Determine remaining tests (same logic as determine-relevant-tests.sh with the fix applied)
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
                # Fixed condition: only check exact match or relevant-is-parent-of-child
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

# Sort and deduplicate
RELEVANT_SORTED_FILE="$TMPDIR_VERIFY/relevant_sorted.txt"
REMAINING_SORTED_FILE="$TMPDIR_VERIFY/remaining_sorted.txt"

if [ ${#RELEVANT_TESTS[@]} -gt 0 ]; then
    printf '%s\n' "${RELEVANT_TESTS[@]}" | sort -u > "$RELEVANT_SORTED_FILE"
else
    touch "$RELEVANT_SORTED_FILE"
fi

if [ ${#REMAINING_TESTS[@]} -gt 0 ]; then
    printf '%s\n' "${REMAINING_TESTS[@]}" | sort -u > "$REMAINING_SORTED_FILE"
else
    touch "$REMAINING_SORTED_FILE"
fi

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

# Validation: check that every spec file appears in at least one phase
echo "=== Coverage Validation ==="

# Expand Phase 1 paths into individual spec files
PHASE1_SPECS_FILE="$TMPDIR_VERIFY/phase1_specs.txt"
touch "$PHASE1_SPECS_FILE"
while IFS= read -r path; do
    [ -z "$path" ] && continue
    if [[ "$path" == *.spec.ts ]]; then
        echo "$path" >> "$PHASE1_SPECS_FILE"
    elif [ -d "$PLAYWRIGHT_DIR/$path" ]; then
        (cd "$PLAYWRIGHT_DIR" && find "$path" -name '*.spec.ts' -print) >> "$PHASE1_SPECS_FILE"
    fi
done < "$RELEVANT_SORTED_FILE"
sort -u "$PHASE1_SPECS_FILE" -o "$PHASE1_SPECS_FILE"

# Expand Phase 2 paths into individual spec files
PHASE2_SPECS_FILE="$TMPDIR_VERIFY/phase2_specs.txt"
touch "$PHASE2_SPECS_FILE"
while IFS= read -r path; do
    [ -z "$path" ] && continue
    if [[ "$path" == *.spec.ts ]]; then
        echo "$path" >> "$PHASE2_SPECS_FILE"
    elif [ -d "$PLAYWRIGHT_DIR/$path" ]; then
        (cd "$PLAYWRIGHT_DIR" && find "$path" -name '*.spec.ts' -print) >> "$PHASE2_SPECS_FILE"
    fi
done < "$REMAINING_SORTED_FILE"
sort -u "$PHASE2_SPECS_FILE" -o "$PHASE2_SPECS_FILE"

# Get all actual spec files
ALL_SPECS_FILE="$TMPDIR_VERIFY/all_specs.txt"
(cd "$PLAYWRIGHT_DIR" && find e2e -name '*.spec.ts' -print | sort) > "$ALL_SPECS_FILE"

# Find specs in both phases (overlap)
BOTH_FILE="$TMPDIR_VERIFY/both.txt"
comm -12 "$PHASE1_SPECS_FILE" "$PHASE2_SPECS_FILE" > "$BOTH_FILE"

# Find specs in neither phase (missing)
COVERED_FILE="$TMPDIR_VERIFY/covered.txt"
sort -u "$PHASE1_SPECS_FILE" "$PHASE2_SPECS_FILE" > "$COVERED_FILE"
MISSING_FILE="$TMPDIR_VERIFY/missing.txt"
comm -23 "$ALL_SPECS_FILE" "$COVERED_FILE" > "$MISSING_FILE"

BOTH_COUNT=$(wc -l < "$BOTH_FILE" | tr -d ' ')
MISSING_COUNT=$(wc -l < "$MISSING_FILE" | tr -d ' ')
ALL_SPEC_COUNT=$(wc -l < "$ALL_SPECS_FILE" | tr -d ' ')
P1_SPEC_COUNT=$(wc -l < "$PHASE1_SPECS_FILE" | tr -d ' ')
P2_SPEC_COUNT=$(wc -l < "$PHASE2_SPECS_FILE" | tr -d ' ')

if [ "$BOTH_COUNT" -gt 0 ]; then
    echo "Specs in BOTH phases (acceptable overlap): $BOTH_COUNT"
    sed 's/^/  /' "$BOTH_FILE"
    echo ""
fi

if [ "$MISSING_COUNT" -gt 0 ]; then
    echo "MISSING specs (in NEITHER phase): $MISSING_COUNT"
    sed 's/^/  *** /' "$MISSING_FILE"
    echo ""
fi

echo "Total spec files: $ALL_SPEC_COUNT"
echo "Phase 1 covers: $P1_SPEC_COUNT specs"
echo "Phase 2 covers: $P2_SPEC_COUNT specs"
echo ""

if [ "$MISSING_COUNT" -eq 0 ]; then
    echo "RESULT: PASS - All spec files are covered by at least one phase."
else
    echo "RESULT: FAIL - $MISSING_COUNT spec file(s) would not run in either phase!"
    exit 1
fi
