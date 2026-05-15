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
#   - node-3 (buildagent only, hz client)    http :8082
#   - postgres   container                  127.0.0.1:5432
#   - jhipster-registry (Eureka) container          :8761
#   - activemq-broker container                     :61613
#   - nginx LB container                            :443 (HTTPS), :54321 (HTTP)
#
# Usage:
#   ./run-e2e-tests-local-multinode-fast.sh [options]
#
# Options:
#   --stop              Tear everything down (host JVMs + infra containers)
#   --filter <pattern>  Run only tests matching the pattern (e.g., "Quiz")
#   --skip-build        Reuse the existing WAR in build/libs (do not rebuild)
#   --skip-up           Reuse already-running infra containers and host JVMs
#   --debug             Tee server logs to stdout (normally only in log files)
#   --help              Show this help message
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
PLAYWRIGHT_EXTRA_ARGS=()
export PLAYWRIGHT_VIDEO_MODE="${PLAYWRIGHT_VIDEO_MODE:-off}"
export PLAYWRIGHT_COVERAGE="${PLAYWRIGHT_COVERAGE:-off}"

while [[ $# -gt 0 ]]; do
    case $1 in
        --stop) STOP=true; shift ;;
        --skip-build) SKIP_BUILD=true; shift ;;
        --skip-up) SKIP_UP=true; shift ;;
        --debug) DEBUG=true; shift ;;
        --filter)
            if [[ -z "$2" || "${2:0:1}" == "-" ]]; then
                echo -e "${RED}ERROR: --filter requires a non-empty pattern argument${NC}"
                echo "Usage: --filter <pattern>"
                exit 1
            fi
            TEST_FILTER="$2"
            shift 2
            ;;
        --help) head -36 "$0" | tail -32; exit 0 ;;
        *) echo -e "${RED}Unknown option: $1${NC}"; exit 1 ;;
    esac
done

cd "$(dirname "$0")"
LOCAL_DIR=".e2e-local-multinode-fast"
COMPOSE_FILE="docker/playwright-E2E-tests-multi-node-fast.yml"

# Per-node port allocation. Indexes match node1/node2/node3 below.
HTTP_PORTS=(8080 8081 8082)
HZ_PORTS=(5701 5702)            # node-3 has no Hazelcast bind port (client)
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

