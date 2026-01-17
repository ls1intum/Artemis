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
#   --db <mysql|postgres> Select database for E2E tests (default: mysql)
#   --help              Show this help message
# =============================================================================

SKIP_BUILD=false
SKIP_CLEANUP=false
TEST_FILTER=""
DB_TYPE="mysql"

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
                echo -e "${RED}ERROR: --db requires a database value (mysql or postgres)${NC}"
                exit 1
            fi
            if [[ "$2" != "mysql" && "$2" != "postgres" ]]; then
                echo -e "${RED}ERROR: invalid --db value: $2 (use mysql or postgres)${NC}"
                exit 1
            fi
            DB_TYPE="$2"
            shift 2
            ;;
        --db=*)
            DB_TYPE="${1#*=}"
            if [[ "$DB_TYPE" != "mysql" && "$DB_TYPE" != "postgres" ]]; then
                echo -e "${RED}ERROR: invalid --db value: $DB_TYPE (use mysql or postgres)${NC}"
                exit 1
            fi
            shift
            ;;
        --help) head -15 "$0" | tail -10; exit 0 ;;
        *) echo -e "${RED}Unknown option: $1${NC}"; exit 1 ;;
    esac
done

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}  Artemis E2E Test Runner${NC}"
echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}  Database: ${DB_TYPE}${NC}"
echo ""

# Environment variables
export ARTEMIS_ADMIN_USERNAME="${ARTEMIS_ADMIN_USERNAME:-artemis_admin}"
export ARTEMIS_ADMIN_PASSWORD="${ARTEMIS_ADMIN_PASSWORD:-artemis_admin}"
export PLAYWRIGHT_USERNAME_TEMPLATE="${PLAYWRIGHT_USERNAME_TEMPLATE:-artemis_test_user_}"
export PLAYWRIGHT_PASSWORD_TEMPLATE="${PLAYWRIGHT_PASSWORD_TEMPLATE:-artemis_test_user_}"
export PLAYWRIGHT_CREATE_USERS="${PLAYWRIGHT_CREATE_USERS:-true}"
# Reduced timeouts for local execution (50% of CI values for faster feedback)
export TEST_TIMEOUT_SECONDS="${TEST_TIMEOUT_SECONDS:-150}"             # CI: 300
export TEST_RETRIES="${TEST_RETRIES:-1}"
export TEST_WORKER_PROCESSES="${TEST_WORKER_PROCESSES:-2}"
export SLOW_TEST_TIMEOUT_SECONDS="${SLOW_TEST_TIMEOUT_SECONDS:-105}"    # CI: 180
export FAST_TEST_TIMEOUT_SECONDS="${FAST_TEST_TIMEOUT_SECONDS:-45}"    # CI: 60
# Custom timeouts for page objects and commands (50% of CI defaults)
export BUILD_RESULT_TIMEOUT_MS="${BUILD_RESULT_TIMEOUT_MS:-45000}"     # CI: 90000
export BUILD_FINISH_TIMEOUT_MS="${BUILD_FINISH_TIMEOUT_MS:-30000}"     # CI: 60000
export EXAM_DASHBOARD_TIMEOUT_MS="${EXAM_DASHBOARD_TIMEOUT_MS:-30000}" # CI: 60000

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
    if [ "$DB_TYPE" = "postgres" ]; then
        COMPOSE_FILE="playwright-E2E-tests-postgres-localci.yml"
        DB_VOLUME="artemis-postgres-data"
    else
        COMPOSE_FILE="playwright-E2E-tests-mysql-localci.yml"
        DB_VOLUME="artemis-mysql-data"
    fi
    cd docker
    docker compose -f "$COMPOSE_FILE" down -v 2>/dev/null || true
    cd ..
    docker volume rm "$DB_VOLUME" artemis-data 2>/dev/null || true
    echo -e "${GREEN}Done${NC}"
else
    echo -e "${YELLOW}Step 2: Skipping cleanup${NC}"
fi

# Step 3: Run tests
echo -e "${BLUE}Step 3: Running E2E tests...${NC}"
echo ""

if [ "$DB_TYPE" = "postgres" ]; then
    CONFIGURATION="postgres-localci"
else
    CONFIGURATION="mysql-localci"
fi

.ci/E2E-tests/execute-locally.sh "$CONFIGURATION" "$TEST_FILTER"

exit $?
