#!/bin/sh

set -e

artemis_path="$(readlink -f "$(dirname "$0")/../..")"

cd "$artemis_path/docker"
open -a Docker

echo "Updating docker group ID in the docker compose file"
PRIMARY_GROUP_ID=$(dscl . -read /Groups/docker PrimaryGroupID | awk '{print $2}')
if [ -n "$PRIMARY_GROUP_ID" ]; then
    sed -i '' "s/999/$PRIMARY_GROUP_ID/g" artemis-dev-local-vc-local-ci-mysql.yml
else
    echo "PrimaryGroupID not found, skipping replacement"
fi

docker compose -f artemis-dev-local-vc-local-ci-mysql.yml up -d
echo "Finished docker compose"

cd "$artemis_path"

echo "Installing Artemis npm dependencies and start Artemis client"

npm install
npm run start
