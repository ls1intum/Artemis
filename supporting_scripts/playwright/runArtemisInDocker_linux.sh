#!/bin/sh

set -e

cd ../..

# configure application and start

if [ ! -f src/main/resources/config/application-local.yml ]; then
    cp src/main/resources/config/application-local-template.yml src/main/resources/config/application-local.yml
fi

echo "Copied configuration"

cd docker

echo "Pulling newest artemis docker image"
docker pull ghcr.io/ls1intum/artemis

echo "Updating docker group ID in the docker compose file"
sed -i "s/999/$(getent group docker | cut -d: -f3)/g" artemis-dev-local-vc-local-ci-mysql.yml

docker compose -f artemis-dev-local-vc-local-ci-mysql.yml up -d
echo "Finished docker compose"

cd ..

echo "Installing Artemis npm dependencies and start Artemis client"

npm install
npm run start
