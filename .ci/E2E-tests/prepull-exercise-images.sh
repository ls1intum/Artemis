#!/usr/bin/env bash

# Pre-pulls the LocalCI exercise images that the E2E suite's programming exercises build in.
#
# Why this exists
# ---------------
# The E2E stack mounts the host's Docker socket (docker/playwright-E2E-tests-postgres-localci.yml),
# so the Artemis build agent runs exercise builds against the runner's own Docker daemon. Without
# this step the first Java build of a run discovers that the ~1 GB Maven image is missing and pulls
# it *while the suite is already running*, competing with the test workload for CPU, disk and
# network. Observed consequence on a PR run: every pull of ls1tum/artemis-maven-template died
# mid-extraction ("DockerClientException: Could not pull image: Extracting") for the first 25
# minutes, 261 build jobs failed, and every test that asserts on a Java build result failed with a
# "0%, Build failed" score — which reads as a grading regression and says nothing about the cause.
# Tests scheduled after the image finally landed passed, so the symptom tracked the schedule rather
# than the code, and the same handful of early tests failed on every pull request.
#
# Pulling here instead moves that one-off cost in front of the suite, where it competes with nothing
# and can be retried, and turns an unobtainable image into one loud setup failure rather than a few
# mystifying assertion failures. Images already present on the host are left alone, so on a warm
# self-hosted runner this is a no-op.

set -euo pipefail

# Images to provision, with the language whose exercises need them. Only the languages the E2E suite
# actually creates exercises for are listed — pulling every language in application.yml would cost
# several gigabytes for images no test builds in.
IMAGES=(
    "ls1tum/artemis-maven-template:java17-25" # java + kotlin default (also the default exercise language)
    "ls1tum/artemis-c-minimal-docker:1.0.0"   # c default
    "ls1tum/artemis-fact-minimal-docker:1.1.0" # c, fact project type
    "ls1tum/artemis-python-docker:v1.1.0"     # python default
)

# Resolved from the script's own location so it does not matter which directory the caller is in.
REPO_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
CONFIG_FILE="${REPO_ROOT}/src/main/resources/config/application.yml"

MAX_ATTEMPTS=2
# Cold pulls of all four images together took 30 seconds on the CI runner, the Maven image 11 of them,
# so this cap carries a large multiple of the real cost while keeping the worst case bounded: two
# attempts per image, so a totally unreachable registry costs about 8 minutes before the step reports
# why rather than hanging until the job timeout.
PULL_TIMEOUT_SECONDS=120

