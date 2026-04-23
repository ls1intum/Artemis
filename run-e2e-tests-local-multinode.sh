#!/bin/bash
set -e

# =============================================================================
# Local Multi-Node E2E Test Runner for Artemis
# =============================================================================
# Mirrors the UX of run-e2e-tests-local-fast.sh but boots a full
# production-faithful multi-node stack so that clustered Hazelcast L2 cache,
# ActiveMQ STOMP relay, and round-robin LB behaviour are actually exercised.
#
# Stack:
#   - Postgres
#   - JHipster Registry (Eureka) for Hazelcast member discovery
#   - ActiveMQ broker for STOMP relay
#   - 3 Artemis nodes (node-1 / node-2 / node-3), forming a real Hazelcast cluster
#   - nginx load balancer (round-robin) in front of the nodes
#   - Playwright runs in a container inside the same docker network
#
# Compose file reused from CI: docker/playwright-E2E-tests-multi-node.yml
#
# Usage:
#   ./run-e2e-tests-local-multinode.sh [options]
#
# Options:
#   --stop              Tear down the full multi-node stack and exit
#   --filter <pattern>  Run only tests matching the pattern (e.g., "Quiz")
#   --skip-build        Do not rebuild the Artemis WAR or Docker image
#                         (reuse build/libs/*.war + the cached node images)
#   --skip-up           Reuse already-running containers; only re-run Playwright
#   --debug             Show all docker-compose output instead of only Playwright
#   --help              Show this help message
# =============================================================================

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

STOP=false
SKIP_BUILD=false
SKIP_UP=false
DEBUG=false
TEST_FILTER=""

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
                echo "Example: --filter \"Quiz\" or --filter \"ExamAssessment|SystemHealth\""
                exit 1
            fi
            TEST_FILTER="$2"
            shift 2
            ;;
        --help) head -35 "$0" | tail -31; exit 0 ;;
        *) echo -e "${RED}Unknown option: $1${NC}"; exit 1 ;;
    esac
done

cd "$(dirname "$0")"
LOCAL_DIR=".e2e-local-multinode"
COMPOSE_FILE="docker/playwright-E2E-tests-multi-node.yml"
REPORT_DIR="src/test/playwright/test-reports"

mkdir -p "$LOCAL_DIR"

# =============================================================================
# --stop: Tear everything down
# =============================================================================
if [ "$STOP" = true ]; then
    echo -e "${BLUE}Stopping multi-node E2E stack...${NC}"
    docker compose --env-file .env -f "$COMPOSE_FILE" down -v 2>/dev/null || true
    docker volume rm artemis-postgres-data artemis-data 2>/dev/null || true
    rm -f docker/playwright-local-override.yml
    rm -rf "$LOCAL_DIR"
    echo -e "${GREEN}Multi-node stack stopped.${NC}"
    exit 0
