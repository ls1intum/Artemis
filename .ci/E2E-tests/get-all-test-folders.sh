#!/bin/bash

# Returns all e2e test folders and root-level test files, excluding certain folders like init, fixtures, support
# Returns a space-separated list of test folder/file paths.

E2E_BASE_PATH="src/test/playwright/e2e"

# Find all directories in e2e folder, excluding certain ones
ALL_DIRS=$(find "$E2E_BASE_PATH" -mindepth 1 -maxdepth 1 -type d ! -name "init" ! -name "fixtures" ! -name "support" | sort)

# Find root-level test files (e.g., Login.spec.ts, Logout.spec.ts, SystemHealth.spec.ts)
ROOT_TESTS=$(find "$E2E_BASE_PATH" -maxdepth 1 -type f -name "*.spec.ts" | sort)

# Combine and output as space-separated list
ALL_TESTS=$(echo "$ALL_DIRS $ROOT_TESTS" | tr '\n' ' ' | sed 's/  */ /g' | sed 's/^ *//' | sed 's/ *$//')

echo "$ALL_TESTS"
