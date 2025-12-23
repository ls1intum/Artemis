#!/bin/bash
set -e

# =============================================================================
# Local E2E Test Runner for Artemis
# =============================================================================
# Usage:
#   ./run-e2e-tests-local.sh [options]
#
# Options:
#   --skip-build        Skip building the WAR file (use existing one)
#   --skip-cleanup      Skip Docker cleanup before running
#   --filter <pattern>  Run only tests matching the pattern (e.g., "Quiz")
#   --help              Show this help message
# =============================================================================

SKIP_BUILD=false
SKIP_CLEANUP=false
TEST_FILTER=""

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

while [[ $# -gt 0 ]]; do
    case $1 in
        --skip-build) SKIP_BUILD=true; shift ;;
        --skip-cleanup) SKIP_CLEANUP=true; shift ;;
        --filter) TEST_FILTER="$2"; shift 2 ;;
        --help) head -15 "$0" | tail -10; exit 0 ;;
        *) echo -e "${RED}Unknown option: $1${NC}"; exit 1 ;;
    esac
done

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}  Artemis E2E Test Runner${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

# Environment variables
export ARTEMIS_ADMIN_USERNAME="${ARTEMIS_ADMIN_USERNAME:-artemis_admin}"
export ARTEMIS_ADMIN_PASSWORD="${ARTEMIS_ADMIN_PASSWORD:-artemis_admin}"
export PLAYWRIGHT_USERNAME_TEMPLATE="${PLAYWRIGHT_USERNAME_TEMPLATE:-artemis_test_user_}"
export PLAYWRIGHT_PASSWORD_TEMPLATE="${PLAYWRIGHT_PASSWORD_TEMPLATE:-artemis_test_user_}"
export PLAYWRIGHT_CREATE_USERS="${PLAYWRIGHT_CREATE_USERS:-true}"
export TEST_TIMEOUT_SECONDS="${TEST_TIMEOUT_SECONDS:-300}"
export TEST_RETRIES="${TEST_RETRIES:-1}"
export TEST_WORKER_PROCESSES="${TEST_WORKER_PROCESSES:-2}"
export SLOW_TEST_TIMEOUT_SECONDS="${SLOW_TEST_TIMEOUT_SECONDS:-300}"
export FAST_TEST_TIMEOUT_SECONDS="${FAST_TEST_TIMEOUT_SECONDS:-60}"

cd "$(dirname "$0")"

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
    cd docker
    docker compose -f playwright-E2E-tests-mysql-localci.yml down -v 2>/dev/null || true
    cd ..
    docker volume rm artemis-mysql-data artemis-data 2>/dev/null || true
    echo -e "${GREEN}Done${NC}"
else
    echo -e "${YELLOW}Step 2: Skipping cleanup${NC}"
fi

# Step 3: Run tests
echo -e "${BLUE}Step 3: Running E2E tests...${NC}"
echo ""

.ci/E2E-tests/execute-locally.sh mysql-localci "$TEST_FILTER"

exit $?
