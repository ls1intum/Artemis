#!/bin/bash
# =============================================================================
# Local E2E Test Execution Script
# =============================================================================
# This script is designed for running E2E tests locally on developer machines.
# It builds the Artemis Docker image from a pre-built WAR file and runs tests.
#
# Usage: ./execute-locally.sh <configuration> [test-filter]
#   configuration: postgres-localci (default), postgres, multi-node
#   test-filter: optional grep pattern to filter tests (e.g., "Quiz")
#
# Environment:
#   E2E_DEBUG=true    Show all Docker container output (default: only Playwright)
#   E2E_LOG_DIR=path  Directory to store logs (default: .e2e-local)
#
# Prerequisites:
#   - WAR file must exist in build/libs/
#   - Docker must be running
#   - Port 5432 must be free for PostgreSQL
# =============================================================================

set -e

CONFIGURATION=${1:-postgres-localci}
TEST_FILTER=$2
DB="postgres"
DEBUG="${E2E_DEBUG:-false}"
LOG_DIR="${E2E_LOG_DIR:-.e2e-local}"

echo "========================================"
echo "  Artemis E2E Local Test Runner"
echo "========================================"
echo "Configuration: $CONFIGURATION"
[ -n "$TEST_FILTER" ] && echo "Test Filter: $TEST_FILTER"
echo ""

# Determine compose file based on configuration
if [ "$CONFIGURATION" = "postgres" ]; then
    COMPOSE_FILE="playwright-E2E-tests-postgres.yml"
    BUILD_SERVICES=("artemis-app")
elif [ "$CONFIGURATION" = "postgres-localci" ]; then
    COMPOSE_FILE="playwright-E2E-tests-postgres-localci.yml"
    BUILD_SERVICES=("artemis-app")
elif [ "$CONFIGURATION" = "multi-node" ]; then
    COMPOSE_FILE="playwright-E2E-tests-multi-node.yml"
    BUILD_SERVICES=("artemis-app-node-1" "artemis-app-node-2" "artemis-app-node-3")
else
    echo "Invalid configuration. Choose: postgres, postgres-localci, or multi-node"
    exit 1
fi

echo "Compose file: $COMPOSE_FILE"

# Set hostname for server.url - MUST use "nginx" for SSH/Git operations to work
# The playwright container uses Docker's bridge network, so SSH URLs like
# ssh://git@nginx:7921/ can be resolved via Docker DNS to reach the nginx container,
# which then forwards to artemis-app. Using $(hostname) would fail because
# port 7921 is not exposed on the local machine.
export HOST_HOSTNAME="nginx"

# Set Docker tag (required by compose file, but we build locally so value doesn't matter)
export ARTEMIS_DOCKER_TAG="${ARTEMIS_DOCKER_TAG:-local}"
# Admin credentials configure the Artemis server (via docker/artemis/config/playwright.env)
# and are passed to the Playwright container (via docker/playwright.yml).
# Playwright test code uses matching hardcoded defaults in support/users.ts.
export ARTEMIS_ADMIN_USERNAME="${ARTEMIS_ADMIN_USERNAME:-artemis_admin}"
export ARTEMIS_ADMIN_PASSWORD="${ARTEMIS_ADMIN_PASSWORD:-artemis_admin}"
export TEST_TIMEOUT_SECONDS="${TEST_TIMEOUT_SECONDS:-360}"
export TEST_RETRIES="${TEST_RETRIES:-1}"
export TEST_WORKER_PROCESSES="${TEST_WORKER_PROCESSES:-4}"
export SLOW_TEST_TIMEOUT_SECONDS="${SLOW_TEST_TIMEOUT_SECONDS:-180}"
export FAST_TEST_TIMEOUT_SECONDS="${FAST_TEST_TIMEOUT_SECONDS:-75}"

# Set platform for ARM64 Macs (Apple Silicon)
# Build the Artemis app natively on ARM and tell LocalCI to use arm64 exercise images.
# Most exercise images (C, Java, Python) support arm64 natively for better performance.
if [ "$(uname -m)" = "arm64" ]; then
    export DOCKER_DEFAULT_PLATFORM="linux/arm64"
    export ARTEMIS_CONTINUOUSINTEGRATION_IMAGEARCHITECTURE="arm64"
    echo "Detected ARM64 architecture, using linux/arm64 for Artemis build and exercise images"
fi

# Change to docker directory
cd "$(dirname "$0")/../../docker"

# Clean up stale JUnit XML files from previous runs to avoid double-counting
rm -f ../src/test/playwright/test-reports/results*.xml

