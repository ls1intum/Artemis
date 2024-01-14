#!/bin/sh

PIPELINE=$1
TEST_FRAMEWORK=$2

echo "Pipeline:"
echo "$PIPELINE"
echo "Test framework:"
echo "$TEST_FRAMEWORK"

if [ "$TEST_FRAMEWORK" = "playwright" ]; then
  if [ "$PIPELINE" = "mysql" ]; then
    COMPOSE_FILE="playwright-E2E-tests-mysql.yml"
  elif [ "$PIPELINE" = "local" ]; then
    COMPOSE_FILE="playwright-E2E-tests-local.yml"
  else
      echo "Invalid database type. Please choose among mysql, postgres or local."
      exit 1
  fi
elif [ "$TEST_FRAMEWORK" = "playwright" ]; then
  if [ "$PIPELINE" = "mysql" ]; then
    COMPOSE_FILE="cypress-E2E-tests-mysql.yml"
  elif [ "$PIPELINE" = "postgres" ]; then
    COMPOSE_FILE="cypress-E2E-tests-postgres.yml"
  elif [ "$PIPELINE" = "local" ]; then
    COMPOSE_FILE="cypress-E2E-tests-local.yml"
  else
    echo "Invalid database type. Please choose among mysql, postgres or local."
    exit 1
  fi
else
    echo "Invalid test framework. Please choose either cypress or playwright."
    exit 1
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
  echo "artemis-playwright pull"
  docker compose -f $COMPOSE_FILE pull artemis-playwright $PIPELINE nginx
  echo "artemis-playwright build"
  docker compose -f $COMPOSE_FILE build --build-arg WAR_FILE_STAGE=external_builder --no-cache --pull artemis-app
  echo "artemis-playwright up"
  docker compose -f $COMPOSE_FILE up --exit-code-from artemis-playwright
else
  echo "Building for cypress"
  docker compose -f $COMPOSE_FILE pull artemis-cypress $PIPELINE nginx
  docker compose -f $COMPOSE_FILE build --build-arg WAR_FILE_STAGE=external_builder --no-cache --pull artemis-app
  docker compose -f $COMPOSE_FILE up --exit-code-from artemis-cypress
fi
exitCode=$?
cd ..
echo "Cypress container exit code: $exitCode"
if [ $exitCode -eq 0 ]
then
    touch .successful
else
    echo "Not creating success file because the tests failed"
fi