# Prints every image configured for the build agent, one per line.
#
# Reads only the build agent's own block (`artemis.continuous-integration.build.images`) rather than
# grepping the whole file, so an image name appearing in a comment or an unrelated setting cannot
# satisfy the drift check below. The file holds a second, unrelated `images:` block for Kubernetes app
# definitions at a shallower indent, which is why the block has to be nested inside `build:` to count:
# `build_indent` is tracked while inside that block and cleared on leaving it, so an `images:` key in
# some later section cannot be picked up. Language keys carry no value of their own and are skipped;
# values are accepted quoted or bare, and trailing comments are stripped.
configured_images() {
    awk '
        /^[[:space:]]*build:[[:space:]]*$/ {
            build_indent = match($0, /[^[:space:]]/)
            next
        }
        # Left the build block without having found its images child — stop looking until the next one.
        !in_block && build_indent && !/^[[:space:]]*$/ && match($0, /[^[:space:]]/) <= build_indent {
            build_indent = 0
        }
        !in_block && build_indent && /^[[:space:]]*images:[[:space:]]*$/ && match($0, /[^[:space:]]/) > build_indent {
            in_block = 1
            block_indent = match($0, /[^[:space:]]/)
            next
        }
        in_block {
            if ($0 ~ /^[[:space:]]*$/) { next }
            if (match($0, /[^[:space:]]/) <= block_indent) { in_block = 0; build_indent = 0; next }
            line = $0
            sub(/[[:space:]]+#.*$/, "", line)
            if (line !~ /:[[:space:]]*[^[:space:]]/) { next }
            sub(/^[^:]*:[[:space:]]*/, "", line)
            gsub(/^["'"'"']|["'"'"']$/, "", line)
            if (line != "") { print line }
        }
    ' "$CONFIG_FILE"
}

# Guard against drift: the list above is a copy of the tags configured for the build agent, so a tag
# bumped in application.yml without updating this script would silently reintroduce the on-demand
# pull this step exists to prevent. Fail here, where the reason is obvious, instead of there.
if [ ! -r "$CONFIG_FILE" ]; then
    echo "::error title=Could not read E2E exercise images::${CONFIG_FILE} is missing or unreadable, so the configured image tags cannot be checked."
    exit 1
fi

# A `while read` loop rather than `mapfile`, which macOS's default Bash 3.2 does not provide.
CONFIGURED=()
while IFS= read -r configured_image; do
    CONFIGURED+=("$configured_image")
done < <(configured_images)

if [ ${#CONFIGURED[@]} -eq 0 ]; then
    echo "::error title=Could not read E2E exercise images::Found no images under the 'images:' block of ${CONFIG_FILE}. The configuration layout changed; update configured_images() in $0."
    exit 1
fi

for image in "${IMAGES[@]}"; do
    found=false
    for configured in "${CONFIGURED[@]}"; do
        if [ "$image" = "$configured" ]; then
            found=true
            break
        fi
    done
    if [ "$found" != true ]; then
        echo "::error title=E2E image list is stale::${image} is no longer configured in ${CONFIG_FILE}. Update IMAGES in $0 to match the tags the build agent uses, otherwise the exercise image is pulled on demand during the test run and Java build assertions fail."
        exit 1
    fi
done

# A bound turns a stalled pull into a non-zero exit so the retries can act on it. coreutils ships
# `timeout` on the CI runners; macOS names it `gtimeout` when coreutils is installed via Homebrew,
# which keeps the bound working for anyone running this locally.
timeout_bin=""
if command -v timeout > /dev/null 2>&1; then
    timeout_bin="timeout"
elif command -v gtimeout > /dev/null 2>&1; then
    timeout_bin="gtimeout"
fi

for image in "${IMAGES[@]}"; do
    if docker image inspect "$image" > /dev/null 2>&1; then
        echo "Already present: ${image}"
        continue
    fi

    # Refuse to start a pull that cannot be bounded: an unbounded `docker pull` that stalls would hold
    # the setup step open until the job timeout, which is the failure this script exists to remove. Only
    # reached when an image is actually missing, so a machine with every image cached still succeeds.
    if [ -z "$timeout_bin" ]; then
        echo "::error title=Cannot bound the E2E image pull::${image} is missing and neither 'timeout' nor 'gtimeout' is available, so a stalled pull could hang this step indefinitely. Install coreutils (on macOS: brew install coreutils) or pull the image manually."
        exit 1
    fi

    pulled=false
    for attempt in $(seq 1 "$MAX_ATTEMPTS"); do
        echo "Pulling ${image} (attempt ${attempt}/${MAX_ATTEMPTS}, up to ${PULL_TIMEOUT_SECONDS}s)..."
        # SIGKILL after a short grace, so a pull wedged badly enough to ignore SIGTERM cannot hold the
        # step open either.
        if "$timeout_bin" --kill-after=30s "${PULL_TIMEOUT_SECONDS}s" docker pull --quiet "$image"; then
            pulled=true
            break
        fi
        # A pull that dies part-way leaves the partial layers behind, so the next attempt resumes from
        # them rather than starting over.
        if [ "$attempt" -lt "$MAX_ATTEMPTS" ]; then
            sleep 10
        fi
    done

    if [ "$pulled" != true ]; then
        echo "::error title=Could not provision E2E exercise image::Failed to pull ${image} after ${MAX_ATTEMPTS} attempts of up to ${PULL_TIMEOUT_SECONDS}s. Programming-exercise builds cannot run without it, so every test asserting on a build result would fail with an unexplained 0% score. Check the runner's disk space and registry connectivity."
        exit 1
    fi
    echo "Pulled: ${image}"
done

echo "All E2E exercise images are available."
