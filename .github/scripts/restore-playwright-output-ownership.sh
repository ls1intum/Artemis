#!/usr/bin/env bash
set -Eeuo pipefail

workspace=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd -P)
playwright_dir="$workspace/src/test/playwright"
outputs=(test-reports test-results .auth node_modules test-exercise-repos)
image_id=

# Match the checkout bind, not just the service label: another job's runner must remain untouched.
containers=$(docker ps --all --quiet --filter label=com.docker.compose.service=artemis-playwright)
while IFS= read -r container; do
    [[ -n "$container" ]] || continue
    source=$(docker inspect --format '{{range .Mounts}}{{if and (eq .Type "bind") (eq .Destination "/app/artemis")}}{{.Source}}{{end}}{{end}}' "$container")
    [[ -n "$source" && "$source" -ef "$workspace" ]] || continue
    image_id=$(docker inspect --format '{{.Image}}' "$container")
    if [[ "$(docker inspect --format '{{.State.Running}}' "$container")" == true ]]; then
        # A cancelled compose command can leave its writer alive; stop it before restoring ownership.
        docker stop "$container" >/dev/null
    fi
done <<< "$containers"

has_outputs=false
for output in "${outputs[@]}"; do
    if [[ -e "$playwright_dir/$output" || -L "$playwright_dir/$output" ]]; then
        has_outputs=true
        break
    fi
done
if [[ "$has_outputs" == false ]]; then
    exit 0
fi
if [[ ! "$image_id" =~ ^sha256:[0-9a-f]{64}$ ]]; then
    echo 'No cached Playwright container image was found for this checkout; output ownership was not changed.' >&2
    exit 1
fi

# Use the already-used image by ID, without pulling or exposing anything beyond this Playwright directory.
docker run --rm --pull=never --network=none --read-only --user=0:0 \
    --mount "type=bind,src=$playwright_dir,dst=/playwright" \
    --entrypoint /bin/sh "$image_id" -eu -c '
        owner=$1
        shift
        for output do
            path=/playwright/$output
            if [ -e "$path" ] || [ -L "$path" ]; then
                chown -hRP "$owner" -- "$path"
            fi
        done
    ' ownership "$(id -u):$(id -g)" "${outputs[@]}"
