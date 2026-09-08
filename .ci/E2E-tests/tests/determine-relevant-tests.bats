#!/usr/bin/env bats

setup() {
    export GIT_CONFIG_NOSYSTEM=1 GIT_CONFIG_GLOBAL=/dev/null
    REPO="$BATS_TEST_TMPDIR/repo"
    mkdir -p "$REPO/.ci/E2E-tests" "$REPO/src/test/playwright/e2e"
    cp "$BATS_TEST_DIRNAME/../determine-relevant-tests.sh" "$BATS_TEST_DIRNAME/../e2e-test-mapping.json" "$REPO/.ci/E2E-tests/"
    cd "$REPO"
    git init -q -b feature
    git config user.name 'E2E selector test'
    git config user.email 'e2e-selector@example.invalid'
    git config commit.gpgsign false
    git add .
    git commit -qm base
    git branch base
    touch src/test/playwright/e2e/First.spec.ts
    git add .
    git commit -qm first
    touch src/test/playwright/e2e/Second.spec.ts
    git add .
    git commit -qm second
    export GITHUB_OUTPUT="$BATS_TEST_TMPDIR/output"
    touch "$GITHUB_OUTPUT"
}

@test "selects every committed spec since the requested base" {
    touch src/test/playwright/e2e/Uncommitted.spec.ts
    run bash .ci/E2E-tests/determine-relevant-tests.sh base
    [ "$status" -eq 0 ]
    grep -qx 'RUN_ALL_TESTS=false' "$GITHUB_OUTPUT"
    grep -q '^RELEVANT_TESTS=.*e2e/First.spec.ts.*e2e/Second.spec.ts' "$GITHUB_OUTPUT"
    ! grep -q 'Uncommitted.spec.ts' "$GITHUB_OUTPUT"
    grep -qx 'REMAINING_TESTS=' "$GITHUB_OUTPUT"
}

@test "runs all tests when the requested base has no committed changes" {
    run bash .ci/E2E-tests/determine-relevant-tests.sh HEAD
    [ "$status" -eq 0 ]
    grep -qx 'RUN_ALL_TESTS=true' "$GITHUB_OUTPUT"
}

@test "rejects a base it cannot compare against rather than selecting the last commit" {
    run bash .ci/E2E-tests/determine-relevant-tests.sh missing-base
    [ "$status" -ne 0 ]
    [[ "$output" == *"ERROR: Cannot compare 'missing-base' with HEAD."* ]]
    [ ! -s "$GITHUB_OUTPUT" ]
}

# The case the removed HEAD~1 fallback used to hide: a shallow checkout has no merge base, so the
# selection silently covered one commit instead of the branch.
@test "rejects shallow history without a merge base" {
    git clone -q --depth 1 "file://$REPO" "$BATS_TEST_TMPDIR/shallow"
    cd "$BATS_TEST_TMPDIR/shallow"
    git fetch -q --depth 1 origin base:base
    run bash .ci/E2E-tests/determine-relevant-tests.sh base
    [ "$status" -ne 0 ]
    [[ "$output" == *"ERROR: Cannot compare 'base' with HEAD."* ]]
    [ ! -s "$GITHUB_OUTPUT" ]
}

# `jq empty`, the obvious guard, accepts all of these and would select tests from a mapping that
# carries no rules at all.
@test "requires the mapping to be exactly one JSON object" {
    for mapping in '{' '' '{} {}' 'null' '[]' '"mapping"' '1' 'true'; do
        printf '%s' "$mapping" > .ci/E2E-tests/e2e-test-mapping.json
        run bash .ci/E2E-tests/determine-relevant-tests.sh base
        [ "$status" -ne 0 ]
        [[ "$output" == *"ERROR: Invalid JSON mapping"* ]]
        [ ! -s "$GITHUB_OUTPUT" ]
    done
}
