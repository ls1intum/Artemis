#!/bin/sh

# pass current host's hostname to the docker container for server.url (see docker compose config file)
export HOST_HOSTNAME=$(hostname)

# just pull everything else than artemis-app as we build it later either way
docker-compose -f ./src/main/docker/cypress/cypress-E2E-tests.yml pull artemis-cypress mysql artemis-nginx
docker-compose -f ./src/main/docker/cypress/cypress-E2E-tests.yml build artemis-app --no-cache --pull
docker-compose -f ./src/main/docker/cypress/cypress-E2E-tests.yml up --exit-code-from artemis-cypress
exitCode=$?
echo "Cypress container exit code: $exitCode"
if [ $exitCode -eq 0 ]
then
    touch .successful
else
    echo "Not creating success file because the tests failed"
fi
