#!/bin/bash
set -e

# =============================================================================
# Fast Multi-Node Local E2E Test Runner for Artemis
# =============================================================================
# Runs the same 3-node multi-node topology as run-e2e-tests-local-multinode.sh
# but launches the Artemis nodes directly from the WAR (java -jar) on the host
# instead of building a Docker image. The supporting infrastructure (Postgres,
# JHipster Registry / Eureka, ActiveMQ broker, nginx LB) still runs in Docker.
#
# Use this for fast iteration on multi-node-sensitive server changes.
# Use ./run-e2e-tests-local-multinode.sh when you need full prod-faithful CI parity
# (Docker image, container isolation, etc.).
#
# Stack layout (host network):
#   - node-1 (core, scheduling)              http :8080  hazelcast :5701  ssh :7921
#   - node-2 (core, buildagent)              http :8081  hazelcast :5702  ssh :7922
#   - node-3 (buildagent only)               http :8082
#   - postgres   container                  127.0.0.1:5432
#   - jhipster-registry (Eureka) container          :8761
#   - activemq-broker container                     :61613
#   - redis container (--middleware redis)  127.0.0.1:6379
#   - nginx LB container                            :443 (HTTPS), :54321 (HTTP)
#
# Usage:
#   ./run-e2e-tests-local-multinode-fast.sh [options]
#
# Options:
#   --stop                 Tear everything down (host JVMs + infra containers)
#   --filter <pattern>     Run only tests matching the pattern (e.g., "Quiz")
#   --specs "<paths>"      Run only these spec paths, relative to src/test/playwright
#                            (e.g., "e2e/exam/ExamResults.spec.ts e2e/lecture/").
#                            Replaces the default "run everything under e2e/".
#                            Combines with --filter. Get the paths for a branch with
#                            .ci/E2E-tests/determine-relevant-tests.sh
#   --middleware <name>    Distributed data backend: hazelcast (default) or redis.
#                            Both are driven through the DistributedDataProvider
#                            abstraction, so the same tests must pass on either.
#                            With redis no Hazelcast instance is created at all.
#   --skip-build           Reuse the existing WAR in build/libs (do not rebuild)
#   --skip-up              Reuse already-running infra containers and host JVMs
#   --debug                Tee server logs to stdout (normally only in log files)
#   --help                 Show this help message
# =============================================================================

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# Defaults
STOP=false
SKIP_BUILD=false
SKIP_UP=false
DEBUG=false
TEST_FILTER=""
TEST_SPECS=""
# Hazelcast stays the default: it is what production runs today. Redis is the supported alternative and has to pass the
# same suite, which is the whole point of the DistributedDataProvider abstraction.
MIDDLEWARE="hazelcast"
PLAYWRIGHT_EXTRA_ARGS=()
export PLAYWRIGHT_VIDEO_MODE="${PLAYWRIGHT_VIDEO_MODE:-off}"
export PLAYWRIGHT_COVERAGE="${PLAYWRIGHT_COVERAGE:-off}"

while [[ $# -gt 0 ]]; do
    case $1 in
        --stop) STOP=true; shift ;;
        --skip-build) SKIP_BUILD=true; shift ;;
        --skip-up) SKIP_UP=true; shift ;;
        --debug) DEBUG=true; shift ;;
        --middleware)
            if [[ -z "$2" ]]; then
                echo -e "${RED}ERROR: --middleware requires an argument (hazelcast or redis)${NC}"
                exit 1
            fi
            MIDDLEWARE="$(echo "$2" | tr '[:upper:]' '[:lower:]')"
            if [[ "$MIDDLEWARE" != "hazelcast" && "$MIDDLEWARE" != "redis" ]]; then
                echo -e "${RED}ERROR: unknown middleware '$2'. Supported: hazelcast, redis${NC}"
                exit 1
            fi
            shift 2
            ;;
        --filter)
            if [[ -z "$2" || "${2:0:1}" == "-" ]]; then
                echo -e "${RED}ERROR: --filter requires a non-empty pattern argument${NC}"
                echo "Usage: --filter <pattern>"
                exit 1
            fi
            TEST_FILTER="$2"
            shift 2
            ;;
        --specs)
            if [[ -z "$2" || "${2:0:1}" == "-" ]]; then
                echo -e "${RED}ERROR: --specs requires a non-empty list of spec paths${NC}"
                echo "Usage: --specs \"<paths>\""
                echo "Example: --specs \"e2e/exam/ExamResults.spec.ts e2e/lecture/\""
                exit 1
            fi
            TEST_SPECS="$2"
            shift 2
            ;;
        --help) head -45 "$0" | tail -41; exit 0 ;;
        *) echo -e "${RED}Unknown option: $1${NC}"; exit 1 ;;
    esac
done

cd "$(dirname "$0")"
LOCAL_DIR=".e2e-local-multinode-fast"
COMPOSE_FILE="docker/playwright-E2E-tests-multi-node-fast.yml"
# Always merged in so that `--stop` tears the Redis container down as well, whichever middleware the run used. Compose
# only starts the services named on `up`, so defining `redis` here does not start it for a Hazelcast run.
REDIS_COMPOSE_FILE="docker/redis.yml"
COMPOSE_ARGS=(--env-file .env -f "$COMPOSE_FILE" -f "$REDIS_COMPOSE_FILE")

# Per-node port allocation. Indexes match node1/node2/node3 below.
HTTP_PORTS=(8080 8081 8082)
# HZ_PORTS/SSH_PORTS document the per-node port scheme for reference; not referenced directly (SC2034).
# shellcheck disable=SC2034
HZ_PORTS=(5701 5702)            # node-3 has no Hazelcast bind port (client)
# shellcheck disable=SC2034
SSH_PORTS=(7921 7922)            # node-3 has no Git SSH

