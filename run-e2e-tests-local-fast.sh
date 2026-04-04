#!/bin/bash
set -e

# =============================================================================
# Fast Local E2E Test Runner for Artemis
# =============================================================================
# Runs Postgres in Docker, server and client on the host. Services stay running
# between test runs so re-runs only take seconds.
#
# Usage:
#   ./run-e2e-tests-local-fast.sh [options]
#
# Options:
#   --stop              Kill server, client, and database; exit
#   --filter <pattern>  Run only tests matching the pattern (e.g., "Quiz")
#   --skip-server       Reuse already-running server
#   --skip-client       Reuse already-running client
#   --skip-db           Reuse already-running Postgres
#   --headed            Run Playwright in headed mode
#   --ui                Open Playwright UI mode
#   --video             Enable video recording (off by default to save CPU)
#   --coverage          Enable coverage collection (off by default, requires extra memory)
#   --debug             Show server and client output inline (normally only in log files)
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
SKIP_SERVER=false
SKIP_CLIENT=false
SKIP_DB=false
DEBUG=false
TEST_FILTER=""
PLAYWRIGHT_EXTRA_ARGS=()
export PLAYWRIGHT_VIDEO_MODE="${PLAYWRIGHT_VIDEO_MODE:-off}"
export PLAYWRIGHT_COVERAGE="${PLAYWRIGHT_COVERAGE:-off}"

while [[ $# -gt 0 ]]; do
    case $1 in
        --stop) STOP=true; shift ;;
        --skip-server) SKIP_SERVER=true; shift ;;
        --skip-client) SKIP_CLIENT=true; shift ;;
        --skip-db) SKIP_DB=true; shift ;;
        --headed) PLAYWRIGHT_EXTRA_ARGS+=("--headed"); shift ;;
        --ui) PLAYWRIGHT_EXTRA_ARGS+=("--ui"); shift ;;
        --video) unset PLAYWRIGHT_VIDEO_MODE; shift ;;
        --coverage) unset PLAYWRIGHT_COVERAGE; export NODE_OPTIONS="${NODE_OPTIONS:+$NODE_OPTIONS }--max-old-space-size=8192"; shift ;;
        --debug) DEBUG=true; shift ;;
        --filter)
            if [[ -z "$2" || "${2:0:1}" == "-" ]]; then
                echo -e "${RED}ERROR: --filter requires a non-empty pattern argument${NC}"
                echo "Usage: --filter <pattern>"
                echo "Example: --filter \"Quiz\" or --filter \"ExamAssessment|SystemHealth\""
                exit 1
            fi
            TEST_FILTER="$2"
            shift 2
            ;;
        --help) head -25 "$0" | tail -21; exit 0 ;;
        *) echo -e "${RED}Unknown option: $1${NC}"; exit 1 ;;
    esac
done

cd "$(dirname "$0")"
LOCAL_DIR=".e2e-local"
COMPOSE_FILE="docker/e2e-local-fast-postgres.yml"

# Kill a process and all its children (portable, works on macOS and Linux)
kill_tree() {
    local pid=$1
    # Kill children first (pgrep -P finds child processes)
    for child in $(pgrep -P "$pid" 2>/dev/null); do
        kill_tree "$child"
    done
    kill "$pid" 2>/dev/null || true
}