# Create override file for local test execution.
echo "Creating local test override..."
if [ -n "$TEST_FILTER" ]; then
    # With a filter, use a single npx command (--grep works across all projects)
    cat > playwright-local-override.yml << EOF
# AUTO-GENERATED - DO NOT COMMIT
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
    OVERRIDE_ARGS="-f playwright-local-override.yml"
else
    OVERRIDE_ARGS=""
fi

# Cleanup function
cleanup() {
    echo "Cleaning up temporary files..."
    rm -f playwright-local-override.yml
}
trap cleanup EXIT

# Pull required images (except artemis-app which we build)
echo ""
echo "Pulling Docker images..."
docker compose --env-file ../.env -f $COMPOSE_FILE pull $DB nginx 2>/dev/null || true

# Build Artemis image from external WAR file
echo ""
echo "Building Artemis Docker image from WAR file..."
docker compose --env-file ../.env -f $COMPOSE_FILE build \
    --build-arg WAR_FILE_STAGE=external_builder \
    --no-cache \
    --pull \
    "${BUILD_SERVICES[@]}"

# Ensure log directory exists (relative to project root)
mkdir -p "../$LOG_DIR"

# Run the tests
echo ""
echo "Starting containers and running tests..."
echo "This may take 10-30 minutes..."
echo ""

# Disable exit on error to capture exit code
set +e
if [ "$DEBUG" = true ]; then
    docker compose --env-file ../.env -f $COMPOSE_FILE $OVERRIDE_ARGS up --exit-code-from artemis-playwright
else
    # Only show Playwright output; other service logs are saved to the log directory
    docker compose --env-file ../.env -f $COMPOSE_FILE $OVERRIDE_ARGS up --attach artemis-playwright --exit-code-from artemis-playwright
fi
EXIT_CODE=$?
set -e

# Save all container logs for later inspection
docker compose --env-file ../.env -f $COMPOSE_FILE logs --no-color > "../$LOG_DIR/docker-compose.log" 2>&1 || true

# Test reports are in the mounted volume (no need to copy from container)
REPORT_DIR="../src/test/playwright/test-reports"

# Parse and display test summary from JUnit XML files
echo ""
echo "========================================"
echo "  TEST RESULTS SUMMARY"
echo "========================================"

TOTAL_TESTS=0
TOTAL_FAILURES=0
TOTAL_ERRORS=0
TOTAL_SKIPPED=0

# The test runner moves results-parallel.xml to results.xml after the run.
# Fall back to results-parallel.xml if the rename didn't happen.
XML_FILES=()
if [ -f "$REPORT_DIR/results.xml" ]; then
    XML_FILES=("$REPORT_DIR/results.xml")
elif [ -f "$REPORT_DIR/results-parallel.xml" ]; then
    XML_FILES=("$REPORT_DIR/results-parallel.xml")
fi

for xml_file in "${XML_FILES[@]}"; do
    # Extract and sum test counts from ALL testsuites in the JUnit XML
    # Each spec file creates a separate <testsuite> element
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

if [ $TOTAL_TESTS -gt 0 ]; then
    echo "  Total:   $TOTAL_TESTS tests"
    echo "  Passed:  $TOTAL_PASSED"
    echo "  Failed:  $TOTAL_FAILURES"
    echo "  Errors:  $TOTAL_ERRORS"
    echo "  Skipped: $TOTAL_SKIPPED"
    echo "========================================"

    # List failed tests if any
    if [ $((TOTAL_FAILURES + TOTAL_ERRORS)) -gt 0 ]; then
        echo ""
        echo "FAILED TESTS:"
        echo "-------------"
        for xml_file in "${XML_FILES[@]}"; do
            # Extract failed test names from failure message attributes
            # The message attribute contains the test file and name
            grep '<failure message=' "$xml_file" 2>/dev/null | \
                sed 's/.*message="\([^"]*\)".*/  - \1/' || true
        done
        echo ""
    fi
else
    echo "  No JUnit test results found in $REPORT_DIR"
    echo "========================================"
fi

# Use test results as primary indicator of pass/fail (not just exit code)
echo ""
if [ $((TOTAL_FAILURES + TOTAL_ERRORS)) -eq 0 ] && [ $EXIT_CODE -eq 0 ]; then
    echo "All tests passed!"
else
    echo "Tests failed! View HTML report:"
    echo "  cd src/test/playwright && npx playwright show-report test-reports/monocart-report"
fi

echo ""
echo "Full Docker logs: $LOG_DIR/docker-compose.log"

# Exit with failure if any tests failed
if [ $((TOTAL_FAILURES + TOTAL_ERRORS)) -gt 0 ]; then
    exit 1
fi
exit $EXIT_CODE