# All host ports the script claims; freed during preflight + --stop.
ALL_PORTS=(8080 8081 8082 5701 5702 7921 7922)

# Kill a process and all its children (portable, works on macOS and Linux)
kill_tree() {
    local pid=$1
    for child in $(pgrep -P "$pid" 2>/dev/null); do
        kill_tree "$child"
    done
    kill "$pid" 2>/dev/null || true
}

# How long to wait for a killed process to release its listening socket before giving up.
# Validated rather than trusted: a non-numeric value would otherwise blow up the integer comparison below with a
# bash arithmetic error, in a pre-flight step whose whole job is to get out of the developer's way.
PORT_RELEASE_TIMEOUT="${PORT_RELEASE_TIMEOUT:-30}"
if ! [[ "$PORT_RELEASE_TIMEOUT" =~ ^[0-9]+$ ]]; then
    echo "Ignoring PORT_RELEASE_TIMEOUT='${PORT_RELEASE_TIMEOUT}': expected a non-negative integer number of seconds. Using 30."
    PORT_RELEASE_TIMEOUT=30
fi

# Free a port if a leftover process holds it. Mirrors run-e2e-tests-local-fast.sh.
check_port_available() {
    local port=$1
    local service_name=$2
    local listeners
    # `+c 0` asks for the untruncated command name. Without it lsof caps COMMAND at nine characters, so
    # `com.docker.backend` arrives as `com.docke`, the Docker check below never matches, and the guard kills
    # the very process it exists to protect.
    listeners=$(lsof +c 0 -nP -iTCP:"$port" -sTCP:LISTEN 2>/dev/null || true)
    if [ -n "$listeners" ]; then
        # A port published by a container is held by Docker's own forwarder, not by a leftover JVM. Killing
        # that process takes Docker Desktop down with it, which then fails this run at the next compose call
        # with a misleading "cannot connect to the Docker daemon". Refuse instead, and say what to stop.
        local docker_holder
        docker_holder=$(echo "$listeners" | awk 'NR>1 {print $1}' | grep -iE '^(com\.docker|docker|vpnkit)' | head -1)
        if [ -n "$docker_holder" ]; then
            echo -e "${RED}Port ${port} (${service_name}) is published by a container (held by '${docker_holder}').${NC}"
            echo -e "${RED}Refusing to kill it: that would stop Docker Desktop. Tear the container stack down first, e.g.${NC}"
            echo -e "${RED}  ./run-e2e-tests-local-multinode.sh --stop${NC}"
            exit 1
        fi
        echo -e "${YELLOW}Port ${port} (${service_name}) is in use — killing existing process...${NC}"
        local pids
        pids=$(echo "$listeners" | awk 'NR>1 {print $2}' | sort -u)
        for pid in $pids; do
            echo "  Killing PID $pid..."
            kill_tree "$pid"
        done
        # Poll for the port to be released instead of sleeping a fixed 2s and checking once. A JVM
        # shutting down can hold its listening socket noticeably longer than that, and the single
        # check then aborts the whole run over a port that frees a moment later.
        #
        # Shaped so the check always happens at least once and always happens *after* the last sleep: a
        # pre-kill `lsof` result must never be what decides the error, otherwise PORT_RELEASE_TIMEOUT=0
        # skips the loop entirely and a port released during the final second is still reported as busy.
        local waited=0
        while true; do
            listeners=$(lsof -nP -iTCP:"$port" -sTCP:LISTEN 2>/dev/null || true)
            if [ -z "$listeners" ]; then
                break
            fi
            if [ "$waited" -ge "$PORT_RELEASE_TIMEOUT" ]; then
                break
            fi
            sleep 1
            waited=$((waited + 1))
        done
        if [ -n "$listeners" ]; then
            echo -e "${RED}ERROR: Port ${port} is still in use ${PORT_RELEASE_TIMEOUT}s after killing processes.${NC}"
            echo "$listeners"
            exit 1
        fi
        echo -e "${GREEN}Port ${port} is now free (released after ${waited}s).${NC}"
    fi
}

# =============================================================================
# --stop: Tear everything down
# =============================================================================
if [ "$STOP" = true ]; then
    echo -e "${BLUE}Stopping fast multi-node E2E stack...${NC}"
    for n in 1 2 3; do
        if [ -f "$LOCAL_DIR/server-${n}.pid" ]; then
            PID=$(cat "$LOCAL_DIR/server-${n}.pid")
            if kill -0 "$PID" 2>/dev/null; then
                echo "Stopping node-${n} (PID $PID)..."
                kill_tree "$PID"
            fi
        fi
    done
    for port in "${ALL_PORTS[@]}"; do
        check_port_available "$port" "leftover process"
    done
    echo "Stopping infra containers..."
    docker compose "${COMPOSE_ARGS[@]}" down -v 2>/dev/null || true
    rm -rf "$LOCAL_DIR"
    echo -e "${GREEN}All services stopped.${NC}"
    exit 0
fi

