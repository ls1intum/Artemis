#!/bin/sh

CONFIGURATION=$1
TEST_FRAMEWORK=$2
TEST_PATHS=$3  # Optional: space-separated list of test paths to run (passed through as-is, e.g., "e2e/exam/ e2e/atlas/")

echo "CONFIGURATION:"
echo "$CONFIGURATION"

# workflow_run uses the workflow definition from the default branch, so
# in-flight PRs can still be invoked with the legacy mysql-localci argument.
if [ "$CONFIGURATION" = "mysql-localci" ]; then
    echo "Compatibility mode: mapping mysql-localci to postgres-localci"
    CONFIGURATION="postgres-localci"
fi

if [ "$CONFIGURATION" = "postgres" ]; then
    COMPOSE_FILE="playwright-E2E-tests-postgres.yml"
  elif [ "$CONFIGURATION" = "postgres-localci" ]; then
    echo "Running for playwright (single node) with postgres-localci"
    COMPOSE_FILE="playwright-E2E-tests-postgres-localci.yml"
  elif [ "$CONFIGURATION" = "multi-node" ]; then
    echo "Running for playwright (multi-node)"
    COMPOSE_FILE="playwright-E2E-tests-multi-node.yml"
  else
      echo "Invalid configuration. Please choose among postgres, postgres-localci or multi-node."
      exit 1
fi

echo "Compose file:"
echo $COMPOSE_FILE

# pass current host's hostname to the docker container for server.url (see docker compose config file)
export HOST_HOSTNAME="nginx"

# Export test paths for docker compose to pick up
if [ -n "$TEST_PATHS" ]; then
    export PLAYWRIGHT_TEST_PATHS="$TEST_PATHS"
    echo "Running filtered tests: $TEST_PATHS"
else
    export PLAYWRIGHT_TEST_PATHS=""
    echo "Running all tests"
fi

cd docker || { echo "ERROR: Failed to change to docker directory" >&2; exit 1; }

# Clean up stale reporter-failed marker from previous runs (self-hosted runners have persistent workspaces)
rm -f ../src/test/playwright/test-reports/.reporter-failed

# Pull the images to avoid using outdated images
# --env-file ../.env is needed because .env lives in the project root but we cd'd into docker/
docker compose --env-file ../.env -f $COMPOSE_FILE pull --quiet --policy always
# Run the tests
docker compose --env-file ../.env -f $COMPOSE_FILE up --exit-code-from artemis-playwright

exitCode=$?
cd ..
echo "Container exit code: $exitCode"

# Check for reporter failure marker (e.g., monocart OOM that didn't affect test results)
REPORTER_MARKER="src/test/playwright/test-reports/.reporter-failed"
if [ -f "$REPORTER_MARKER" ]; then
    echo "WARNING: Reporter failure detected (tests still passed):"
    cat "$REPORTER_MARKER"
    if [ -n "$GITHUB_OUTPUT" ]; then
        echo "reporter_failed=true" >> "$GITHUB_OUTPUT"
    fi
    rm -f "$REPORTER_MARKER"
    # Tests passed but reporter failed — treat as success
    exitCode=0
fi

if [ $exitCode -eq 0 ]
then
    touch .successful
else
    echo "Not creating success file because the tests failed"
fi

exit $exitCode
