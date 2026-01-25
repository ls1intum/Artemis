#!/bin/sh

set -e

artemis_path="$(cd "$(dirname "$0")/../.." && pwd -P)"

cd "$artemis_path/docker"

echo "Updating docker group ID in the docker compose file"
DOCKER_GID="$(getent group docker 2>/dev/null | cut -d: -f3)"
if [ -n "$DOCKER_GID" ]; then
	sed -i "s/999/$DOCKER_GID/g" artemis-dev-local-vc-local-ci-mysql.yml
else
	echo "Docker group ID not found, skipping replacement"
fi

docker compose -f artemis-dev-local-vc-local-ci-mysql.yml up -d
echo "Finished docker compose"

cd "$artemis_path"

echo "Installing Artemis npm dependencies and start Artemis client"

npm install
npm run start
