#!/bin/sh

# pass current host's hostname to the docker container for server.url (see docker compose config file)
export HOST_HOSTNAME=$(hostname)

docker-compose -f ./src/main/docker/cypress-E2E-tests.yml pull
docker-compose -f ./src/main/docker/cypress-E2E-tests.yml build --no-cache --pull
docker-compose -f ./src/main/docker/cypress-E2E-tests.yml up --exit-code-from artemis-cypress
exitCode=$?
echo "Cypress container exit code: $exitCode"
if [ $exitCode -eq 0 ]
then
    touch .successful
else
    echo "Not creating success file because the tests failed"
fi
