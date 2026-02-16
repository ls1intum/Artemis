#!/bin/sh

CONFIGURATION=$1
TEST_FRAMEWORK=$2
TEST_PATHS=$3  # Optional: space-separated list of test paths to run (passed through as-is, e.g., "e2e/exam/ e2e/atlas/")
DB="mysql"

echo "CONFIGURATION:"
echo "$CONFIGURATION"

if [ "$CONFIGURATION" = "mysql" ]; then
    COMPOSE_FILE="playwright-E2E-tests-mysql.yml"
  elif [ "$CONFIGURATION" = "postgres" ]; then
    COMPOSE_FILE="playwright-E2E-tests-postgres.yml"
    DB="postgres"
  elif [ "$CONFIGURATION" = "mysql-localci" ]; then
    echo "Running for playwright (single node) with mysql-localci"
    COMPOSE_FILE="playwright-E2E-tests-mysql-localci.yml"
  elif [ "$CONFIGURATION" = "multi-node" ]; then
    echo "Running for playwright (multi-node)"
    COMPOSE_FILE="playwright-E2E-tests-multi-node.yml"
  else
      echo "Invalid configuration. Please choose among mysql, postgres, mysql-localci or multi-node."
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

# Pull the images to avoid using outdated images
docker compose -f $COMPOSE_FILE pull --quiet --policy always
# Run the tests
docker compose -f $COMPOSE_FILE up --exit-code-from artemis-playwright

exitCode=$?
cd ..
echo "Container exit code: $exitCode"
if [ $exitCode -eq 0 ]
then
    touch .successful
else
    echo "Not creating success file because the tests failed"
fi

exit $exitCode