fi

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}  Artemis Multi-Node E2E Runner${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

# =============================================================================
# Prerequisites
# =============================================================================
echo -e "${BLUE}Step 0: Checking prerequisites...${NC}"
MISSING=""
command -v docker >/dev/null 2>&1 || MISSING="$MISSING docker"
command -v java   >/dev/null 2>&1 || MISSING="$MISSING java"
if [ -n "$MISSING" ]; then
    echo -e "${RED}ERROR: Missing required commands:$MISSING${NC}"
    exit 1
fi

# CI env vars (copied from .ci/E2E-tests/execute-locally.sh for parity)
# HOST_HOSTNAME must be "nginx" because the playwright container resolves the
# Artemis URL via Docker DNS inside the artemis network.
export HOST_HOSTNAME="nginx"
export ARTEMIS_DOCKER_TAG="${ARTEMIS_DOCKER_TAG:-local}"
export ARTEMIS_ADMIN_USERNAME="${ARTEMIS_ADMIN_USERNAME:-artemis_admin}"
export ARTEMIS_ADMIN_PASSWORD="${ARTEMIS_ADMIN_PASSWORD:-artemis_admin}"
export TEST_TIMEOUT_SECONDS="${TEST_TIMEOUT_SECONDS:-360}"
export TEST_RETRIES="${TEST_RETRIES:-1}"
export TEST_WORKER_PROCESSES="${TEST_WORKER_PROCESSES:-4}"
export SLOW_TEST_TIMEOUT_SECONDS="${SLOW_TEST_TIMEOUT_SECONDS:-180}"
export FAST_TEST_TIMEOUT_SECONDS="${FAST_TEST_TIMEOUT_SECONDS:-75}"

if [ "$(uname -m)" = "arm64" ]; then
    export DOCKER_DEFAULT_PLATFORM="linux/arm64"
    export ARTEMIS_CONTINUOUSINTEGRATION_IMAGEARCHITECTURE="arm64"
    echo "Detected ARM64 — using linux/arm64 Artemis build and arm64 exercise images"
fi

echo -e "${GREEN}Prerequisites OK${NC}"

# =============================================================================
# Step 1: Build the Artemis WAR (source of truth for the multi-node image)
# =============================================================================
if [ "$SKIP_BUILD" = false ] && [ "$SKIP_UP" = false ]; then
    echo ""
    echo -e "${BLUE}Step 1: Building Artemis WAR (./gradlew -Pprod -Pwar bootWar -x test)...${NC}"
    ./gradlew -Pprod -Pwar clean bootWar -x test
    echo -e "${GREEN}WAR built${NC}"
else
    echo ""
    echo -e "${YELLOW}Step 1: Skipping WAR build${NC}"
fi

if ! ls build/libs/*.war >/dev/null 2>&1; then
    echo -e "${RED}ERROR: No WAR file in build/libs/. Run without --skip-build.${NC}"
    exit 1
fi

# =============================================================================
# Step 2: Build the Artemis Docker image from the WAR (used by all 3 nodes)
# =============================================================================
if [ "$SKIP_BUILD" = false ] && [ "$SKIP_UP" = false ]; then
    echo ""
    echo -e "${BLUE}Step 2: Building Artemis Docker image from WAR...${NC}"
    docker compose --env-file .env -f "$COMPOSE_FILE" build \
        --build-arg WAR_FILE_STAGE=external_builder \
        --pull \
        artemis-app-node-1 artemis-app-node-2 artemis-app-node-3
    echo -e "${GREEN}Image built${NC}"
else
    echo ""
    echo -e "${YELLOW}Step 2: Skipping image build${NC}"
fi

# =============================================================================
# Step 3: Bring up the multi-node stack
# =============================================================================
if [ "$SKIP_UP" = false ]; then
    echo ""
    echo -e "${BLUE}Step 3: Starting multi-node stack (postgres + registry + broker + 3 Artemis nodes + nginx)...${NC}"
    # Start everything except the playwright container; we run it separately so we can capture logs.
    docker compose --env-file .env -f "$COMPOSE_FILE" up -d \
        postgres jhipster-registry activemq-broker \
        artemis-app-node-1 artemis-app-node-2 artemis-app-node-3 \
        nginx

    echo ""
    echo "Waiting for all three Artemis nodes to become healthy (this may take several minutes on first run)..."
    TIMEOUT=600
    ELAPSED=0
    while true; do
        HEALTHY=0
        for n in 1 2 3; do
            status=$(docker inspect --format='{{.State.Health.Status}}' "artemis-app-node-$n" 2>/dev/null || echo "missing")
            if [ "$status" = "healthy" ]; then
                HEALTHY=$((HEALTHY + 1))
            fi
        done
        if [ $HEALTHY -eq 3 ]; then
            echo -e "${GREEN}All 3 Artemis nodes healthy (${ELAPSED}s)${NC}"
            break
        fi
        if [ $ELAPSED -ge $TIMEOUT ]; then
            echo -e "${RED}ERROR: Only $HEALTHY/3 Artemis nodes healthy after ${TIMEOUT}s${NC}"
            echo "Last 40 lines of each node's log:"
            for n in 1 2 3; do
                echo "--- artemis-app-node-$n ---"
                docker logs --tail 40 "artemis-app-node-$n" 2>&1 || true
            done
            exit 1
        fi
        sleep 10
        ELAPSED=$((ELAPSED + 10))
        echo "  ${ELAPSED}s — $HEALTHY/3 healthy"
    done

    # Additional Hazelcast-cluster sanity check: every node should see 3 members
    echo ""
    echo "Verifying Hazelcast cluster size (each node should report 3 members)..."
    for n in 1 2 3; do
        SIZE=$(docker logs "artemis-app-node-$n" 2>&1 | grep -oE "Members \{size:[0-9]+" | tail -1 | grep -oE "[0-9]+$" || echo "?")
        echo "  node-$n: cluster size = $SIZE"
    done
else
    echo ""
    echo -e "${YELLOW}Step 3: Skipping stack startup (--skip-up)${NC}"
fi

# =============================================================================
# Step 4: Run Playwright inside the artemis-playwright container
# =============================================================================
echo ""
echo -e "${BLUE}Step 4: Running Playwright multi-node tests...${NC}"

# Clean stale reports
rm -f "$REPORT_DIR"/results*.xml
rm -rf "$REPORT_DIR"/monocart-report*/

# With a --filter argument we need to override the default playwright command the
# artemis-playwright container would run. Mirror the pattern used by
# .ci/E2E-tests/execute-locally.sh.
OVERRIDE_ARGS=""
if [ -n "$TEST_FILTER" ]; then
    cat > docker/playwright-local-override.yml << EOF
# AUTO-GENERATED — DO NOT COMMIT
services:
    artemis-playwright:
        command: >
            sh -c '
            cd /app/artemis/src/test/playwright &&
            chmod 777 /root &&
            rm -f test-reports/results*.xml &&
            npm ci &&
            npm run playwright:setup &&
            PLAYWRIGHT_JUNIT_OUTPUT_NAME=test-reports/results.xml npx playwright test e2e --grep "${TEST_FILTER}" --reporter=list,junit,monocart-reporter
            '
EOF
    OVERRIDE_ARGS="-f docker/playwright-local-override.yml"
fi

cleanup() {
    rm -f docker/playwright-local-override.yml
}
trap cleanup EXIT

TEST_START=$(date +%s)
set +e
if [ "$DEBUG" = true ]; then
    docker compose --env-file .env -f "$COMPOSE_FILE" $OVERRIDE_ARGS up --exit-code-from artemis-playwright artemis-playwright
else
    docker compose --env-file .env -f "$COMPOSE_FILE" $OVERRIDE_ARGS up --attach artemis-playwright --exit-code-from artemis-playwright artemis-playwright
fi
TEST_EXIT=$?
set -e
TEST_END=$(date +%s)
TEST_DURATION=$((TEST_END - TEST_START))
TEST_MINS=$((TEST_DURATION / 60))
TEST_SECS=$((TEST_DURATION % 60))

# Archive container logs for post-run inspection
docker compose --env-file .env -f "$COMPOSE_FILE" logs --no-color > "$LOCAL_DIR/docker-compose.log" 2>&1 || true

# =============================================================================
# Step 5: Summarise results
# =============================================================================
XML_FILES=()
if [ -f "$REPORT_DIR/results.xml" ]; then
    XML_FILES=("$REPORT_DIR/results.xml")
else
    for f in "$REPORT_DIR"/results-parallel.xml "$REPORT_DIR"/results-sequential.xml; do
        [ -f "$f" ] && XML_FILES+=("$f")
    done
fi

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

echo ""
echo -e "${BLUE}========================================${NC}"
if [ $((TOTAL_FAILURES + TOTAL_ERRORS)) -eq 0 ] && [ $TEST_EXIT -eq 0 ]; then
    echo -e "${GREEN}  ALL TESTS PASSED (multi-node)${NC}"
else
    echo -e "${RED}  SOME TESTS FAILED (multi-node)${NC}"
fi
echo -e "${BLUE}========================================${NC}"

if [ $TOTAL_TESTS -gt 0 ]; then
    echo -e "  ${GREEN}Passed:${NC}  $TOTAL_PASSED"
    [ $((TOTAL_FAILURES + TOTAL_ERRORS)) -gt 0 ] && echo -e "  ${RED}Failed:${NC}  $((TOTAL_FAILURES + TOTAL_ERRORS))" || echo "  Failed:  0"
    [ $TOTAL_SKIPPED -gt 0 ] && echo "  Skipped: $TOTAL_SKIPPED"
    echo "  Total:   $TOTAL_TESTS"
    echo ""
    echo "  Playwright duration: ${TEST_MINS}m ${TEST_SECS}s"

    if [ $((TOTAL_FAILURES + TOTAL_ERRORS)) -gt 0 ]; then
        echo ""
        echo -e "${RED}Failed tests:${NC}"
        for xml_file in "${XML_FILES[@]}"; do
            while IFS= read -r tc_line; do
                tc_name=$(echo "$tc_line" | sed -n 's/.*name="\([^"]*\)".*/\1/p')
                tc_class=$(echo "$tc_line" | sed -n 's/.*classname="\([^"]*\)".*/\1/p')
                [ -n "$tc_name" ] && echo -e "  ${RED}✗${NC} ${tc_class} > ${tc_name}"
            done < <(awk '/<testcase / { tc=$0 } /<failure/ && tc { print tc; tc="" }' "$xml_file")
        done
        echo ""
        echo -e "${BLUE}Full container logs:${NC} $LOCAL_DIR/docker-compose.log"
        echo -e "${BLUE}HTML report:${NC} cd src/test/playwright && npx playwright show-report test-reports/monocart-report"
    fi
else
    echo "  No JUnit test results found in $REPORT_DIR"
fi

echo ""
echo -e "${BLUE}Stack is still running. Quick re-run (reuse everything):${NC}"
echo "  ./run-e2e-tests-local-multinode.sh --skip-build --skip-up [--filter \"Quiz\"]"
echo ""
echo -e "${BLUE}To stop the multi-node stack:${NC}"
echo "  ./run-e2e-tests-local-multinode.sh --stop"

if [ $((TOTAL_FAILURES + TOTAL_ERRORS)) -gt 0 ]; then
    exit 1
fi
exit $TEST_EXIT
