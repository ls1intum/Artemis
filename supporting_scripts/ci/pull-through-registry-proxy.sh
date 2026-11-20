#!/usr/bin/env bash

# Makes container images available on this host, pulling Docker Hub images through the AET Harbor
# pull-through cache. A Docker Hub image is never pulled from Docker Hub itself.
#
# Why this exists
# ---------------
# The self-hosted runners share few public IP addresses, and Docker Hub limits anonymous pulls per
# address. Once the allowance is used up, or the path to Docker Hub is slow, a pull stalls without an
# error: server tests then fail in Testcontainers ("Docker image pull has not made progress") and the
# E2E setup cannot provision the exercise images. The AET Harbor instance runs a proxy cache for Docker
# Hub, so a pull from there costs Docker Hub nothing once the image is cached.
#
# What it does
# ------------
# For every image given as an argument:
#   1. An image that is already present is left alone (unless --refresh is given).
#   2. A Docker Hub image is pulled from the proxy and tagged with its original name, so compose files,
#      Testcontainers and the Artemis build agent find it under the name they ask for.
#   3. If the proxy cannot serve it, the image counts as failed. There is no fallback to Docker Hub: it
#      would hide a broken proxy until Docker Hub throttles the runner again.
# Images of other registries (ghcr.io, quay.io, ...) are pulled directly.
#
# Usage: pull-through-registry-proxy.sh [--refresh] IMAGE...
#   --refresh  pull an image again even if it is present, like `docker compose pull --policy always`
#
# Environment:
#   DOCKER_HUB_PROXY             registry path that mirrors Docker Hub (default:
#                                harbor.aet.cit.tum.de/dockerhub-ghr-proxy)
#   IMAGE_PULL_TIMEOUT_SECONDS   upper bound of a single pull (default: 300)
#   REGISTRY_PROXY_PROBE_SECONDS how long to wait for the proxy to answer before giving up (default: 10)

set -uo pipefail

DOCKER_HUB_PROXY="${DOCKER_HUB_PROXY:-harbor.aet.cit.tum.de/dockerhub-ghr-proxy}"
IMAGE_PULL_TIMEOUT_SECONDS="${IMAGE_PULL_TIMEOUT_SECONDS:-300}"

refresh=false
if [ "${1:-}" = "--refresh" ]; then
    refresh=true
    shift
fi

if [ "$#" -eq 0 ]; then
    echo "Usage: $0 [--refresh] IMAGE..." >&2
    exit 2
fi

# coreutils ships `timeout` on the CI runners; macOS names it `gtimeout` when coreutils is installed
# via Homebrew. Without either a pull is not bounded, which only matters for local use.
timeout_bin=""
if command -v timeout > /dev/null 2>&1; then
    timeout_bin="timeout"
elif command -v gtimeout > /dev/null 2>&1; then
    timeout_bin="gtimeout"
fi

# Pulls one reference with an upper bound, so a stalled pull fails instead of holding the job open.
bounded_pull() {
    if [ -n "$timeout_bin" ]; then
        "$timeout_bin" --kill-after=30s "${IMAGE_PULL_TIMEOUT_SECONDS}s" docker pull --quiet "$1" > /dev/null
    else
        docker pull --quiet "$1" > /dev/null
    fi
}

# Prints the repository path of a Docker Hub image ("library/alpine:3", "ls1tum/image:1") and returns
# 1 for an image of another registry. A first path segment that contains a dot or a colon, or that is
# "localhost", names a registry host; that is the rule Docker itself applies.
docker_hub_path() {
    local image="$1"
    local first="${image%%/*}"
    if [ "$image" = "$first" ]; then
        echo "library/${image}"
        return 0
    fi
    case "$first" in
        docker.io | index.docker.io | registry-1.docker.io)
            image="${image#*/}"
            ;;
        localhost | *.* | *:*)
            return 1
            ;;
    esac
    case "$image" in
        */*) echo "$image" ;;
        *) echo "library/${image}" ;;
    esac
}

# Asks the proxy once whether it answers at all, so a runner that cannot reach it fails after one short
# wait instead of after the connect timeout of every image. Any HTTP status counts as reachable: the
# registry endpoint answers 401 without a token.
proxy_probed=false
require_reachable_proxy() {
    [ "$proxy_probed" = true ] && return 0
    proxy_probed=true
    command -v curl > /dev/null 2>&1 || return 0
    if ! curl --silent --output /dev/null --max-time "${REGISTRY_PROXY_PROBE_SECONDS:-10}" "https://${DOCKER_HUB_PROXY%%/*}/v2/"; then
        echo "::error title=Registry proxy unreachable::${DOCKER_HUB_PROXY%%/*} did not answer within ${REGISTRY_PROXY_PROBE_SECONDS:-10}s. Docker Hub images are only pulled through it."
        return 1
    fi
}

failed=()
for image in "$@"; do
    if [ "$refresh" = false ] && docker image inspect "$image" > /dev/null 2>&1; then
        echo "Already present: ${image}"
        continue
    fi

    if hub_path="$(docker_hub_path "$image")"; then
        if ! require_reachable_proxy; then
            exit 1
        fi
        proxied="${DOCKER_HUB_PROXY}/${hub_path}"
        if bounded_pull "$proxied" && docker tag "$proxied" "$image"; then
            # Drop the proxy name again: the image stays, under the name its users ask for.
            docker rmi "$proxied" > /dev/null 2>&1 || true
            echo "Pulled through ${DOCKER_HUB_PROXY}: ${image}"
        else
            failed+=("$image")
        fi
    elif bounded_pull "$image"; then
        echo "Pulled: ${image}"
    else
        failed+=("$image")
    fi
done

if [ "${#failed[@]}" -gt 0 ]; then
    echo "::error title=Could not pull images::${failed[*]}"
    exit 1
fi
