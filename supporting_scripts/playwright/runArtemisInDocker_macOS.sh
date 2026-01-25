#!/bin/sh

set -e

artemis_path="$(cd "$(dirname "$0")/../.." && pwd -P)"

cd "$artemis_path/docker"
open -a Docker

echo "Updating docker group ID in the docker compose file"
# On macOS, Docker Desktop uses a user-owned socket (e.g., ~/.docker/run/docker.sock).
# The 'docker' group usually does not exist, so we derive the GID from the actual socket.
DOCKER_SOCK_PATH="/var/run/docker.sock"
if [ -L "$DOCKER_SOCK_PATH" ]; then
    DOCKER_SOCK_PATH="$(readlink "$DOCKER_SOCK_PATH")"
fi
PRIMARY_GROUP_ID="$(stat -f '%g' "$DOCKER_SOCK_PATH" 2>/dev/null || true)"
if [ -z "$PRIMARY_GROUP_ID" ]; then
    PRIMARY_GROUP_ID=$(dscl . -read /Groups/docker PrimaryGroupID 2>/dev/null | awk '{print $2}')
fi

if [ -n "$PRIMARY_GROUP_ID" ]; then
    sed -i '' "s/999/$PRIMARY_GROUP_ID/g" artemis-dev-local-vc-local-ci-mysql.yml
else
    echo "Docker socket group ID not found, skipping replacement"
fi

docker compose -f artemis-dev-local-vc-local-ci-mysql.yml up -d
echo "Finished docker compose"

cd "$artemis_path"

echo "Installing Artemis npm dependencies and start Artemis client"

npm install
npm run start