# =============================================================================
# Banner
# =============================================================================
echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}  Artemis Fast Multi-Node E2E Runner${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

# =============================================================================
# Step 0: Prerequisites + port preflight
# =============================================================================
echo -e "${BLUE}Step 0: Checking prerequisites...${NC}"
MISSING=""
command -v docker >/dev/null 2>&1  || MISSING="$MISSING docker"
command -v java >/dev/null 2>&1    || MISSING="$MISSING java"
command -v node >/dev/null 2>&1    || MISSING="$MISSING node"

# Activate the pnpm version pinned in package.json via Corepack (shipped with
# Node 24). Idempotent; ensures `pnpm` is on PATH on fresh setups.
if command -v corepack >/dev/null 2>&1; then
    corepack enable >/dev/null 2>&1 || true
fi
command -v pnpm >/dev/null 2>&1    || MISSING="$MISSING pnpm"
command -v unzip >/dev/null 2>&1   || MISSING="$MISSING unzip"
command -v lsof >/dev/null 2>&1    || MISSING="$MISSING lsof"
command -v pgrep >/dev/null 2>&1   || MISSING="$MISSING pgrep"
command -v python3 >/dev/null 2>&1 || MISSING="$MISSING python3"
command -v curl >/dev/null 2>&1    || MISSING="$MISSING curl"
if [ -n "$MISSING" ]; then
    echo -e "${RED}ERROR: Missing required commands:$MISSING${NC}"
    if [[ "$MISSING" == *pnpm* ]]; then
        echo -e "${RED}Activate the pnpm version pinned in package.json once via:${NC}"
        echo -e "${RED}    corepack enable${NC}"
    fi
    exit 1
fi

mkdir -p "$LOCAL_DIR"

# Pre-clear ports we will claim, so a leftover process from an earlier crash cannot block the launch.
#
# With --skip-up the running nodes are exactly what we intend to reuse, so killing whatever holds their
# ports would defeat the flag: the pre-clear tore the stack down, and Step 4 then "reused" the PID it had
# just killed and aborted the whole run when that process finished dying. Under --skip-up a node port is
# therefore only cleared when its node does not answer /management/health/readiness, i.e. when it is a crashed
# leftover rather than the stack we were asked to keep.
node_port_is_healthy() {
    # Bounded on purpose: a wedged JVM can accept the connection and never answer, and an unbounded curl here would
    # hang the pre-flight instead of deciding that this stack cannot be reused.
    curl -sf --connect-timeout 2 --max-time 5 "http://localhost:$1/management/health/readiness" >/dev/null 2>&1
}

# All or nothing: the running nodes own their Hazelcast and management ports as well, so a healthy stack
# has to be kept whole. If any node is missing, everything is cleared and all three are relaunched.
REUSE_RUNNING_NODES=false
if [ "$SKIP_UP" = true ]; then
    REUSE_RUNNING_NODES=true
    for port in "${HTTP_PORTS[@]}"; do
        node_port_is_healthy "$port" || REUSE_RUNNING_NODES=false
    done
fi

if [ "$REUSE_RUNNING_NODES" = true ]; then
    echo -e "${GREEN}All three nodes answer /management/health/readiness — keeping the running stack (--skip-up).${NC}"
else
    for port in "${ALL_PORTS[@]}"; do
        check_port_available "$port" "Artemis host JVM"
    done
fi
echo -e "${GREEN}Prerequisites OK${NC}"

# =============================================================================
# Middleware selection
# =============================================================================
# The distributed data backend is chosen by a single Artemis property, read by every node from
# docker/artemis/config/middleware-<name>.env. Everything else about the stack is identical, so a Redis run and a
# Hazelcast run differ only in that file plus, for Redis, one extra container.
MIDDLEWARE_SERVICES=()
if [ "$MIDDLEWARE" = "redis" ]; then
    MIDDLEWARE_SERVICES=(redis)
fi
echo -e "${BLUE}Distributed data middleware: ${MIDDLEWARE}${NC}"

# =============================================================================
# Step 1: Build the WAR (unless --skip-build)
# =============================================================================
# Parallelise the two long legs of a -Pprod WAR build that touch disjoint output
# directories:
#   pnpm run webapp:prod      -> build/resources/main/static/   (Angular bundle)
#   ./gradlew compileJava     -> build/classes/                  (.class files)
# The bundle is then moved to build/webapp-dist and Gradle is re-entered for the assembly step, where
# the copy-only `webapp` task puts it back inside the tracked task graph (see the comment at that
# step for why handing it over through build/resources/main/static directly does not work).
#
# SBOM generation (server cyclonedxBom + client cdxgen + filter-shipped) is opt-in
# via `-Psbom`. The E2E path does not need an SBOM in the WAR, so we omit it here.
# AdminSbomResource handles a missing SBOM by returning 404, and the admin UI
# renders an informational banner.
if [ "$SKIP_BUILD" = false ]; then
    echo ""
    echo -e "${BLUE}Step 1: Building WAR (parallel: webapp + compileJava, then bootWar)...${NC}"
    CLIENT_LOG="$LOCAL_DIR/build-client.log"
    SERVER_LOG="$LOCAL_DIR/build-server.log"
    : > "$CLIENT_LOG"; : > "$SERVER_LOG"

    pnpm run webapp:prod >"$CLIENT_LOG" 2>&1 &
    CLIENT_PID=$!
    echo -e "${YELLOW}  • client build started (pid $CLIENT_PID, log: $CLIENT_LOG)${NC}"

    ./gradlew -Pprod -Pwar compileJava -x webapp >"$SERVER_LOG" 2>&1 &
    SERVER_PID=$!
    echo -e "${YELLOW}  • server compile started (pid $SERVER_PID, log: $SERVER_LOG)${NC}"

    set +e
    wait "$CLIENT_PID"; CLIENT_RC=$?
    wait "$SERVER_PID"; SERVER_RC=$?
    set -e
    if [ "$CLIENT_RC" -ne 0 ] || [ "$SERVER_RC" -ne 0 ]; then
        echo -e "${RED}Build failed (client rc=$CLIENT_RC, server rc=$SERVER_RC).${NC}"
        # Indirect expansion by name, without `${tag^^}`: macOS still ships bash 3.2, where that is a syntax
        # error, and the failure replaced the build output with "bad substitution" exactly when it was needed.
        for pair in "client:$CLIENT_LOG" "server:$SERVER_LOG"; do
            echo -e "${RED}--- last 50 lines of ${pair%%:*} log ---${NC}"
            tail -n 50 "${pair#*:}" 2>/dev/null || true
        done
        exit 1
    fi
    echo -e "${GREEN}  ✓ client + server built; assembling WAR...${NC}"
    # Hand the bundle to Gradle via build/webapp-dist instead of leaving it at the Angular config's
    # default build/resources/main/static. That directory is processResources' declared output, and
    # Gradle deletes every declared task-output directory it does not already recognize from a previous
    # build in this workspace *before* any task runs. In a fresh worktree (or any workspace where no
    # build has run processResources yet) that wiped the bundle we had just built, and bootWar then
    # produced a WAR with no client — the sanity check below caught it, costing a full rebuild cycle.
    # build/webapp-dist is not declared as any task's output, so it survives the cleanup, and the
    # `webapp` task (gradle/profile_prod.gradle registers a copy-only variant when that directory
    # exists) materialises it into place as part of the tracked task graph. This mirrors what CI's
    # build-war job does; note the deliberate absence of `-x webapp` below, which is what lets that
    # copy run. See .github/workflows/ci-build.yml and gradle/profile_prod.gradle for the rationale.
    rm -rf build/webapp-dist
    mv build/resources/main/static build/webapp-dist
    # The handoff directory must not outlive this build, on ANY exit path. gradle/profile_prod.gradle
    # picks the copy-only `webapp` task at CONFIGURATION time based on build/webapp-dist existing, so a
    # leftover copy would (a) make any later `-Pprod` build silently package this stale bundle instead of
    # rebuilding the client, and (b) break `./gradlew -Pprod -Pwar clean bootWar` — which
    # run-e2e-tests-local-multinode.sh and the documented production build both use — because `clean`
    # deletes the task's declared input. A trap rather than a trailing `rm` because `set -e` would skip
    # the latter whenever the assembly below fails, which is exactly when the stale copy is left behind.
    trap 'rm -rf build/webapp-dist' EXIT
    # bootWar without `-Psbom` skips the SBOM dependency chain entirely; the
    # cyclonedxBom and generateClientSbom tasks are not wired into copySbomsToResources.
    ./gradlew -Pprod -Pwar bootWar -x test
    rm -rf build/webapp-dist
    trap - EXIT
else
    echo ""
    echo -e "${YELLOW}Step 1: Skipping WAR build (--skip-build)${NC}"
fi

# Resolve the WAR for the *current* build.gradle version rather than picking the first
# lexicographic match from build/libs. We do not `gradle clean` in the fast path (that defeats
# fast iteration), so stale artifacts from older releases stick around — and the new
# `major.patch` scheme makes alphabetical ordering unsafe: e.g. `Artemis-9.1.2.war` sorts before
# `Artemis-9.2.war`. A stale WAR built from pre-PR-#12695 sources still uses the old
# `new Semver(currentVersionString)` migration code, which then dies on startup with
# `SemverException: Invalid version (no patch version): 9.2` when the persisted DB carries a
# two-part version. Resolve the WAR after the build so a freshly produced artifact is picked up.
ARTEMIS_VERSION=$(grep -E '^[[:space:]]*version[[:space:]]*=' build.gradle | head -1 | sed -E 's/.*"([^"]+)".*/\1/')
if [ -z "$ARTEMIS_VERSION" ]; then
    echo -e "${RED}ERROR: Could not determine Artemis version from build.gradle${NC}"
    exit 1
fi
WAR_FILE="build/libs/Artemis-${ARTEMIS_VERSION}.war"
if [ ! -e "$WAR_FILE" ]; then
    echo -e "${RED}ERROR: Expected WAR not found: $WAR_FILE${NC}"
    echo "Drop --skip-build to build it, or delete stale build/libs/Artemis-*.war from prior versions."
    exit 1
fi

# Sanity-check the Angular bundle is in the WAR (nginx serves it from there). Without -Pprod the
# bootWar task may produce a JSP-less, asset-less artifact.
if ! unzip -l "$WAR_FILE" | grep -q 'WEB-INF/classes/static/index.html'; then
    echo -e "${RED}ERROR: $WAR_FILE does not contain WEB-INF/classes/static/index.html.${NC}"
    echo "Re-build with: ./gradlew -Pprod -Pwar bootWar -x test"
    exit 1
fi
echo -e "${GREEN}Using WAR: $WAR_FILE${NC}"

# =============================================================================
# Step 2: Bring up infra containers (postgres + eureka + activemq)
# =============================================================================
if [ "$SKIP_UP" = false ]; then
    echo ""
    echo -e "${BLUE}Step 2: Starting infra containers (postgres, activemq-broker, and the registry only on Hazelcast)...${NC}"
    # The JHipster registry exists to locate Hazelcast members, so a Redis stack does not start it at all. Running it
    # anyway would hide the very thing worth proving here: that a Redis deployment needs no service registry.
    INFRA_SERVICES=(postgres activemq-broker)
    if [ "$MIDDLEWARE" != "redis" ]; then
        INFRA_SERVICES+=(jhipster-registry)
    fi
    docker compose "${COMPOSE_ARGS[@]}" up -d "${INFRA_SERVICES[@]}" "${MIDDLEWARE_SERVICES[@]}"

    echo "Waiting for Postgres..."
    TIMEOUT=120; ELAPSED=0
    until docker exec artemis-postgres pg_isready -U Artemis -d Artemis >/dev/null 2>&1; do
        [ $ELAPSED -ge $TIMEOUT ] && { echo -e "${RED}Postgres not ready after ${TIMEOUT}s${NC}"; exit 1; }
        sleep 2; ELAPSED=$((ELAPSED + 2))
    done
    echo -e "${GREEN}Postgres ready (${ELAPSED}s)${NC}"

    if [ "$MIDDLEWARE" = "redis" ]; then
        echo -e "${GREEN}No Eureka registry on Redis — nothing reads it once Hazelcast is out of the picture.${NC}"
    else
        echo "Waiting for Eureka registry..."
        TIMEOUT=180; ELAPSED=0
        until curl -sf http://localhost:8761/actuator/health >/dev/null 2>&1; do
            [ $ELAPSED -ge $TIMEOUT ] && { echo -e "${RED}Eureka not ready after ${TIMEOUT}s${NC}"; exit 1; }
            sleep 2; ELAPSED=$((ELAPSED + 2))
        done
        echo -e "${GREEN}Eureka ready (${ELAPSED}s)${NC}"
    fi
else
    echo ""
    echo -e "${YELLOW}Step 2: Skipping infra (--skip-up). Assuming postgres/eureka/activemq are running.${NC}"
fi

# =============================================================================
# Step 3: Detect Docker socket + ARM
# =============================================================================
if [ -S "/var/run/docker.sock" ]; then
    DOCKER_SOCK="/var/run/docker.sock"
elif [ -S "$HOME/.docker/run/docker.sock" ]; then
    DOCKER_SOCK="$HOME/.docker/run/docker.sock"
else
    echo -e "${YELLOW}WARNING: Could not find Docker socket; LocalCI builds may fail${NC}"
    DOCKER_SOCK="/var/run/docker.sock"
fi

ARM_OVERRIDES=""
HOST_ARCH=$(uname -m)
# `arm64` on macOS Apple Silicon, `aarch64` on Linux ARM. Both need the arm64 image override.
if [ "$HOST_ARCH" = "arm64" ] || [ "$HOST_ARCH" = "aarch64" ]; then
    ARM_OVERRIDES="export ARTEMIS_CONTINUOUSINTEGRATION_IMAGEARCHITECTURE=arm64"
    echo "Detected ARM64 (uname -m=$HOST_ARCH) — exercise images will use arm64 variants"
fi

# =============================================================================
# Step 3b: Create the shared data directory tree the application would normally
# write to /opt/artemis/data inside the Docker image. The `docker` Spring profile
# (active on all 3 nodes) hardcodes /opt/artemis/data/* paths in
# application-docker.yml — these must be overridden to a host-writable path or
# course-icon uploads, file-upload exercises, LocalVC pushes etc. all 500.
#
# Mirror the docker-named-volume model where ALL nodes share the same data tree
# (LocalVC bare repos pushed by node-2 must be readable by node-1 etc.).
# =============================================================================
ARTEMIS_DATA_DIR="$(pwd)/$LOCAL_DIR/data"
mkdir -p \
    "$ARTEMIS_DATA_DIR/course-archives" \
    "$ARTEMIS_DATA_DIR/repos" \
    "$ARTEMIS_DATA_DIR/repos-download" \
    "$ARTEMIS_DATA_DIR/uploads" \
    "$ARTEMIS_DATA_DIR/exports" \
    "$ARTEMIS_DATA_DIR/legal" \
    "$ARTEMIS_DATA_DIR/build-logs" \
    "$ARTEMIS_DATA_DIR/local-vcs-repos"

# =============================================================================
# Step 4: Launch 3 Artemis JVMs
# =============================================================================
# Admin credentials of the stack, used by the cluster preflight login below and by Playwright. They mirror
# docker/artemis/config/prod-multinode-fast.env, which cannot use the published `artemis_admin` password because the prod
# profile refuses to start on it.
export ADMIN_USERNAME="artemis_admin"
export ADMIN_PASSWORD="local-e2e-admin-not-a-deployment-credential"

# A JWT signing key committed to the repository would be one anyone can use to forge a token, so it is generated per run.
# Exported here rather than inside launch_node, which runs in a subshell per node: all three nodes have to sign with the
# same key, or a token minted on one node is rejected by the next.
export ARTEMIS_E2E_JWT_SECRET="${ARTEMIS_E2E_JWT_SECRET:-$(openssl rand -base64 64 | tr -d '\n')}"

launch_node() {
    local n=$1
    local http_port=${HTTP_PORTS[$((n - 1))]}
    local log_file="$LOCAL_DIR/server-${n}.log"
    local pid_file="$LOCAL_DIR/server-${n}.pid"

    # Reuse a node only when it actually serves requests. A live PID is not enough: a JVM that is shutting
    # down still passes `kill -0` for several seconds, and reusing it means waiting on a health endpoint
    # that will never come up again. Anything else gets relaunched, after clearing the port it may hold.
    if [ "$REUSE_RUNNING_NODES" = true ] && node_port_is_healthy "$http_port"; then
        echo "node-${n} already serving on :${http_port}, reusing."
        return
    fi

    echo "Launching node-${n} (http :${http_port}) -> $log_file"

    (
        set -a
        # shellcheck disable=SC1091
        source docker/artemis/config/prod-multinode-fast.env
        # shellcheck disable=SC1090,SC1091
        source "docker/artemis/config/node${n}-fast.env"
        # Last, so the selected backend wins over anything the profile files set.
        # shellcheck disable=SC1090,SC1091
        source "docker/artemis/config/middleware-${MIDDLEWARE}.env"
        if [ "$MIDDLEWARE" = "redis" ]; then
            # The host JVMs reach the container through its published port. The client name is this node's identity for
            # the Redis provider, so it has to differ per node or the build agent cleanup sees one node instead of three.
            export SPRING_DATA_REDIS_HOST="localhost"
            export SPRING_DATA_REDIS_CLIENTNAME="artemis-node-${n}"
        fi
        set +a
        export ARTEMIS_CONTINUOUSINTEGRATION_DOCKERCONNECTIONURI="unix://$DOCKER_SOCK"
        eval "$ARM_OVERRIDES"

        # Override the /opt/artemis/data/* paths from application-docker.yml so the JVM writes to
        # the host-side shared data tree we just created. Without this, any endpoint that touches
        # the filesystem (course icon upload, file-upload exercise, LocalVC project create, ...)
        # fails with java.nio.file.AccessDeniedException: /opt/artemis.
        export ARTEMIS_COURSEARCHIVESPATH="$ARTEMIS_DATA_DIR/course-archives"
        export ARTEMIS_REPOCLONEPATH="$ARTEMIS_DATA_DIR/repos"
        export ARTEMIS_REPODOWNLOADCLONEPATH="$ARTEMIS_DATA_DIR/repos-download"
        export ARTEMIS_FILEUPLOADPATH="$ARTEMIS_DATA_DIR/uploads"
        export ARTEMIS_SUBMISSIONEXPORTPATH="$ARTEMIS_DATA_DIR/exports"
        export ARTEMIS_LEGALPATH="$ARTEMIS_DATA_DIR/legal"
        export ARTEMIS_BUILDLOGSPATH="$ARTEMIS_DATA_DIR/build-logs"
        export ARTEMIS_VERSIONCONTROL_LOCALVCSREPOPATH="$ARTEMIS_DATA_DIR/local-vcs-repos"

        # Run the JVM in UTC to match production servers (which run UTC) and the app's own
        # hibernate.jdbc.time_zone=UTC. On a developer machine in a non-UTC zone (e.g. CEST) the
        # default JVM zone otherwise shifts ZonedDateTime values by the offset on DB round-trips,
        # pushing date-gated logic — such as a programming exercise's "Run Tests after Due Date"
        # date — hours into the future and breaking date-sensitive E2E tests only locally.
        if [ "$DEBUG" = true ]; then
            exec java -Xmx2g -XX:+UseG1GC -Dfile.encoding=UTF-8 -Duser.timezone=UTC \
                 -jar "$WAR_FILE" 2>&1 | tee "$log_file"
        else
            exec java -Xmx2g -XX:+UseG1GC -Dfile.encoding=UTF-8 -Duser.timezone=UTC \
                 -jar "$WAR_FILE" > "$log_file" 2>&1
        fi
    ) &
    echo $! > "$pid_file"
}

# =============================================================================
# Step 4 / 5: Serial node startup
#
# Liquibase + the artemis_version row insert race if multiple JVMs initialise concurrently
# against the same database (PSQLException: duplicate key value violates unique constraint
# "artemis_version_pkey"). The Docker multi-node compose avoids this with
# `depends_on: artemis-app-node-1: condition: service_healthy`. We mirror that here by
# launching each node only after the previous one is reachable on /management/health/readiness.
# =============================================================================
wait_for_node() {
    local n=$1
    local port=${HTTP_PORTS[$((n - 1))]}
    local pid_file="$LOCAL_DIR/server-${n}.pid"
    local log_file="$LOCAL_DIR/server-${n}.log"
    local pid; pid=$(cat "$pid_file")

    # Readiness rather than the aggregate health: the aggregate turns DOWN (and the endpoint answers 503, which
    # `curl -sf` treats as a failure) whenever an external integration cannot be reached from a developer machine —
    # the push-notification relay is enough on its own. The node then serves requests perfectly well while the gate
    # waits out its full budget and aborts the run. Readiness covers what this wait is actually about: the node
    # accepting traffic.
    echo "Waiting for node-${n} on http://localhost:${port}/management/health/readiness ..."
    local TIMEOUT=420 ELAPSED=0
    until curl -sf "http://localhost:${port}/management/health/readiness" >/dev/null 2>&1; do
        if ! kill -0 "$pid" 2>/dev/null; then
            echo -e "${RED}ERROR: node-${n} (PID $pid) died. Last 20 lines of $log_file:${NC}"
            tail -20 "$log_file"
            exit 1
        fi
        if [ $ELAPSED -ge $TIMEOUT ]; then
            echo -e "${RED}ERROR: node-${n} not ready after ${TIMEOUT}s. Last 20 lines of $log_file:${NC}"
            tail -20 "$log_file"
            exit 1
        fi
        sleep 5; ELAPSED=$((ELAPSED + 5))
    done
    echo -e "${GREEN}node-${n} ready (${ELAPSED}s)${NC}"
}

wait_for_eureka_registration() {
    local instance_id=$1
    echo "Waiting for Eureka to publish ${instance_id} in its registry..."
    local TIMEOUT=60 ELAPSED=0
    until curl -s -u admin:admin "http://localhost:8761/eureka/apps/ARTEMIS" 2>/dev/null \
            | grep -q "<instanceId>${instance_id}</instanceId>"; do
        [ $ELAPSED -ge $TIMEOUT ] && {
            echo -e "${RED}WARNING: ${instance_id} not visible in Eureka after ${TIMEOUT}s; continuing anyway${NC}"
            return 0
        }
        sleep 2; ELAPSED=$((ELAPSED + 2))
    done
    # Add a small buffer so subsequent nodes' first registry fetch (5s default) picks up this one.
    sleep 5
    echo -e "${GREEN}${instance_id} visible in Eureka (${ELAPSED}s)${NC}"
}

echo ""
echo -e "${BLUE}Step 4: Launching 3 host JVMs (serial — Liquibase requires it)...${NC}"
for n in 1 2 3; do
    launch_node "$n"
    wait_for_node "$n"
    # Ensure the just-started node is visible in the Eureka registry before launching the next one.
    # Without this, node-N+1 forms a solo Hazelcast cluster because its initial registry fetch did
    # not yet include node-N (cache lag), and Hazelcast does not auto-merge two existing clusters.
    #
    # None of that applies on Redis: no node registers with Eureka there (RedisDiscoveryEnvironmentPostProcessor
    # turns discovery off everywhere), because the registry only ever existed to locate Hazelcast members. Nodes
    # find each other through the distributed node registry, which Step 4b waits for instead.
    if [ "$MIDDLEWARE" = "redis" ]; then
        echo "node-${n} does not register with Eureka on Redis (discovery is off; the cluster forms through Redis) — skipping."
    else
        wait_for_eureka_registration "Artemis:${n}"
    fi
done

# =============================================================================
# Step 4b: Wait for every core node to appear in the cluster
# =============================================================================
# The admin endpoint reads the distributed node registry, which is provider-neutral, so this wait works for either
# middleware. It matters most for Hazelcast: when 3 host JVMs come up sequentially they each form a solo cluster
# (TcpIpConfig is empty at HazelcastInstance creation; peers are added afterwards by HazelcastClusterManager from
# Eureka), and Hazelcast's split-brain MERGE task is what consolidates them. That task is configured at
# MERGE_FIRST_RUN_DELAY=30s + MERGE_NEXT_RUN_DELAY=30s in HazelcastConfiguration.configureSplitBrainProtection(). The
# slow runner happens to wait this long because Docker image+container startup takes longer than 60s; we need to wait it
# out explicitly. On Redis the nodes are visible after one heartbeat interval and this returns almost immediately.
echo ""
echo -e "${BLUE}Step 4b: Waiting for all core nodes to register (expected ${EXPECTED_CLUSTER_NODE_COUNT:-2})...${NC}"
EXPECTED_CLUSTER=${EXPECTED_CLUSTER_NODE_COUNT:-2}
TIMEOUT=180; ELAPSED=0
COOKIE=$(mktemp)
trap 'rm -f "$COOKIE"' EXIT

# Login via node-1 directly (HTTP, no nginx required for this preflight check).
curl -s -c "$COOKIE" -X POST 'http://localhost:8080/api/core/public/authenticate' \
    -H 'Content-Type: application/json' \
    -d "{\"username\":\"$ADMIN_USERNAME\",\"password\":\"$ADMIN_PASSWORD\",\"rememberMe\":true}" \
    -o /dev/null

while true; do
    SIZE=$(curl -s -b "$COOKIE" 'http://localhost:8080/api/core/admin/websocket/nodes' \
            | python3 -c 'import sys,json;
try:
    d=json.load(sys.stdin); print(len(d))
except Exception:
    print(0)' 2>/dev/null)
    [ "${SIZE:-0}" -ge "$EXPECTED_CLUSTER" ] && { echo -e "${GREEN}Cluster reached ${SIZE} members (${ELAPSED}s)${NC}"; break; }
    [ $ELAPSED -ge $TIMEOUT ] && { echo -e "${YELLOW}WARNING: cluster only reached ${SIZE:-0}/${EXPECTED_CLUSTER} members within ${TIMEOUT}s; running tests anyway${NC}"; break; }
    sleep 5; ELAPSED=$((ELAPSED + 5))
done

# =============================================================================
# Step 5: Start nginx LB (after upstreams are alive so DNS resolves cleanly)
# =============================================================================
if [ "$SKIP_UP" = false ]; then
    echo ""
    echo -e "${BLUE}Step 5: Starting nginx LB...${NC}"
    docker compose "${COMPOSE_ARGS[@]}" up -d nginx

    echo "Waiting for nginx to be healthy..."
    TIMEOUT=60; ELAPSED=0
    until docker inspect --format='{{.State.Health.Status}}' artemis-nginx 2>/dev/null | grep -q '^healthy$'; do
        [ $ELAPSED -ge $TIMEOUT ] && { echo -e "${YELLOW}WARNING: nginx healthcheck did not pass within ${TIMEOUT}s; continuing anyway${NC}"; break; }
        sleep 2; ELAPSED=$((ELAPSED + 2))
    done
    echo -e "${GREEN}nginx LB ready${NC}"
fi

# =============================================================================
# Step 6: Run Playwright (host mode, BASE_URL=https://localhost)
# =============================================================================
echo ""
echo -e "${BLUE}Step 6: Running Playwright tests...${NC}"

# WebAuthn/passkey requires a domain-name origin: the nodes derive their Relying Party ID from
# SERVER_URL (https://localhost, see docker/artemis/config/prod-multinode-fast.env), so the browser
# origin must also be https://localhost. An IP literal like 127.0.0.1 is rejected as an RP ID and
# breaks every passkey test (passkey data is replicated over Hazelcast, so this is real multi-node
# coverage we want). We still avoid the historical ::1-under-load ECONNREFUSED cascade — macOS
# resolves `localhost` to `::1` first but Docker publishes 443 on IPv4 — by mapping localhost ->
# 127.0.0.1 inside the browser (PW_BROWSER_HOST_RESOLVER_RULES). Connections stay on the IPv4 port
# Docker publishes while the origin/RP ID remains a valid domain.
export BASE_URL="https://localhost"
export PW_BROWSER_HOST_RESOLVER_RULES="MAP localhost 127.0.0.1"
export NODE_TLS_REJECT_UNAUTHORIZED=0  # nginx self-signed cert
export EXERCISE_REPO_DIRECTORY="test-exercise-repos"
export TEST_WORKERS="${TEST_WORKERS:-${FAST_SLOW_WORKERS:-4}}"
export TEST_RETRIES="${TEST_RETRIES:-1}"
export FAST_TEST_TIMEOUT_SECONDS="${FAST_TEST_TIMEOUT_SECONDS:-60}"
export SLOW_TEST_TIMEOUT_SECONDS="${SLOW_TEST_TIMEOUT_SECONDS:-180}"
export BUILD_RESULT_TIMEOUT_MS="${BUILD_RESULT_TIMEOUT_MS:-180000}"
export BUILD_FINISH_TIMEOUT_MS="${BUILD_FINISH_TIMEOUT_MS:-120000}"
export EXAM_DASHBOARD_TIMEOUT_MS="${EXAM_DASHBOARD_TIMEOUT_MS:-120000}"
# Activate the @multi-node project and tell ClusterFormation.spec.ts what topology to expect. The provider decides which
# node-identity shape the spec may assert: Hazelcast publishes `[host]:port`, Redis publishes a client name without a port.
export EXPECTED_CLUSTER_NODE_COUNT="2"
# node-2 and node-3 both run a build agent, each with its own short name (see node2-fast.env / node3-fast.env).
export EXPECTED_MIN_BUILD_AGENTS="2"
export DISTRIBUTED_DATA_PROVIDER="$MIDDLEWARE"
# Direct per-core-node URLs. The @multi-node specs that assert cross-node behaviour need to address one specific
# node rather than whichever the load balancer picks, which is the whole point of those assertions.
export MULTI_NODE_URLS="http://localhost:${HTTP_PORTS[0]},http://localhost:${HTTP_PORTS[1]}"

cd src/test/playwright
pnpm run playwright:setup-local 2>/dev/null

rm -f test-reports/results*.xml
rm -rf test-reports/monocart-report*/

# Positional args are the spec paths Playwright runs. Default to the whole e2e/ tree;
# --specs narrows it to an explicit set (word-split on purpose, the option is documented
# as a space-separated list). --grep filters by test title and composes with either.
BASE_ARGS=()
if [ -n "$TEST_SPECS" ]; then
    # shellcheck disable=SC2206  # deliberate word splitting: --specs is a space-separated list
    BASE_ARGS=($TEST_SPECS)
else
    BASE_ARGS=(e2e)
fi
if [ -n "$TEST_FILTER" ]; then
    BASE_ARGS+=(--grep "$TEST_FILTER")
fi
BASE_ARGS+=("${PLAYWRIGHT_EXTRA_ARGS[@]}")

TEST_START=$(date +%s)
EXIT_CODE=0
echo -e "${BLUE}Running fast/slow/multi-node tests with $TEST_WORKERS workers...${NC}"
export PLAYWRIGHT_TEST_TYPE="parallel"
TEST_CMD=(pnpm exec playwright test "${BASE_ARGS[@]}" \
          --project=fast-tests --project=slow-tests --project=multi-node-tests \
          --workers="$TEST_WORKERS")
echo "Running: ${TEST_CMD[*]}"
echo ""

set +e
"${TEST_CMD[@]}"
TEST_EXIT=$?
set -e
[ $TEST_EXIT -ne 0 ] && EXIT_CODE=$TEST_EXIT

TEST_END=$(date +%s)
TEST_DURATION=$((TEST_END - TEST_START))
TEST_MINS=$((TEST_DURATION / 60))
TEST_SECS=$((TEST_DURATION % 60))

cd ../../..

# =============================================================================
# Step 7: Report results
# =============================================================================
REPORT_DIR="src/test/playwright/test-reports"

XML_FILES=()
if [ -f "$REPORT_DIR/results.xml" ]; then
    XML_FILES=("$REPORT_DIR/results.xml")
else
    for f in "$REPORT_DIR"/results-parallel.xml "$REPORT_DIR"/results-multinode.xml "$REPORT_DIR"/results-sequential.xml; do
        [ -f "$f" ] && XML_FILES+=("$f")
    done
fi

TOTAL_TESTS=0; TOTAL_FAILURES=0; TOTAL_ERRORS=0; TOTAL_SKIPPED=0
for xml_file in "${XML_FILES[@]}"; do
    while IFS= read -r line; do
        tests=$(echo "$line"     | grep -o 'tests="[0-9]*"'    | grep -o '[0-9]*')
        failures=$(echo "$line"  | grep -o 'failures="[0-9]*"' | grep -o '[0-9]*')
        errors=$(echo "$line"    | grep -o 'errors="[0-9]*"'   | grep -o '[0-9]*')
        skipped=$(echo "$line"   | grep -o 'skipped="[0-9]*"'  | grep -o '[0-9]*')
        TOTAL_TESTS=$((TOTAL_TESTS + ${tests:-0}))
        TOTAL_FAILURES=$((TOTAL_FAILURES + ${failures:-0}))
        TOTAL_ERRORS=$((TOTAL_ERRORS + ${errors:-0}))
        TOTAL_SKIPPED=$((TOTAL_SKIPPED + ${skipped:-0}))
    done < <(grep '<testsuite ' "$xml_file")
done

TOTAL_PASSED=$((TOTAL_TESTS - TOTAL_FAILURES - TOTAL_ERRORS - TOTAL_SKIPPED))

echo ""
echo -e "${BLUE}========================================${NC}"
if [ $((TOTAL_FAILURES + TOTAL_ERRORS)) -eq 0 ] && [ $EXIT_CODE -eq 0 ]; then
    echo -e "${GREEN}  ALL TESTS PASSED (multi-node fast)${NC}"
else
    echo -e "${RED}  SOME TESTS FAILED (multi-node fast)${NC}"
fi
echo -e "${BLUE}========================================${NC}"
if [ $TOTAL_TESTS -gt 0 ]; then
    echo -e "  ${GREEN}Passed:${NC}  $TOTAL_PASSED"
    [ $((TOTAL_FAILURES + TOTAL_ERRORS)) -gt 0 ] && echo -e "  ${RED}Failed:${NC}  $((TOTAL_FAILURES + TOTAL_ERRORS))" || echo "  Failed:  0"
    [ $TOTAL_SKIPPED -gt 0 ] && echo "  Skipped: $TOTAL_SKIPPED"
    echo "  Total:   $TOTAL_TESTS"
    echo ""
    echo "  Playwright duration: ${TEST_MINS}m ${TEST_SECS}s"
fi

echo ""
echo -e "${BLUE}Stack is still running. Quick re-run (reuse everything):${NC}"
echo "  ./run-e2e-tests-local-multinode-fast.sh --skip-build --skip-up [--filter \"...\"]"
echo ""
echo -e "${BLUE}To stop:${NC}  ./run-e2e-tests-local-multinode-fast.sh --stop"

exit $EXIT_CODE
