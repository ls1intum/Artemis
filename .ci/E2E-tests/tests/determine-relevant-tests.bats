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

@test "rejects a missing base rather than selecting only the last commit" {
    run bash .ci/E2E-tests/determine-relevant-tests.sh missing-base
    [ "$status" -ne 0 ]
    [[ "$output" == *"ERROR: Cannot compare 'missing-base' with HEAD."* ]]
    [ ! -s "$GITHUB_OUTPUT" ]
}

@test "rejects a base with unrelated history" {
    unrelated=$(git commit-tree -m unrelated "$(git mktree < /dev/null)")
    git branch unrelated "$unrelated"
    run bash .ci/E2E-tests/determine-relevant-tests.sh unrelated
    [ "$status" -ne 0 ]
    [[ "$output" == *"ERROR: Cannot compare 'unrelated' with HEAD."* ]]
    [ ! -s "$GITHUB_OUTPUT" ]
}

@test "rejects shallow history without a merge base" {
    git clone -q --depth 1 "file://$REPO" "$BATS_TEST_TMPDIR/shallow"
    cd "$BATS_TEST_TMPDIR/shallow"
    git fetch -q --depth 1 origin base:base
    run bash .ci/E2E-tests/determine-relevant-tests.sh base
    [ "$status" -ne 0 ]
    [[ "$output" == *"ERROR: Cannot compare 'base' with HEAD."* ]]
    [ ! -s "$GITHUB_OUTPUT" ]
}

@test "covers the exercise split-panel spec in the remaining or relevant phase" {
    spec=e2e/exercise/ExerciseSplitPanelResize.spec.ts
    mkdir -p src/test/playwright/e2e/exercise
    cp "$BATS_TEST_DIRNAME/../../../src/test/playwright/$spec" "src/test/playwright/$spec"
    git add .
    git commit -qm 'split-panel spec'
    git branch selection-base
    touch README.md
    git add .
    git commit -qm documentation

    run bash .ci/E2E-tests/determine-relevant-tests.sh selection-base
    [ "$status" -eq 0 ]
    grep -qx 'RUN_ALL_TESTS=false' "$GITHUB_OUTPUT"
    grep -E "^REMAINING_TESTS=(.* )?${spec//./\\.}( |$)" "$GITHUB_OUTPUT"

    mkdir -p src/main/webapp/app/exercise
    touch src/main/webapp/app/exercise/change.ts
    git add .
    git commit -qm exercise
    : > "$GITHUB_OUTPUT"
    run bash .ci/E2E-tests/determine-relevant-tests.sh selection-base
    [ "$status" -eq 0 ]
    grep -qx 'RUN_ALL_TESTS=false' "$GITHUB_OUTPUT"
    grep -E "^RELEVANT_TESTS=(.* )?${spec//./\\.}( |$)" "$GITHUB_OUTPUT"
    ! grep -E "^REMAINING_TESTS=(.* )?${spec//./\\.}( |$)" "$GITHUB_OUTPUT"
}

@test "reports missing jq without emitting a selection" {
    mkdir "$BATS_TEST_TMPDIR/bin"
    ln -s "$(command -v dirname)" "$BATS_TEST_TMPDIR/bin/dirname"
    PATH="$BATS_TEST_TMPDIR/bin" run "$BASH" .ci/E2E-tests/determine-relevant-tests.sh base
    [ "$status" -ne 0 ]
    [[ "$output" == *"ERROR: jq is required"* ]]
    [ ! -s "$GITHUB_OUTPUT" ]
}

@test "rejects malformed mapping JSON without emitting a selection" {
    printf '{' > .ci/E2E-tests/e2e-test-mapping.json
    run bash .ci/E2E-tests/determine-relevant-tests.sh base
    [ "$status" -ne 0 ]
    [[ "$output" == *"ERROR: Invalid JSON"* ]]
    [ ! -s "$GITHUB_OUTPUT" ]
}
