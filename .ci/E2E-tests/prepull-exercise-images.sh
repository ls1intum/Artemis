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

CONFIG_FILE="src/main/resources/config/application.yml"
MAX_ATTEMPTS=3

# Guard against drift: the list above is a copy of the tags configured for the build agent, so a tag
# bumped in application.yml without updating this script would silently reintroduce the on-demand
# pull this step exists to prevent. Fail here, where the reason is obvious, instead of there.
for image in "${IMAGES[@]}"; do
    if ! grep -qF "\"${image}\"" "$CONFIG_FILE"; then
        echo "::error title=E2E image list is stale::${image} is no longer configured in ${CONFIG_FILE}. Update IMAGES in $0 to match the tags the build agent uses, otherwise the exercise image is pulled on demand during the test run and Java build assertions fail."
        exit 1
    fi
done

for image in "${IMAGES[@]}"; do
    if docker image inspect "$image" > /dev/null 2>&1; then
        echo "Already present: ${image}"
        continue
    fi

    pulled=false
    for attempt in $(seq 1 "$MAX_ATTEMPTS"); do
        echo "Pulling ${image} (attempt ${attempt}/${MAX_ATTEMPTS})..."
        if docker pull --quiet "$image"; then
            pulled=true
            break
        fi
        # A pull that dies part-way leaves the partial layers behind; the next attempt resumes from
        # them, so a plain retry after a short backoff is worth more than it looks.
        if [ "$attempt" -lt "$MAX_ATTEMPTS" ]; then
            sleep $((attempt * 10))
        fi
    done

    if [ "$pulled" != true ]; then
        echo "::error title=Could not provision E2E exercise image::Failed to pull ${image} after ${MAX_ATTEMPTS} attempts. Programming-exercise builds cannot run without it, so every test asserting on a build result would fail with an unexplained 0% score. Check the runner's disk space and registry connectivity."
        exit 1
    fi
    echo "Pulled: ${image}"
done

echo "All E2E exercise images are available."
