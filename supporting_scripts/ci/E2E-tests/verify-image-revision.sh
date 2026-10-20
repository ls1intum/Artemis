#!/usr/bin/env bash
# Verify the source revision of the exact image every Artemis node will run.
set -Eeuo pipefail

expected=${ARTEMIS_EXPECTED_COMMIT_SHA:-}
if ! [[ "$expected" =~ ^[0-9a-f]{40}$ ]]; then
    echo '::error::ARTEMIS_EXPECTED_COMMIT_SHA must be a full commit SHA.' >&2
    exit 1
fi
if [[ "${ARTEMIS_DOCKER_TAG:-}" != "sha-${expected}" ]]; then
    echo "::error::Docker tag must match expected commit: expected sha-${expected}." >&2
    exit 1
fi

image="ghcr.io/ls1intum/artemis:sha-${expected}"
revision=$(docker image inspect --format '{{ index .Config.Labels "org.opencontainers.image.revision" }}' "$image")
if [[ "$revision" != "$expected" ]]; then
    # No environment dumps or image config: either could expose runtime credentials.
    echo "::error::Image revision mismatch for ${image}; expected ${expected}." >&2
    exit 1
fi
printf 'Verified Artemis image revision: %s\n' "$expected"
