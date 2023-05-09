#!/bin/sh

# Create libs folder because the Artemis docker compose file expects the .war file there
mkdir -p build/libs
mv ./*.war build/libs/

# pass current host's hostname to the docker container for server.url (see docker compose config file)
export HOST_HOSTNAME=$(hostname)

cd docker
#just pull everything else than artemis-app as we build it later either way
docker compose -f cypress-E2E-tests-mysql.yml pull artemis-cypress mysql nginx
docker compose -f cypress-E2E-tests-mysql.yml build --build-arg WAR_FILE_STAGE=external_builder --no-cache --pull artemis-app
docker compose -f cypress-E2E-tests-mysql.yml up --exit-code-from artemis-cypress
exitCode=$?
cd ..
echo "Cypress container exit code: $exitCode"
if [ $exitCode -eq 0 ]
then
    touch .successful
else
    echo "Not creating success file because the tests failed"
fi
