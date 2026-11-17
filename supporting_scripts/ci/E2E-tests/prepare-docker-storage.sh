#!/usr/bin/env bash
set -euo pipefail

# Called only by the dedicated E2E runner after its containers have been stopped.
# Immutable application image tags otherwise accumulate across runs. Keep recent images
# when space allows, all images in use, and the exercise/browser/database image cache.
application_source_label=org.opencontainers.image.source=https://github.com/ls1intum/Artemis
docker image prune --all --force \
    --filter "label=$application_source_label" \
    --filter until=24h

docker_root=$(docker info --format '{{.DockerRootDir}}')
read_headroom() {
    read -r total_kib available_kib < <(df -Pk "$docker_root" | awk 'END { print $2, $4 }')
    if ! [[ "$total_kib" =~ ^[0-9]+$ && "$available_kib" =~ ^[0-9]+$ ]] || ((total_kib == 0)); then
        echo '::error::Cannot determine Docker disk headroom on the E2E runner.' >&2
        exit 1
    fi

    # ActiveMQ stops producers at 90% disk use. Reserve another 10 GiB for image
    # pulls, exercise builds and test artifacts; do not relax this reserve.
    required_kib=$((total_kib / 10 + 10 * 1024 * 1024))
}
read_headroom

if ((available_kib < required_kib)); then
    echo 'Docker disk headroom is low; reclaiming unused build cache to meet the reserve before checking again.'
    # Use the daemon's default builder, whose storage is checked above. Buildx
    # evicts least-recently-used, reclaimable cache only until the required free
    # space is available, with a 512 MB cache floor. Cached application/exercise/
    # browser/database images, containers and volumes remain untouched.
    if ! docker buildx prune --builder default --force --min-free-space "$((required_kib * 1024))" --reserved-space 512MB; then
        echo '::error::Cannot reclaim unused Docker build cache on the E2E runner.' >&2
        exit 1
    fi
    read_headroom
fi

if ((available_kib < required_kib)); then
    echo 'Docker disk headroom is still low; reclaiming unused Artemis images regardless of age.'
    # Busy runners can fill with application images created within the last day.
    # Drop only their retention window under pressure. Docker keeps images referenced
    # by containers; other images, containers and volumes remain untouched.
    if ! docker image prune --all --force --filter "label=$application_source_label"; then
        echo '::error::Cannot reclaim unused Artemis images on the E2E runner.' >&2
        exit 1
    fi
    read_headroom
fi

if ((available_kib < required_kib)); then
    echo "::error::Insufficient Docker disk headroom: ${available_kib} KiB available, ${required_kib} KiB required below the broker disk limit. Reclaim space or expand the dedicated E2E runner disk." >&2
    docker system df
    exit 1
fi
echo "Docker disk headroom: ${available_kib} KiB available, ${required_kib} KiB required."