# Free a port if a leftover process holds it. Mirrors run-e2e-tests-local-fast.sh:92-117.
check_port_available() {
    local port=$1
    local service_name=$2
    local listeners
    listeners=$(lsof -nP -iTCP:"$port" -sTCP:LISTEN 2>/dev/null || true)
    if [ -n "$listeners" ]; then
        echo -e "${YELLOW}Port ${port} (${service_name}) is in use — killing existing process...${NC}"
        local pids
        pids=$(echo "$listeners" | awk 'NR>1 {print $2}' | sort -u)
        for pid in $pids; do
            echo "  Killing PID $pid..."
            kill_tree "$pid"
        done
        sleep 2
        listeners=$(lsof -nP -iTCP:"$port" -sTCP:LISTEN 2>/dev/null || true)
        if [ -n "$listeners" ]; then
            echo -e "${RED}ERROR: Port ${port} is still in use after killing processes.${NC}"
            echo "$listeners"
            exit 1
        fi
        echo -e "${GREEN}Port ${port} is now free.${NC}"
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
    docker compose --env-file .env -f "$COMPOSE_FILE" down -v 2>/dev/null || true
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

command -v bun >/dev/null 2>&1     || MISSING="$MISSING bun"
command -v unzip >/dev/null 2>&1   || MISSING="$MISSING unzip"
command -v lsof >/dev/null 2>&1    || MISSING="$MISSING lsof"
command -v pgrep >/dev/null 2>&1   || MISSING="$MISSING pgrep"
command -v python3 >/dev/null 2>&1 || MISSING="$MISSING python3"
command -v curl >/dev/null 2>&1    || MISSING="$MISSING curl"
if [ -n "$MISSING" ]; then
    echo -e "${RED}ERROR: Missing required commands:$MISSING${NC}"
    if [[ "$MISSING" == *bun* ]]; then
        echo -e "${RED}Install Bun via:${NC}"
        echo -e "${RED}    curl -fsSL https://bun.sh/install | bash${NC}"
        echo -e "${RED}or (macOS):${NC}"
        echo -e "${RED}    brew install oven-sh/bun/bun${NC}"
    fi
    exit 1
fi

mkdir -p "$LOCAL_DIR"

# Pre-clear ports we will claim. --skip-up still benefits from this (orphan from a prior crash).
for port in "${ALL_PORTS[@]}"; do
    check_port_available "$port" "Artemis host JVM"
done
echo -e "${GREEN}Prerequisites OK${NC}"

# =============================================================================
# Step 1: Build the WAR (unless --skip-build)
# =============================================================================
if [ "$SKIP_BUILD" = false ]; then
    echo ""
    echo -e "${BLUE}Step 1: Building WAR (./gradlew -Pprod -Pwar bootWar -x test)...${NC}"
    ./gradlew -Pprod -Pwar bootWar -x test
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
    echo -e "${BLUE}Step 2: Starting infra containers (postgres, jhipster-registry, activemq-broker)...${NC}"
    docker compose --env-file .env -f "$COMPOSE_FILE" up -d postgres jhipster-registry activemq-broker

    echo "Waiting for Postgres..."
    TIMEOUT=120; ELAPSED=0
    until docker exec artemis-postgres pg_isready -U Artemis -d Artemis >/dev/null 2>&1; do
        [ $ELAPSED -ge $TIMEOUT ] && { echo -e "${RED}Postgres not ready after ${TIMEOUT}s${NC}"; exit 1; }
        sleep 2; ELAPSED=$((ELAPSED + 2))
    done
    echo -e "${GREEN}Postgres ready (${ELAPSED}s)${NC}"

    echo "Waiting for Eureka registry..."
    TIMEOUT=180; ELAPSED=0
    until curl -sf http://localhost:8761/actuator/health >/dev/null 2>&1; do
        [ $ELAPSED -ge $TIMEOUT ] && { echo -e "${RED}Eureka not ready after ${TIMEOUT}s${NC}"; exit 1; }
        sleep 2; ELAPSED=$((ELAPSED + 2))
    done
    echo -e "${GREEN}Eureka ready (${ELAPSED}s)${NC}"
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
launch_node() {
    local n=$1
    local http_port=${HTTP_PORTS[$((n - 1))]}
    local log_file="$LOCAL_DIR/server-${n}.log"
    local pid_file="$LOCAL_DIR/server-${n}.pid"

    if [ "$SKIP_UP" = true ] && [ -f "$pid_file" ] && kill -0 "$(cat "$pid_file")" 2>/dev/null; then
        echo "node-${n} already running (PID $(cat "$pid_file")), reusing."
        return
    fi

    echo "Launching node-${n} (http :${http_port}) -> $log_file"

    (
        set -a
        # shellcheck disable=SC1091
        source docker/artemis/config/prod-multinode-fast.env
        # shellcheck disable=SC1091
        source "docker/artemis/config/node${n}-fast.env"
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

        if [ "$DEBUG" = true ]; then
            exec java -Xmx2g -XX:+UseG1GC -Dfile.encoding=UTF-8 \
                 -jar "$WAR_FILE" 2>&1 | tee "$log_file"
        else
            exec java -Xmx2g -XX:+UseG1GC -Dfile.encoding=UTF-8 \
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
# launching each node only after the previous one is reachable on /management/health.
# =============================================================================
wait_for_node() {
    local n=$1
    local port=${HTTP_PORTS[$((n - 1))]}
    local pid_file="$LOCAL_DIR/server-${n}.pid"
    local log_file="$LOCAL_DIR/server-${n}.log"
    local pid; pid=$(cat "$pid_file")

    echo "Waiting for node-${n} on http://localhost:${port}/management/health ..."
    local TIMEOUT=420 ELAPSED=0
    until curl -sf "http://localhost:${port}/management/health" >/dev/null 2>&1; do
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
    wait_for_eureka_registration "Artemis:${n}"
done

# =============================================================================
# Step 4b: Wait for Hazelcast split-brain merge
# =============================================================================
# When 3 host JVMs come up sequentially they each form a solo Hazelcast cluster (TcpIpConfig is
# empty at HazelcastInstance creation; peers are added afterwards by HazelcastClusterManager
# from Eureka). Hazelcast's split-brain MERGE task is what actually consolidates the solo
# clusters into one. It is configured at MERGE_FIRST_RUN_DELAY=30s + MERGE_NEXT_RUN_DELAY=30s
# in HazelcastConfiguration.configureSplitBrainProtection(). The slow runner happens to wait
# this long because Docker image+container startup takes longer than 60s; we need to wait it
# out explicitly. Poll node-1's cluster size via the admin API and wait until it reaches the
# expected count, with a generous timeout.
echo ""
echo -e "${BLUE}Step 4b: Waiting for Hazelcast split-brain merge (cluster size = ${EXPECTED_CLUSTER_NODE_COUNT:-2})...${NC}"
EXPECTED_CLUSTER=${EXPECTED_CLUSTER_NODE_COUNT:-2}
TIMEOUT=180; ELAPSED=0
COOKIE=$(mktemp)
trap 'rm -f "$COOKIE"' EXIT

# Login via node-1 directly (HTTP, no nginx required for this preflight check).
curl -s -c "$COOKIE" -X POST 'http://localhost:8080/api/core/public/authenticate' \
    -H 'Content-Type: application/json' \
    -d '{"username":"artemis_admin","password":"artemis_admin","rememberMe":true}' \
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
    docker compose --env-file .env -f "$COMPOSE_FILE" up -d nginx

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

# IPv4 explicit. macOS resolves `localhost` to `::1` first, but Docker publishes 443 only on
# IPv4 (no `::` binding). Under parallel test load this manifested as ECONNREFUSED on ::1:443
# and ECONNRESET cascades — the slow runner does not hit this because Playwright lives inside
# a container on the docker bridge and reaches nginx via `https://artemis-nginx`.
export BASE_URL="https://127.0.0.1"
export NODE_TLS_REJECT_UNAUTHORIZED=0  # nginx self-signed cert
export ADMIN_USERNAME="artemis_admin"
export ADMIN_PASSWORD="artemis_admin"
export ALLOW_GROUP_CUSTOMIZATION="true"
export STUDENT_GROUP_NAME="students"
export TUTOR_GROUP_NAME="tutors"
export EDITOR_GROUP_NAME="editors"
export INSTRUCTOR_GROUP_NAME="instructors"
export EXERCISE_REPO_DIRECTORY="test-exercise-repos"
export TEST_WORKERS="${TEST_WORKERS:-${FAST_SLOW_WORKERS:-4}}"
export TEST_RETRIES="${TEST_RETRIES:-1}"
export FAST_TEST_TIMEOUT_SECONDS="${FAST_TEST_TIMEOUT_SECONDS:-60}"
export SLOW_TEST_TIMEOUT_SECONDS="${SLOW_TEST_TIMEOUT_SECONDS:-180}"
export BUILD_RESULT_TIMEOUT_MS="${BUILD_RESULT_TIMEOUT_MS:-180000}"
export BUILD_FINISH_TIMEOUT_MS="${BUILD_FINISH_TIMEOUT_MS:-120000}"
export EXAM_DASHBOARD_TIMEOUT_MS="${EXAM_DASHBOARD_TIMEOUT_MS:-120000}"
# Activate the @multi-node project and tell HazelcastCluster.spec.ts what topology to expect.
export EXPECTED_CLUSTER_NODE_COUNT="2"
export EXPECTED_MIN_BUILD_AGENTS="1"

cd src/test/playwright
bun run playwright:setup-local 2>/dev/null

rm -f test-reports/results*.xml
rm -rf test-reports/monocart-report*/

BASE_ARGS=(e2e)
if [ -n "$TEST_FILTER" ]; then
    BASE_ARGS+=(--grep "$TEST_FILTER")
fi
BASE_ARGS+=("${PLAYWRIGHT_EXTRA_ARGS[@]}")

TEST_START=$(date +%s)
EXIT_CODE=0
echo -e "${BLUE}Running fast/slow/multi-node tests with $TEST_WORKERS workers...${NC}"
export PLAYWRIGHT_TEST_TYPE="parallel"
TEST_CMD=(bunx playwright test "${BASE_ARGS[@]}" \
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
