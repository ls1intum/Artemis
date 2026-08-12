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

MAX_ATTEMPTS=3
# Cold pulls of all four images together took 30 seconds on the CI runner, the Maven image 11 of them,
# so these caps carry a large multiple of the real cost. They exist to stop a *stalled* pull: an
# unbounded `docker pull` here would hang until the job timeout, which is the same undiagnosable
# failure this script exists to remove, only moved earlier.
PULL_TIMEOUT_SECONDS=300
# `timeout` only terminates the `docker pull` client; the daemon keeps the pull going and the next
# attempt attaches to it. Against a genuinely stalled daemon or registry every attempt would therefore
# burn its full cap, so the retries need a shared ceiling as well — otherwise the step could outlive
# the job timeout and be killed before it can report why.
PULL_DEADLINE_SECONDS=600
# Grace between SIGTERM and SIGKILL for a pull that will not stop on its own. Subtracted from each
# attempt's cap rather than added to it, so the deadline stays a true ceiling.
KILL_GRACE_SECONDS=30

# Prints every image configured for the build agent, one per line.
#
# Reads only the build agent's own block (`artemis.continuous-integration.build.images`) rather than
# grepping the whole file, so an image name appearing in a comment or an unrelated setting cannot
# satisfy the drift check below. The file holds a second, unrelated `images:` block for Kubernetes app
# definitions at a shallower indent, which is why the block has to be nested inside `build:` to count:
# `build_indent` is tracked while inside that block and cleared on leaving it, so an `images:` key in
# some later section cannot be picked up. It has to be the *direct* child: `build_child_indent` is taken
# from the first key below `build:`, and only an `images:` at exactly that indent counts, so an `images:`
# mapping nested deeper under `build:` - inside `default-docker-flags:`, say - is not mistaken for the
# list of build agent images. Language keys carry no value of their own and are skipped; values are
# accepted quoted or bare, and trailing comments are stripped.
configured_images() {
    awk '
        /^[[:space:]]*build:[[:space:]]*$/ {
            build_indent = match($0, /[^[:space:]]/)
            build_child_indent = 0
            next
        }
        # Left the build block without having found its images child — stop looking until the next one.
        !in_block && build_indent && !/^[[:space:]]*$/ && match($0, /[^[:space:]]/) <= build_indent {
            build_indent = 0
            build_child_indent = 0
        }
        # The first key below `build:` fixes the indent its direct children sit at.
        !in_block && build_indent && !build_child_indent && !/^[[:space:]]*$/ && match($0, /[^[:space:]]/) > build_indent {
            build_child_indent = match($0, /[^[:space:]]/)
        }
        !in_block && build_indent && /^[[:space:]]*images:[[:space:]]*$/ && match($0, /[^[:space:]]/) == build_child_indent {
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
    deadline=$((SECONDS + PULL_DEADLINE_SECONDS))
    for attempt in $(seq 1 "$MAX_ATTEMPTS"); do
        remaining=$((deadline - SECONDS))
        if [ "$remaining" -le 0 ]; then
            echo "Giving up on ${image}: its ${PULL_DEADLINE_SECONDS}s budget is spent after $((attempt - 1)) attempt(s)."
            break
        fi

        # Cap the attempt by whatever is left of the image's budget, not just by PULL_TIMEOUT_SECONDS.
        # Checking the deadline only between attempts would let a single hanging pull overrun it by a
        # further PULL_TIMEOUT_SECONDS, so the shared ceiling has to bound the pull itself.
        attempt_cap=$((remaining < PULL_TIMEOUT_SECONDS ? remaining : PULL_TIMEOUT_SECONDS))

        # The SIGKILL grace period has to come out of the attempt's own share of the budget, not be
        # added to it: `timeout --kill-after=D T` sends SIGTERM at T and only escalates at T+D, so
        # charging D on top would let a pull that ignores SIGTERM run past the deadline the comment
        # above claims to hold. Below the grace period there is no room to be graceful, so kill outright.
        pull=(docker pull --quiet "$image")
        if [ "$attempt_cap" -gt "$KILL_GRACE_SECONDS" ]; then
            pull=("$timeout_bin" --kill-after="${KILL_GRACE_SECONDS}s" "$((attempt_cap - KILL_GRACE_SECONDS))s" "${pull[@]}")
        else
            pull=("$timeout_bin" --signal=KILL "${attempt_cap}s" "${pull[@]}")
        fi

        echo "Pulling ${image} (attempt ${attempt}/${MAX_ATTEMPTS}, up to ${attempt_cap}s)..."
        if "${pull[@]}"; then
            pulled=true
            break
        fi

        # A pull that dies part-way leaves the partial layers behind; the next attempt resumes from
        # them, so a plain retry after a short backoff is worth more than it looks. The backoff is
        # bounded by the budget too, otherwise sleeping could push the loop past the deadline.
        remaining=$((deadline - SECONDS))
        if [ "$attempt" -lt "$MAX_ATTEMPTS" ] && [ "$remaining" -gt 0 ]; then
            backoff=$((attempt * 10))
            if [ "$backoff" -gt "$remaining" ]; then
                backoff=$remaining
            fi
            sleep "$backoff"
        fi
    done

    if [ "$pulled" != true ]; then
        echo "::error title=Could not provision E2E exercise image::Failed to pull ${image} within ${PULL_DEADLINE_SECONDS}s (up to ${MAX_ATTEMPTS} attempts). Programming-exercise builds cannot run without it, so every test asserting on a build result would fail with an unexplained 0% score. Check the runner's disk space and registry connectivity."
        exit 1
    fi
    echo "Pulled: ${image}"
done

echo "All E2E exercise images are available."
