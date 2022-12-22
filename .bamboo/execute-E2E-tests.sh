#!/bin/sh

# pass current host's hostname to the docker container for server.url (see docker compose config file)
export HOST_HOSTNAME=$(hostname)

docker-compose --env-file .src/main/docker/cypress/bamboo-E2E-testing.env pull
docker-compose --env-file .src/main/docker/cypress/bamboo-E2E-testing.env build --no-cache --pull
docker-compose --env-file .src/main/docker/cypress/bamboo-E2E-testing.env up --exit-code-from artemis-cypress
exitCode=$?
echo "Cypress container exit code: $exitCode"
if [ $exitCode -eq 0 ]
then
    touch ../../../../.successful
else
    echo "Not creating success file because the tests failed"
fi
