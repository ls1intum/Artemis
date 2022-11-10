#!/bin/sh

# TODO: integrate this into bamboo instead of using an inline script

# Create libs folder because the Artemis docker compose file expects the .war file there
mkdir -p build/libs
mv *.war build/libs/

# Start Artemis docker containers with docker-compose
cd src/main/docker/cypress

#TODO: recheck docker compose version and do it in one command
docker-compose --env-file ./bamboo-E2E-testing.env pull
docker-compose --env-file ./bamboo-E2E-testing.env build --no-cache --pull
docker-compose --env-file ./bamboo-E2E-testing.env up --exit-code-from artemis-cypress
exitCode=$?
echo "Cypress container exit code: $exitCode"
if [ $exitCode -eq 0 ]
then
    touch ../../../../.successful
else
    echo "Not creating success file because the tests failed"
fi
