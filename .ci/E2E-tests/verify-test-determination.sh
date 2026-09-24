#!/usr/bin/env bash
# Check mapped paths and spec coverage with the selector used by CI.
set -euo pipefail

if [ "${1:-}" != "--all" ] || [ "$#" -ne 1 ]; then
    echo "Usage: $0 --all" >&2
    exit 2
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
MAPPED_PATHS=$(jq -r '[.allTestPaths[], .alwaysRunTests[], (.mappings[].testPaths[])] | unique[]' "$SCRIPT_DIR/e2e-test-mapping.json")
while IFS= read -r path; do
    if [ ! -e "$REPO_ROOT/src/test/playwright/$path" ]; then
        echo "Mapped E2E path does not exist: $path" >&2
        exit 1
    fi
done <<< "$MAPPED_PATHS"

TMP_REPO="$(mktemp -d)"
trap 'rm -rf "$TMP_REPO"' EXIT

mkdir -p "$TMP_REPO/.ci/E2E-tests" "$TMP_REPO/src/test/playwright"
cp "$SCRIPT_DIR/determine-relevant-tests.sh" "$SCRIPT_DIR/e2e-test-mapping.json" "$TMP_REPO/.ci/E2E-tests/"
(
    cd "$REPO_ROOT/src/test/playwright"
    find e2e -name '*.spec.ts' -print
) | while IFS= read -r spec; do
    mkdir -p "$TMP_REPO/src/test/playwright/$(dirname "$spec")"
    touch "$TMP_REPO/src/test/playwright/$spec"
done

(
    cd "$TMP_REPO"
    git init -q
    git config user.name 'E2E coverage check'
    git config user.email 'e2e-coverage@example.invalid'
    git config commit.gpgsign false
    git add .
    git commit -qm base
    git branch base
    touch coverage-probe
    git add coverage-probe
    git commit -qm probe
    GITHUB_OUTPUT="$TMP_REPO/selection" bash .ci/E2E-tests/determine-relevant-tests.sh base > "$TMP_REPO/selector.log"
)

for phase in RELEVANT_TESTS REMAINING_TESTS; do
    paths="$(sed -n "s/^${phase}=//p" "$TMP_REPO/selection")"
    for path in $paths; do
        if [ -d "$TMP_REPO/src/test/playwright/$path" ]; then
            (cd "$TMP_REPO/src/test/playwright" && find "$path" -name '*.spec.ts' -print)
        elif [ -f "$TMP_REPO/src/test/playwright/$path" ]; then
            printf '%s\n' "$path"
        fi
    done
done | sort -u > "$TMP_REPO/covered"
(
    cd "$REPO_ROOT/src/test/playwright"
    find e2e -name '*.spec.ts' -print | sort
) > "$TMP_REPO/all"
comm -23 "$TMP_REPO/all" "$TMP_REPO/covered" > "$TMP_REPO/missing"
if [ -s "$TMP_REPO/missing" ]; then
    echo 'Specs in neither E2E phase:' >&2
    cat "$TMP_REPO/missing" >&2
    exit 1
fi
printf 'All %s E2E specs are covered by the real selector.\n' "$(wc -l < "$TMP_REPO/all" | tr -d ' ')"