# Ensures a required port is available before starting a service.
# If a leftover process (e.g. from a previous crashed run) occupies the port,
# it is automatically killed so the script can proceed without manual intervention.
# This is intentional: developers and CI agents should not have to manually hunt
# down stale processes every time they re-run E2E tests. Do NOT replace this with
# a simple "error and exit" — that was tried and reverted because it broke the
# hands-free workflow that this script is designed to provide.
check_port_available() {
    local port=$1
    local service_name=$2
    local listeners

    listeners=$(lsof -nP -iTCP:"$port" -sTCP:LISTEN 2>/dev/null || true)
    if [ -n "$listeners" ]; then
        echo -e "${YELLOW}Port ${port} (${service_name}) is in use — killing existing process...${NC}"
        # Extract PIDs from lsof output (skip header line) and kill them
        local pids
        pids=$(echo "$listeners" | awk 'NR>1 {print $2}' | sort -u)
        for pid in $pids; do
            echo "  Killing PID $pid..."
            kill_tree "$pid"
        done
        sleep 2
        # Verify port is now free after killing
        listeners=$(lsof -nP -iTCP:"$port" -sTCP:LISTEN 2>/dev/null || true)
        if [ -n "$listeners" ]; then
            echo -e "${RED}ERROR: Port ${port} is still in use after killing processes. Cannot start ${service_name}.${NC}"
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
    echo -e "${BLUE}Stopping all E2E services...${NC}"

    # Kill server
    if [ -f "$LOCAL_DIR/server.pid" ]; then
        SERVER_PID=$(cat "$LOCAL_DIR/server.pid")
        if kill -0 "$SERVER_PID" 2>/dev/null; then
            echo "Stopping server (PID $SERVER_PID)..."
            kill_tree "$SERVER_PID"
        fi
    fi

    # Kill client
    if [ -f "$LOCAL_DIR/client.pid" ]; then
        CLIENT_PID=$(cat "$LOCAL_DIR/client.pid")
        if kill -0 "$CLIENT_PID" 2>/dev/null; then
            echo "Stopping client (PID $CLIENT_PID)..."
            kill_tree "$CLIENT_PID"
        fi
    fi

    # Stop Postgres
    echo "Stopping Postgres..."
    docker compose --env-file .env -f "$COMPOSE_FILE" down -v 2>/dev/null || true

    rm -rf "$LOCAL_DIR"
    echo -e "${GREEN}All services stopped.${NC}"
    exit 0
fi

# =============================================================================
# Banner
# =============================================================================
echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}  Artemis Fast Local E2E Test Runner${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

# =============================================================================
# Step 0: Prerequisites
# =============================================================================
echo -e "${BLUE}Step 0: Checking prerequisites...${NC}"

MISSING=""
command -v docker >/dev/null 2>&1 || MISSING="$MISSING docker"
command -v java >/dev/null 2>&1   || MISSING="$MISSING java"
command -v node >/dev/null 2>&1   || MISSING="$MISSING node"
command -v npm >/dev/null 2>&1    || MISSING="$MISSING npm"

if [ -n "$MISSING" ]; then
    echo -e "${RED}ERROR: Missing required commands:$MISSING${NC}"
    exit 1
fi

mkdir -p "$LOCAL_DIR"
echo -e "${GREEN}Prerequisites OK${NC}"

# =============================================================================
# Step 1: Postgres
# =============================================================================
if [ "$SKIP_DB" = false ]; then
    echo ""
    echo -e "${BLUE}Step 1: Starting Postgres...${NC}"

    # Check if port 5432 is in use by something other than our container
    if lsof -i :5432 -sTCP:LISTEN >/dev/null 2>&1; then
        # Check if it's our container
        if ! docker ps --format '{{.Names}}' 2>/dev/null | grep -q '^artemis-postgres$'; then
            echo -e "${RED}ERROR: Port 5432 is already in use by another process${NC}"
            echo "Run: lsof -i :5432 to see what's using it"
            exit 1
        fi
    fi

    docker compose --env-file .env -f "$COMPOSE_FILE" down -v 2>/dev/null || true
    docker compose --env-file .env -f "$COMPOSE_FILE" up -d

    echo "Waiting for Postgres to be ready..."
    TIMEOUT=60
    ELAPSED=0
    until docker exec artemis-postgres pg_isready -U Artemis -d Artemis >/dev/null 2>&1; do
        if [ $ELAPSED -ge $TIMEOUT ]; then
            echo -e "${RED}ERROR: Postgres not ready after ${TIMEOUT}s${NC}"
            exit 1
        fi
        sleep 2
        ELAPSED=$((ELAPSED + 2))
    done
    echo -e "${GREEN}Postgres ready (${ELAPSED}s)${NC}"
else
    echo ""
    echo -e "${YELLOW}Step 1: Skipping Postgres (--skip-db)${NC}"
fi

# =============================================================================
# Step 2: Start server and client (in parallel)
# =============================================================================
NEED_WAIT_SERVER=false
NEED_WAIT_CLIENT=false

if [ "$SKIP_SERVER" = false ]; then
    echo ""
    echo -e "${BLUE}Step 2a: Starting server (bootRun)...${NC}"

    # Kill stale server from previous run
    if [ -f "$LOCAL_DIR/server.pid" ]; then
        OLD_PID=$(cat "$LOCAL_DIR/server.pid")
        if kill -0 "$OLD_PID" 2>/dev/null; then
            echo "Killing stale server (PID $OLD_PID)..."
            kill_tree "$OLD_PID"
            sleep 2
        fi
    fi

    check_port_available 8080 "Artemis server"
    check_port_available 7921 "local VC SSH server"

    # Server environment variables
    export SPRING_PROFILES_ACTIVE="artemis,scheduling,localvc,localci,buildagent,core,dev"
    export SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:5432/Artemis?sslmode=disable"
    export SPRING_DATASOURCE_USERNAME="Artemis"
    export SPRING_DATASOURCE_PASSWORD=""
    export SPRING_LIQUIBASE_CONTEXTS="dev,e2e"
    export ARTEMIS_BCRYPTSALTROUNDS="4"
    export ARTEMIS_USERMANAGEMENT_INTERNALADMIN_USERNAME="artemis_admin"
    export ARTEMIS_USERMANAGEMENT_INTERNALADMIN_PASSWORD="artemis_admin"
    export ARTEMIS_USERMANAGEMENT_USEEXTERNAL="false"
    export ARTEMIS_VERSIONCONTROL_URL="http://localhost:8080"
    export ARTEMIS_VERSIONCONTROL_USER="artemis_admin"
    export ARTEMIS_VERSIONCONTROL_PASSWORD="artemis_admin"
    export ARTEMIS_CONTINUOUSINTEGRATION_EMPTYCOMMITNECESSARY="true"
    export ARTEMIS_CONTINUOUSINTEGRATION_ARTEMISAUTHENTICATIONTOKENVALUE="demo"
    # Use minimal C Docker image for faster E2E builds (~2s vs ~5s)
    export ARTEMIS_CONTINUOUSINTEGRATION_BUILD_IMAGES_C_DEFAULT="ls1tum/artemis-c-minimal-docker:1.0.0"

    # Auto-detect Docker socket path (Docker Desktop on macOS uses ~/.docker/run/docker.sock)
    if [ -S "/var/run/docker.sock" ]; then
        DOCKER_SOCK="/var/run/docker.sock"
    elif [ -S "$HOME/.docker/run/docker.sock" ]; then
        DOCKER_SOCK="$HOME/.docker/run/docker.sock"
    else
        echo -e "${YELLOW}WARNING: Could not find Docker socket; builds may fail${NC}"
        DOCKER_SOCK="/var/run/docker.sock"
    fi
    export ARTEMIS_CONTINUOUSINTEGRATION_DOCKERCONNECTIONURI="unix://$DOCKER_SOCK"
    export ARTEMIS_GIT_NAME="artemis"
    export ARTEMIS_GIT_EMAIL="artemis@example.com"
    export ARTEMIS_VERSIONCONTROL_SSHHOSTKEYPATH="$(pwd)/src/test/playwright/ssh-keys"
    export ARTEMIS_VERSIONCONTROL_SSHPORT="7921"
    export ARTEMIS_TELEMETRY_ENABLED="false"
    export SERVER_URL="http://localhost:8080"
    export EUREKA_CLIENT_ENABLED="false"
    export INFO_TESTSERVER="true"

    # ARM64 Macs: use arm64 exercise images for LocalCI
    if [ "$(uname -m)" = "arm64" ]; then
        export ARTEMIS_CONTINUOUSINTEGRATION_IMAGEARCHITECTURE="arm64"
        echo "Detected ARM64 — using arm64 exercise images"
    fi

    # Start server in background
    if [ "$DEBUG" = true ]; then
        ./gradlew bootRun -x webapp > >(tee "$LOCAL_DIR/server.log") 2>&1 &
    else
        ./gradlew bootRun -x webapp > "$LOCAL_DIR/server.log" 2>&1 &
    fi
    SERVER_PID=$!
    echo "$SERVER_PID" > "$LOCAL_DIR/server.pid"
    echo "Server starting (PID $SERVER_PID), log: $LOCAL_DIR/server.log"
    NEED_WAIT_SERVER=true
else
    echo ""
    echo -e "${YELLOW}Step 2a: Skipping server (--skip-server)${NC}"
    if ! curl -sf http://localhost:8080/management/health >/dev/null 2>&1; then
        echo -e "${RED}WARNING: Server does not appear to be running at http://localhost:8080${NC}"
    fi
fi

if [ "$SKIP_CLIENT" = false ]; then
    echo ""
    echo -e "${BLUE}Step 2b: Starting client (npm start)...${NC}"

    # Kill stale client from previous run
    if [ -f "$LOCAL_DIR/client.pid" ]; then
        OLD_PID=$(cat "$LOCAL_DIR/client.pid")
        if kill -0 "$OLD_PID" 2>/dev/null; then
            echo "Killing stale client (PID $OLD_PID)..."
            kill_tree "$OLD_PID"
            sleep 2
        fi
    fi

    check_port_available 9000 "Angular client"

    if [ "$DEBUG" = true ]; then
        npm start > >(tee "$LOCAL_DIR/client.log") 2>&1 &
    else
        npm start > "$LOCAL_DIR/client.log" 2>&1 &
    fi
    CLIENT_PID=$!
    echo "$CLIENT_PID" > "$LOCAL_DIR/client.pid"
    echo "Client starting (PID $CLIENT_PID), log: $LOCAL_DIR/client.log"
    NEED_WAIT_CLIENT=true
else
    echo ""
    echo -e "${YELLOW}Step 2b: Skipping client (--skip-client)${NC}"
    if ! curl -sf http://localhost:9000 >/dev/null 2>&1; then
        echo -e "${RED}WARNING: Client does not appear to be running at http://localhost:9000${NC}"
    fi
fi

# =============================================================================
# Step 3: Wait for server and client to be ready
# =============================================================================
if [ "$NEED_WAIT_SERVER" = true ]; then
    echo ""
    echo "Waiting for server to be ready (this may take a few minutes on first run)..."
    TIMEOUT=300
    ELAPSED=0
    until curl -sf http://localhost:8080/management/health >/dev/null 2>&1; do
        if ! kill -0 "$SERVER_PID" 2>/dev/null; then
            echo -e "${RED}ERROR: Server process died. Check $LOCAL_DIR/server.log${NC}"
            tail -20 "$LOCAL_DIR/server.log"
            exit 1
        fi
        if [ $ELAPSED -ge $TIMEOUT ]; then
            echo -e "${RED}ERROR: Server not ready after ${TIMEOUT}s. Check $LOCAL_DIR/server.log${NC}"
            tail -20 "$LOCAL_DIR/server.log"
            exit 1
        fi
        sleep 5
        ELAPSED=$((ELAPSED + 5))
    done
    echo -e "${GREEN}Server ready (${ELAPSED}s)${NC}"
fi

if [ "$NEED_WAIT_CLIENT" = true ]; then
    echo "Waiting for client to be ready..."
    TIMEOUT=120
    ELAPSED=0
    until curl -sf http://localhost:9000 >/dev/null 2>&1; do
        if ! kill -0 "$CLIENT_PID" 2>/dev/null; then
            echo -e "${RED}ERROR: Client process died. Check $LOCAL_DIR/client.log${NC}"
            tail -20 "$LOCAL_DIR/client.log"
            exit 1
        fi
        if [ $ELAPSED -ge $TIMEOUT ]; then
            echo -e "${RED}ERROR: Client not ready after ${TIMEOUT}s. Check $LOCAL_DIR/client.log${NC}"
            tail -20 "$LOCAL_DIR/client.log"
            exit 1
        fi
        sleep 3
        ELAPSED=$((ELAPSED + 3))
    done
    echo -e "${GREEN}Client ready (${ELAPSED}s)${NC}"
fi

# =============================================================================
# Step 4: Run Playwright tests
# =============================================================================
echo ""
echo -e "${BLUE}Step 4: Running Playwright tests...${NC}"

# Playwright environment
export BASE_URL="http://localhost:9000"
export ADMIN_USERNAME="artemis_admin"
export ADMIN_PASSWORD="artemis_admin"
export ALLOW_GROUP_CUSTOMIZATION="true"
export STUDENT_GROUP_NAME="students"
export TUTOR_GROUP_NAME="tutors"
export EDITOR_GROUP_NAME="editors"
export INSTRUCTOR_GROUP_NAME="instructors"
export EXERCISE_REPO_DIRECTORY="test-exercise-repos"
export TEST_WORKERS="${TEST_WORKERS:-${FAST_SLOW_WORKERS:-6}}"
export TEST_RETRIES="${TEST_RETRIES:-1}"
export FAST_TEST_TIMEOUT_SECONDS="${FAST_TEST_TIMEOUT_SECONDS:-45}"
export SLOW_TEST_TIMEOUT_SECONDS="${SLOW_TEST_TIMEOUT_SECONDS:-90}"
export BUILD_RESULT_TIMEOUT_MS="${BUILD_RESULT_TIMEOUT_MS:-120000}"
export BUILD_FINISH_TIMEOUT_MS="${BUILD_FINISH_TIMEOUT_MS:-90000}"
export EXAM_DASHBOARD_TIMEOUT_MS="${EXAM_DASHBOARD_TIMEOUT_MS:-90000}"

cd src/test/playwright

# Install Chromium if needed
npm run playwright:setup-local 2>/dev/null

# Clean stale reports and coverage cache
rm -f test-reports/results*.xml
rm -rf test-reports/monocart-report*/

# =============================================================================
# CPU monitoring: sample every 2s to a CSV file for post-run analysis
# =============================================================================
CPU_LOG="$(cd ../../.. && pwd)/$LOCAL_DIR/cpu-usage.csv"
echo "timestamp,cpu_user,cpu_sys,cpu_idle,load_avg_1m,load_avg_5m,load_avg_15m" > "$CPU_LOG"

sample_cpu() {
    while true; do
        # macOS: top -l1 gives CPU percentages; sysctl gives load averages
        if [[ "$(uname)" == "Darwin" ]]; then
            CPU_LINE=$(top -l1 -n0 2>/dev/null | grep "CPU usage")
            CPU_USER=$(echo "$CPU_LINE" | sed -n 's/.*: \([0-9.]*\)% user.*/\1/p')
            CPU_SYS=$(echo "$CPU_LINE" | sed -n 's/.*, \([0-9.]*\)% sys.*/\1/p')
            CPU_IDLE=$(echo "$CPU_LINE" | sed -n 's/.*, \([0-9.]*\)% idle.*/\1/p')
            LOAD=$(sysctl -n vm.loadavg 2>/dev/null | tr -d '{}' | xargs)
            LOAD_1=$(echo "$LOAD" | awk '{print $1}')
            LOAD_5=$(echo "$LOAD" | awk '{print $2}')
            LOAD_15=$(echo "$LOAD" | awk '{print $3}')
        else
            # Linux: /proc/stat based
            read -r CPU_USER CPU_SYS CPU_IDLE <<< $(top -bn1 2>/dev/null | grep "Cpu(s)" | awk '{print $2, $4, $8}' | tr -d '%,')
            read -r LOAD_1 LOAD_5 LOAD_15 _ < /proc/loadavg
        fi
        echo "$(date +%H:%M:%S),${CPU_USER:-0},${CPU_SYS:-0},${CPU_IDLE:-0},${LOAD_1:-0},${LOAD_5:-0},${LOAD_15:-0}" >> "$CPU_LOG"
        sleep 2
    done
}

# Start CPU sampler in background
sample_cpu &
CPU_MONITOR_PID=$!

# Build base Playwright args
BASE_ARGS=(e2e)
if [ -n "$TEST_FILTER" ]; then
    BASE_ARGS+=(--grep "$TEST_FILTER")
fi
BASE_ARGS+=("${PLAYWRIGHT_EXTRA_ARGS[@]}")

TEST_START=$(date +%s)
EXIT_CODE=0

# --- Run all tests (fast + slow) in a single phase ---
echo -e "${BLUE}Running all tests with $TEST_WORKERS workers...${NC}"
export PLAYWRIGHT_TEST_TYPE="parallel"
TEST_CMD=(npx playwright test "${BASE_ARGS[@]}" --project=fast-tests --project=slow-tests --workers="$TEST_WORKERS")
echo "Running: ${TEST_CMD[*]}"
echo ""

set +e
"${TEST_CMD[@]}"
TEST_EXIT=$?
set -e

if [ $TEST_EXIT -ne 0 ]; then
    EXIT_CODE=$TEST_EXIT
fi

# Stop CPU monitoring
kill "$CPU_MONITOR_PID" 2>/dev/null || true
wait "$CPU_MONITOR_PID" 2>/dev/null || true

TEST_END=$(date +%s)
TEST_DURATION=$((TEST_END - TEST_START))
TEST_MINS=$((TEST_DURATION / 60))
TEST_SECS=$((TEST_DURATION % 60))

cd ../../..

# =============================================================================
# Step 5: Report results
# =============================================================================
REPORT_DIR="src/test/playwright/test-reports"

XML_FILES=()
if [ -f "$REPORT_DIR/results.xml" ]; then
    XML_FILES=("$REPORT_DIR/results.xml")
else
    for f in "$REPORT_DIR"/results-parallel.xml "$REPORT_DIR"/results-sequential.xml "$REPORT_DIR"/results.xml; do
        [ -f "$f" ] && XML_FILES+=("$f")
    done
fi

# Parse totals from JUnit XML
TOTAL_TESTS=0
TOTAL_FAILURES=0
TOTAL_ERRORS=0
TOTAL_SKIPPED=0

for xml_file in "${XML_FILES[@]}"; do
    while IFS= read -r line; do
        tests=$(echo "$line" | grep -o 'tests="[0-9]*"' | grep -o '[0-9]*')
        failures=$(echo "$line" | grep -o 'failures="[0-9]*"' | grep -o '[0-9]*')
        errors=$(echo "$line" | grep -o 'errors="[0-9]*"' | grep -o '[0-9]*')
        skipped=$(echo "$line" | grep -o 'skipped="[0-9]*"' | grep -o '[0-9]*')
        TOTAL_TESTS=$((TOTAL_TESTS + ${tests:-0}))
        TOTAL_FAILURES=$((TOTAL_FAILURES + ${failures:-0}))
        TOTAL_ERRORS=$((TOTAL_ERRORS + ${errors:-0}))
        TOTAL_SKIPPED=$((TOTAL_SKIPPED + ${skipped:-0}))
    done < <(grep '<testsuite ' "$xml_file")
done

TOTAL_PASSED=$((TOTAL_TESTS - TOTAL_FAILURES - TOTAL_ERRORS - TOTAL_SKIPPED))

# Print summary
echo ""
echo -e "${BLUE}========================================${NC}"
if [ $((TOTAL_FAILURES + TOTAL_ERRORS)) -eq 0 ] && [ $EXIT_CODE -eq 0 ]; then
    echo -e "${GREEN}  ALL TESTS PASSED${NC}"
else
    echo -e "${RED}  SOME TESTS FAILED${NC}"
fi
echo -e "${BLUE}========================================${NC}"

if [ $TOTAL_TESTS -gt 0 ]; then
    echo -e "  ${GREEN}Passed:${NC}  $TOTAL_PASSED"
    [ $((TOTAL_FAILURES + TOTAL_ERRORS)) -gt 0 ] && echo -e "  ${RED}Failed:${NC}  $((TOTAL_FAILURES + TOTAL_ERRORS))" || echo "  Failed:  0"
    [ $TOTAL_SKIPPED -gt 0 ] && echo "  Skipped: $TOTAL_SKIPPED"
    echo "  Total:   $TOTAL_TESTS"
    echo ""
    echo -e "  ${BLUE}Timing:${NC}"
    echo "  All tests ($TEST_WORKERS workers): ${TEST_MINS}m ${TEST_SECS}s"
    echo -e "${BLUE}----------------------------------------${NC}"

    # CPU usage summary from collected samples
    if [ -f "$LOCAL_DIR/cpu-usage.csv" ] && [ "$(wc -l < "$LOCAL_DIR/cpu-usage.csv")" -gt 1 ]; then
        echo ""
        echo -e "${BLUE}  CPU Usage Summary:${NC}"
        awk -F',' 'NR>1 {
            n++; u+=$2; s+=$3; i+=$4; l1+=$5
            if ($2+$3 > max_used) max_used=$2+$3
            if ($4 < min_idle || NR==2) min_idle=$4
        } END {
            if (n>0) {
                printf "  Avg CPU used:   %.1f%% (user: %.1f%%, sys: %.1f%%)\n", (u+s)/n, u/n, s/n
                printf "  Avg CPU idle:   %.1f%%\n", i/n
                printf "  Peak CPU used:  %.1f%% (min idle: %.1f%%)\n", max_used, min_idle
                printf "  Avg load (1m):  %.2f\n", l1/n
                printf "  Samples:        %d (every 2s)\n", n
            }
        }' "$LOCAL_DIR/cpu-usage.csv"

        # Parallelization headroom analysis
        NPROC=$(sysctl -n hw.ncpu 2>/dev/null || nproc 2>/dev/null || echo 8)
        echo ""
        echo -e "  ${BLUE}Parallelization analysis (${NPROC} CPU cores):${NC}"
        awk -F',' -v cores="$NPROC" 'NR>1 {
            n++; i+=$4
        } END {
            if (n>0) {
                avg_idle=i/n
                if (avg_idle > 40) {
                    printf "  ✓ Significant headroom (%.0f%% idle) — try more workers\n", avg_idle
                } else if (avg_idle > 20) {
                    printf "  ~ Moderate headroom (%.0f%% idle) — a few more workers may help\n", avg_idle
                } else {
                    printf "  ✗ CPU mostly saturated (%.0f%% idle) — more workers unlikely to help\n", avg_idle
                }
            }
        }' "$LOCAL_DIR/cpu-usage.csv"

        echo ""
        echo -e "  ${BLUE}Full CPU log:${NC} $LOCAL_DIR/cpu-usage.csv"
    fi

    # Show individual failed tests with their names from JUnit XML
    if [ $((TOTAL_FAILURES + TOTAL_ERRORS)) -gt 0 ]; then
        echo ""
        echo -e "${RED}Failed tests:${NC}"
        FAIL_NAMES=()
        for xml_file in "${XML_FILES[@]}"; do
            # Extract failed test case names: find <testcase> elements that contain <failure>
            # Use awk to pair testcase names with failure status
            while IFS= read -r tc_line; do
                tc_name=$(echo "$tc_line" | sed -n 's/.*name="\([^"]*\)".*/\1/p')
                tc_class=$(echo "$tc_line" | sed -n 's/.*classname="\([^"]*\)".*/\1/p')
                if [ -n "$tc_name" ]; then
                    echo -e "  ${RED}✗${NC} ${tc_class} > ${tc_name}"
                    # Collect unique test names for re-run suggestion
                    FAIL_NAMES+=("$tc_name")
                fi
            done < <(
                # Find testcase elements that have a <failure> child
                awk '/<testcase / { tc=$0 } /<failure/ && tc { print tc; tc="" }' "$xml_file"
            )
        done

        echo ""
        # Suggest re-run command for failed tests
        if [ ${#FAIL_NAMES[@]} -gt 0 ] && [ ${#FAIL_NAMES[@]} -le 5 ]; then
            # Build a grep pattern from failed test names (escape regex special chars)
            RERUN_PATTERN=""
            for name in "${FAIL_NAMES[@]}"; do
                escaped=$(echo "$name" | sed 's/[.[\*^$()+?{|]/\\&/g')
                if [ -z "$RERUN_PATTERN" ]; then
                    RERUN_PATTERN="$escaped"
                else
                    RERUN_PATTERN="$RERUN_PATTERN|$escaped"
                fi
            done
            echo -e "${BLUE}Re-run failed tests:${NC}"
            echo "  ./run-e2e-tests-local-fast.sh --skip-server --skip-client --skip-db --filter \"$RERUN_PATTERN\""
        fi

        echo ""
        echo -e "${BLUE}View HTML report:${NC}"
        echo "  cd src/test/playwright && npx playwright show-report test-reports/monocart-report"
    fi
else
    echo "  No JUnit test results found in $REPORT_DIR"
    echo "  Time: ${TEST_MINS}m ${TEST_SECS}s"
fi

echo ""
echo -e "${BLUE}Logs:${NC} $LOCAL_DIR/ (server.log, client.log, cpu-usage.csv)"
echo ""
echo -e "${BLUE}Services are still running. Quick re-run:${NC}"
echo "  ./run-e2e-tests-local-fast.sh --skip-server --skip-client --skip-db [--filter \"Test\"]"
echo ""
echo -e "${BLUE}To stop all services:${NC}"
echo "  ./run-e2e-tests-local-fast.sh --stop"

if [ $((TOTAL_FAILURES + TOTAL_ERRORS)) -gt 0 ]; then
    exit 1
fi
exit $EXIT_CODE
