#!/bin/sh

CONFIGURATION=$1
TEST_FRAMEWORK=$2

echo "CONFIGURATION:"
echo "$CONFIGURATION"
echo "Test framework:"
echo "$TEST_FRAMEWORK"

if [ "$TEST_FRAMEWORK" = "playwright" ]; then
  if [ "$CONFIGURATION" = "mysql" ]; then
    COMPOSE_FILE="playwright-E2E-tests-mysql.yml"
  elif [ "$CONFIGURATION" = "postgres" ]; then
    COMPOSE_FILE="playwright-E2E-tests-postgres.yml"
  elif [ "$CONFIGURATION" = "mysql-localci" ]; then
    COMPOSE_FILE="playwright-E2E-tests-mysql-localci.yml"
  else
      echo "Invalid configuration. Please choose among mysql, postgres or mysql-localci."
      exit 1
  fi
else
  if [ "$CONFIGURATION" = "mysql" ]; then
    COMPOSE_FILE="cypress-E2E-tests-mysql.yml"
  elif [ "$CONFIGURATION" = "postgres" ]; then
    COMPOSE_FILE="cypress-E2E-tests-postgres.yml"
  elif [ "$CONFIGURATION" = "local" ]; then
    COMPOSE_FILE="cypress-E2E-tests-local.yml"
  else
    echo "Invalid configuration. Please choose among mysql, postgres or local."
    exit 1
  fi
fi

echo "Compose file:"
echo $COMPOSE_FILE

# Create libs folder because the Artemis docker compose file expects the .war file there
mkdir -p build/libs
mv ./*.war build/libs/

# pass current host's hostname to the docker container for server.url (see docker compose config file)
export HOST_HOSTNAME=$(hostname)

cd docker
#just pull everything else than artemis-app as we build it later either way
if [ "$TEST_FRAMEWORK" = "playwright" ]; then
  echo "Building for playwright"
  docker compose -f $COMPOSE_FILE pull artemis-playwright $CONFIGURATION nginx
  docker compose -f $COMPOSE_FILE build --build-arg WAR_FILE_STAGE=external_builder --no-cache --pull artemis-app
  docker compose -f $COMPOSE_FILE up --exit-code-from artemis-playwright
else
  echo "Building for cypress"
  docker compose -f $COMPOSE_FILE pull artemis-cypress $CONFIGURATION nginx
  docker compose -f $COMPOSE_FILE build --build-arg WAR_FILE_STAGE=external_builder --no-cache --pull artemis-app
  docker compose -f $COMPOSE_FILE up --exit-code-from artemis-cypress
fi
exitCode=$?
cd ..
echo "Container exit code: $exitCode"
if [ $exitCode -eq 0 ]
then
    touch .successful
else
    echo "Not creating success file because the tests failed"
fi
