#!/usr/bin/env bash
set -euo pipefail

# Called only by the dedicated E2E runner after its containers have been stopped.
# Immutable application image tags otherwise accumulate across runs. Keep recent images,
# all images in use, and the exercise/browser/database images cached by this runner.
docker image prune --all --force \
    --filter label=org.opencontainers.image.source=https://github.com/ls1intum/Artemis \
    --filter until=24h

docker_root=$(docker info --format '{{.DockerRootDir}}')
read -r total_kib available_kib < <(df -Pk "$docker_root" | awk 'END { print $2, $4 }')
if ! [[ "$total_kib" =~ ^[0-9]+$ && "$available_kib" =~ ^[0-9]+$ ]] || ((total_kib == 0)); then
    echo '::error::Cannot determine Docker disk headroom on the E2E runner.' >&2
    exit 1
fi

# ActiveMQ stops producers at 90% disk use. Reserve another 10 GiB for image pulls,
# exercise builds and test artifacts, so that storage pressure fails during setup
# rather than surfacing as unrelated websocket/save/result assertion timeouts.
required_kib=$((total_kib / 10 + 10 * 1024 * 1024))
if ((available_kib < required_kib)); then
    echo "::error::Insufficient Docker disk headroom: ${available_kib} KiB available, ${required_kib} KiB required below the broker disk limit. Reclaim space or expand the dedicated E2E runner disk." >&2
    docker system df
    exit 1
fi
echo "Docker disk headroom: ${available_kib} KiB available, ${required_kib} KiB required."
