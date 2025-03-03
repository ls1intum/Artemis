#!/bin/sh

set -e

artemis_path="$(readlink -f "$(dirname "$0")/../..")"

cd "$artemis_path/docker"

echo "Updating docker group ID in the docker compose file"
sed -i "s/999/$(getent group docker | cut -d: -f3)/g" artemis-dev-local-vc-local-ci-mysql.yml

docker compose -f artemis-dev-local-vc-local-ci-mysql.yml up -d
echo "Finished docker compose"

cd "$artemis_path"

echo "Installing Artemis npm dependencies and start Artemis client"

npm install
npm run start
