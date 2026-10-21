#!/bin/sh

INFRASTRUCTURE_FAILED=true
# Called indirectly by the EXIT trap on every path, including image/setup failures.
# shellcheck disable=SC2329
report_infrastructure_verdict() {
    if [ -n "${GITHUB_OUTPUT:-}" ]; then
        echo "infrastructure_failed=$INFRASTRUCTURE_FAILED" >> "$GITHUB_OUTPUT"
    fi
}
trap report_infrastructure_verdict 0

rm -f .successful src/test/playwright/test-reports/runner-status.env

CONFIGURATION=$1
# $2 (test framework) is part of the positional CLI contract but is currently unused in this script.
# shellcheck disable=SC2034
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
  elif [ "$CONFIGURATION" = "multi-node-redis" ]; then
    echo "Running for playwright (multi-node, Redis distributed data provider)"
    # Two files, spelled as the argument list `docker compose -f $COMPOSE_FILE` splits them into: the Redis file layers
    # the Redis container and the per-node provider settings over the Hazelcast stack.
    COMPOSE_FILE="playwright-E2E-tests-multi-node.yml -f playwright-E2E-tests-multi-node-redis.yml"
    # ClusterFormation.spec.ts reads this to decide which node-identity shape it may assert.
    export DISTRIBUTED_DATA_PROVIDER="redis"
  else
      echo "Invalid configuration. Please choose among postgres, postgres-localci, multi-node or multi-node-redis."
      exit 1
fi

echo "Compose file:"
echo "$COMPOSE_FILE"

# pass current host's hostname to the docker container for server.url (see docker compose config file)
export HOST_HOSTNAME="nginx"

# The prod-profile stacks need a JWT signing key. A key committed to the repository would be one anyone can use to forge
# a token, so it is generated per run and shared by every service that reads deployment/docker/artemis/config/playwright.env.
export ARTEMIS_E2E_JWT_SECRET="${ARTEMIS_E2E_JWT_SECRET:-$(openssl rand -base64 64 | tr -d '\n')}"

# Export test paths for docker compose to pick up
if [ -n "$TEST_PATHS" ]; then
    export PLAYWRIGHT_TEST_PATHS="$TEST_PATHS"
    echo "Running filtered tests: $TEST_PATHS"
else
    export PLAYWRIGHT_TEST_PATHS=""
    echo "Running all tests"
fi

cd deployment/docker || { echo "ERROR: Failed to change to docker directory" >&2; exit 1; }

# Clean up stale reporter-failed marker from previous runs (self-hosted runners have persistent workspaces)
rm -f ../../src/test/playwright/test-reports/.reporter-failed

# Pull the images to avoid using outdated images
# --env-file ../../.env is needed because .env lives in the project root but we cd'd into deployment/docker/
# COMPOSE_FILE intentionally contains both -f arguments for the Redis overlay.
# shellcheck disable=SC2086
docker compose --env-file ../../.env -f $COMPOSE_FILE pull --quiet --policy always || exit $?

# A compose stack must serve the revision whose tests are being run. Local runs without an expected SHA skip this check.
if [ -n "${ARTEMIS_EXPECTED_COMMIT_SHA:-}" ] || [ "${GITHUB_ACTIONS:-}" = "true" ]; then
    bash ../../supporting_scripts/ci/E2E-tests/verify-image-revision.sh || exit $?
fi
# Run the tests
# COMPOSE_FILE intentionally contains both -f arguments for the Redis overlay.
# shellcheck disable=SC2086
docker compose --env-file ../../.env -f $COMPOSE_FILE up --exit-code-from artemis-playwright

exitCode=$?
cd ../..
echo "Container exit code: $exitCode"

# Never infer execution health from JUnit: a partial report can contain only known flaky failures.
# Compare exact text instead of sourcing a file from the container into the host shell.
RUNNER_STATUS="src/test/playwright/test-reports/runner-status.env"
if [ -f "$RUNNER_STATUS" ] && [ "$(cat "$RUNNER_STATUS")" = "$(printf 'exit_code=%s\ninfrastructure_failed=false' "$exitCode")" ]; then
    INFRASTRUCTURE_FAILED=false
elif [ "$exitCode" -eq 0 ]; then
    echo "ERROR: Container succeeded without a complete runner verdict."
    exitCode=1
fi

# Report optional report-generation failures without altering the container exit code.
REPORTER_MARKER="src/test/playwright/test-reports/.reporter-failed"
if [ -f "$REPORTER_MARKER" ]; then
    echo "WARNING: Optional reporter failure detected:"
    cat "$REPORTER_MARKER"
    if [ -n "$GITHUB_OUTPUT" ]; then
        echo "reporter_failed=true" >> "$GITHUB_OUTPUT"
    fi
    rm -f "$REPORTER_MARKER"
fi

if [ $exitCode -eq 0 ]
then
    touch .successful
else
    echo "Not creating success file because the tests failed"
fi

exit $exitCode
