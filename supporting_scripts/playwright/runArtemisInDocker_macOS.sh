#!/bin/sh

set -e

cd ../..

# configure application and start

if [ ! -f src/main/resources/config/application-local.yml ]; then
    cp src/main/resources/config/application-local-template.yml src/main/resources/config/application-local.yml
fi

echo "Copied configuration"

cd docker
open -a Docker

echo "Pulling newest artemis docker image"
docker pull ghcr.io/ls1intum/artemis

echo "Updating docker group ID in the docker compose file"
PRIMARY_GROUP_ID=$(dscl . -read /Groups/docker PrimaryGroupID | awk '{print $2}')
if [ -n "$PRIMARY_GROUP_ID" ]; then
    sed -i '' "s/999/$PRIMARY_GROUP_ID/g" artemis-dev-local-vc-local-ci-mysql.yml
else
    echo "PrimaryGroupID not found, skipping replacement"
fi

docker compose -f artemis-dev-local-vc-local-ci-mysql.yml up -d
echo "Finished docker compose"

cd ..

echo "Installing Artemis npm dependencies and start Artemis client"

npm install
npm run start
