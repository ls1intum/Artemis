#!/bin/sh

set -e

cd ../..

echo "Copied configuration"

cd docker

echo "Updating docker group ID in the docker compose file"
sed -i "s/999/$(getent group docker | cut -d: -f3)/g" artemis-dev-local-vc-local-ci-mysql.yml

docker compose -f artemis-dev-local-vc-local-ci-mysql.yml up -d
echo "Finished docker compose"

cd ..

echo "Installing Artemis npm dependencies and start Artemis client"

npm install
npm run start
