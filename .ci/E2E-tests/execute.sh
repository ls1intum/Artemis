#!/bin/sh

CONFIGURATION=$1
TEST_FRAMEWORK=$2
DB="mysql"

echo "CONFIGURATION:"
echo "$CONFIGURATION"

if [ "$CONFIGURATION" = "mysql" ]; then
    COMPOSE_FILE="playwright-E2E-tests-mysql.yml"
  elif [ "$CONFIGURATION" = "postgres" ]; then
    COMPOSE_FILE="playwright-E2E-tests-postgres.yml"
    DB="postgres"
  elif [ "$CONFIGURATION" = "mysql-localci" ]; then
    COMPOSE_FILE="playwright-E2E-tests-mysql-localci.yml"
  elif [ "$CONFIGURATION" = "multi-node" ]; then
    COMPOSE_FILE="playwright-E2E-tests-multi-node.yml"
  else
      echo "Invalid configuration. Please choose among mysql, postgres, mysql-localci or multi-node."
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
if [ "$CONFIGURATION" = "multi-node" ]; then
    echo "Building for playwright (multi-node)"
    docker compose -f $COMPOSE_FILE pull artemis-playwright $DB nginx
    docker compose -f $COMPOSE_FILE build --build-arg WAR_FILE_STAGE=external_builder --no-cache --pull artemis-app-node-1 artemis-app-node-2 artemis-app-node-3
    docker compose -f $COMPOSE_FILE up --exit-code-from artemis-playwright
  else
    echo "Building for playwright"
    docker compose -f $COMPOSE_FILE pull artemis-playwright $DB nginx
    docker compose -f $COMPOSE_FILE build --build-arg WAR_FILE_STAGE=external_builder --no-cache --pull artemis-app
    docker compose -f $COMPOSE_FILE up --exit-code-from artemis-playwright
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
