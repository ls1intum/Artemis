#!/bin/sh

# Create libs folder because the Artemis docker compose file expects the .war file there
mkdir -p build/libs
mv ./*.war build/libs/

# pass current host's hostname to the docker container for server.url (see docker compose config file)
export HOST_HOSTNAME=$(hostname)

# Load git history needed for analysis
git config remote.origin.fetch "+refs/heads/*:refs/remotes/origin/*"
git fetch --unshallow || git fetch --all

cd docker
docker build .. --build-arg WAR_FILE_STAGE=external_builder -f ./artemis/Dockerfile -t artemis:coverage-latest
docker compose -f cypress-E2E-tests.yml -f cypress-E2E-tests-coverage-override.yml pull nginx mysql
docker compose -f cypress-E2E-tests.yml -f cypress-E2E-tests-coverage-override.yml build --no-cache --pull artemis-cypress
#do not pull the base image artemis:coverage-latest for artemis-app as it's stored locally and built above
docker compose -f cypress-E2E-tests.yml -f cypress-E2E-tests-coverage-override.yml build --build-arg WAR_FILE_STAGE=external_builder --no-cache artemis-app
docker compose -f cypress-E2E-tests.yml -f cypress-E2E-tests-coverage-override.yml up --exit-code-from artemis-cypress
exitCode=$?
cd ..
echo "Cypress container exit code: $exitCode"
if [ $exitCode -eq 0 ]
then
    touch .successful
else
    echo "Not creating success file because the tests failed"
fi
