#!/bin/sh

# Create libs folder because the Artemis docker compose file expects the .war file there
mkdir -p build/libs
mv ./*.war build/libs/

# pass current host's hostname to the docker container for server.url (see docker compose config file)
export HOST_HOSTNAME=$(hostname)

# docker compose pull cmds: just pull everything else than artemis-app as we build it later either way
# for backwards compatibility we check for old docker compose files
CypressDockerComposeFilePath=""
if [ -f docker/cypress-E2E-tests.yml ]; then
    CypressDockerComposeFilePath="docker/cypress-E2E-tests.yml"
    docker compose -f $CypressDockerComposeFilePath pull artemis-cypress mysql nginx
    docker compose -f $CypressDockerComposeFilePath build --build-arg WAR_FILE_STAGE=external_builder --no-cache --pull artemis-app
elif [ -f src/main/docker/cypress/cypress-E2E-tests.yml ]; then
    CypressDockerComposeFilePath="src/main/docker/cypress/cypress-E2E-tests.yml"
    docker compose -f $CypressDockerComposeFilePath pull artemis-cypress mysql artemis-nginx
    docker compose -f $CypressDockerComposeFilePath build --no-cache --pull artemis-app
fi
docker compose -f $CypressDockerComposeFilePath up --exit-code-from artemis-cypress
exitCode=$?
echo "Cypress container exit code: $exitCode"
if [ $exitCode -eq 0 ]
then
    touch .successful
else
    echo "Not creating success file because the tests failed"
fi
