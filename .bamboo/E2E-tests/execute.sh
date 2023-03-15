#!/bin/sh

# Create libs folder because the Artemis docker compose file expects the .war file there
mkdir -p build/libs
mv ./*.war build/libs/

# Start Artemis docker containers with docker-compose
cd src/main/docker/cypress

# pass current host's hostname to the docker container for server.url (see docker compose config file)
export HOST_HOSTNAME=$(hostname)

docker compose -f cypress-E2E-tests.yml pull
docker compose -f cypress-E2E-tests.yml build --no-cache --pull
docker compose -f cypress-E2E-tests.yml up --exit-code-from artemis-cypress
exitCode=$?
echo "Cypress container exit code: $exitCode"
if [ $exitCode -eq 0 ]
then
    touch ../../../../.successful
else
    echo "Not creating success file because the tests failed"
fi
