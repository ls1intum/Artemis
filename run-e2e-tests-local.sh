#!/bin/bash
set -e

# =============================================================================
# Local E2E Test Runner for Artemis
# =============================================================================
# Usage:
#   ./run-e2e-tests-local.sh [options]
#
# Options:
#   --stop              Stop all Docker containers and volumes; exit
#   --skip-build        Skip building the WAR file (use existing one)
#   --skip-cleanup      Skip Docker cleanup before running
#   --filter <pattern>  Run only tests matching the pattern (e.g., "Quiz")
#   --debug             Show all Docker container output (default: only test results)
#   --db <postgres>       Select database for E2E tests (default: postgres)
#   --help              Show this help message
# =============================================================================

STOP=false
SKIP_BUILD=false
SKIP_CLEANUP=false
DEBUG=false
TEST_FILTER=""
DB_TYPE="postgres"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

while [[ $# -gt 0 ]]; do
    case $1 in
        --stop) STOP=true; shift ;;
        --skip-build) SKIP_BUILD=true; shift ;;
        --skip-cleanup) SKIP_CLEANUP=true; shift ;;
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
        --db)
            if [[ -z "$2" || "${2:0:1}" == "-" ]]; then
                echo -e "${RED}ERROR: --db requires a database value (postgres)${NC}"
                exit 1
            fi
            if [[ "$2" != "postgres" ]]; then
                echo -e "${RED}ERROR: invalid --db value: $2 (only postgres is supported)${NC}"
                exit 1
            fi
            DB_TYPE="$2"
            shift 2
            ;;
        --db=*)
            DB_TYPE="${1#*=}"
            if [[ "$DB_TYPE" != "postgres" ]]; then
                echo -e "${RED}ERROR: invalid --db value: $DB_TYPE (only postgres is supported)${NC}"
                exit 1
            fi
            shift
            ;;
        --help) head -17 "$0" | tail -12; exit 0 ;;
        *) echo -e "${RED}Unknown option: $1${NC}"; exit 1 ;;
    esac
done

cd "$(dirname "$0")"

# =============================================================================
# --stop: Tear down all Docker containers and volumes
# =============================================================================
if [ "$STOP" = true ]; then
    echo -e "${BLUE}Stopping all E2E Docker services...${NC}"
    cd docker
    for compose_file in playwright-E2E-tests-postgres-localci.yml playwright-E2E-tests-postgres.yml playwright-E2E-tests-multi-node.yml; do
        if [ -f "$compose_file" ]; then
            docker compose --env-file ../.env -f "$compose_file" down -v 2>/dev/null || true
        fi
    done
    cd ..
    docker volume rm artemis-postgres-data artemis-data 2>/dev/null || true
    echo -e "${GREEN}All services stopped.${NC}"
    exit 0
fi

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}  Artemis E2E Test Runner${NC}"
echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}  Database: ${DB_TYPE}${NC}"
echo ""

# Environment variables
export ARTEMIS_ADMIN_USERNAME="${ARTEMIS_ADMIN_USERNAME:-artemis_admin}"
export ARTEMIS_ADMIN_PASSWORD="${ARTEMIS_ADMIN_PASSWORD:-artemis_admin}"
export SPRING_LIQUIBASE_CONTEXTS="${SPRING_LIQUIBASE_CONTEXTS:-prod,e2e}"
# Timeouts matching CI values for reliable local execution
export TEST_TIMEOUT_SECONDS="${TEST_TIMEOUT_SECONDS:-150}"             # CI: 300
export TEST_RETRIES="${TEST_RETRIES:-1}"
export TEST_WORKER_PROCESSES="${TEST_WORKER_PROCESSES:-5}"
export SLOW_TEST_TIMEOUT_SECONDS="${SLOW_TEST_TIMEOUT_SECONDS:-180}"   # CI: 180
export FAST_TEST_TIMEOUT_SECONDS="${FAST_TEST_TIMEOUT_SECONDS:-45}"    # CI: 60
# Build timeouts match CI defaults to avoid flaky failures from slow local builds
export BUILD_RESULT_TIMEOUT_MS="${BUILD_RESULT_TIMEOUT_MS:-90000}"     # CI: 90000
export BUILD_FINISH_TIMEOUT_MS="${BUILD_FINISH_TIMEOUT_MS:-60000}"     # CI: 60000
export EXAM_DASHBOARD_TIMEOUT_MS="${EXAM_DASHBOARD_TIMEOUT_MS:-60000}" # CI: 60000

# Step 1: Build WAR
if [ "$SKIP_BUILD" = false ]; then
    echo -e "${BLUE}Step 1: Building WAR file...${NC}"
    ./gradlew -Pprod -Pwar clean bootWar -x test
    echo -e "${GREEN}Done${NC}"
else
    echo -e "${YELLOW}Step 1: Skipping build${NC}"
fi

# Check WAR exists
if ! ls build/libs/*.war 1> /dev/null 2>&1; then
    echo -e "${RED}ERROR: No WAR file in build/libs/. Run without --skip-build${NC}"
    exit 1
fi

# Step 2: Cleanup
if [ "$SKIP_CLEANUP" = false ]; then
    echo -e "${BLUE}Step 2: Cleaning Docker environment...${NC}"
    COMPOSE_FILE="playwright-E2E-tests-postgres-localci.yml"
    DB_VOLUME="artemis-postgres-data"
    cd docker
    docker compose --env-file ../.env -f "$COMPOSE_FILE" down -v 2>/dev/null || true
    cd ..
    docker volume rm "$DB_VOLUME" artemis-data 2>/dev/null || true
    echo -e "${GREEN}Done${NC}"
else
    echo -e "${YELLOW}Step 2: Skipping cleanup${NC}"
fi

# Step 3: Run tests
echo -e "${BLUE}Step 3: Running E2E tests...${NC}"
echo ""

CONFIGURATION="postgres-localci"

export E2E_DEBUG="$DEBUG"
export E2E_LOG_DIR=".e2e-local"

.ci/E2E-tests/execute-locally.sh "$CONFIGURATION" "$TEST_FILTER"

exit $?
