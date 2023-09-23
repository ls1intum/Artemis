#!/bin/sh

DB=$1

if [ "$DB" = "mysql" ]; then
  COMPOSE_FILE="cypress-E2E-tests-mysql.yml"
elif [ "$DB" = "postgres" ]; then
  COMPOSE_FILE="cypress-E2E-tests-postgres.yml"
else
  echo "Invalid database type. Please choose either mysql or postgres."
  exit 1
fi

# Create libs folder because the Artemis docker compose file expects the .war file there
mkdir -p build/libs
mv ./*.war build/libs/

# pass current host's hostname to the docker container for server.url (see docker compose config file)
export HOST_HOSTNAME=$(hostname)

cd docker
#just pull everything else than artemis-app as we build it later either way
docker compose -f $COMPOSE_FILE pull artemis-cypress $DB nginx
docker compose -f $COMPOSE_FILE build --build-arg WAR_FILE_STAGE=external_builder --no-cache --pull artemis-app
docker compose -f $COMPOSE_FILE up --exit-code-from artemis-cypress
