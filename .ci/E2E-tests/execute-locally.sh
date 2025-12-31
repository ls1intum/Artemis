#!/bin/bash
# =============================================================================
# Local E2E Test Execution Script
# =============================================================================
# This script is designed for running E2E tests locally on developer machines.
# It builds the Artemis Docker image from a pre-built WAR file and runs tests.
#
# Usage: ./execute-locally.sh <configuration> [test-filter]
#   configuration: mysql-localci (default), mysql, postgres, multi-node
#   test-filter: optional grep pattern to filter tests (e.g., "Quiz")
#
# Prerequisites:
#   - WAR file must exist in build/libs/
#   - Docker must be running
#   - Port 3306 must be free (stop local MySQL if running)
# =============================================================================

set -e

CONFIGURATION=${1:-mysql-localci}
TEST_FILTER=$2
DB="mysql"

echo "========================================"
echo "  Artemis E2E Local Test Runner"
echo "========================================"
echo "Configuration: $CONFIGURATION"
[ -n "$TEST_FILTER" ] && echo "Test Filter: $TEST_FILTER"
echo ""

# Determine compose file based on configuration
if [ "$CONFIGURATION" = "mysql" ]; then
    COMPOSE_FILE="playwright-E2E-tests-mysql.yml"
elif [ "$CONFIGURATION" = "postgres" ]; then
    COMPOSE_FILE="playwright-E2E-tests-postgres.yml"
    DB="postgres"
elif [ "$CONFIGURATION" = "mysql-localci" ]; then
    COMPOSE_FILE="playwright-E2E-tests-mysql-localci.yml"
elif [ "$CONFIGURATION" = "multi-node" ]; then
    COMPOSE_FILE="playwright-E2E-tests-multi-node.yml"
else
    echo "Invalid configuration. Choose: mysql, postgres, mysql-localci, or multi-node"
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

# Set platform for ARM64 Macs (Apple Silicon)
# Note: We keep DOCKER_DEFAULT_PLATFORM for building the Artemis app natively on ARM,
# but we do NOT set ARTEMIS_CONTINUOUSINTEGRATION_IMAGEARCHITECTURE. This allows LocalCI
# to use amd64 images (the default) which Docker Desktop can emulate via Rosetta.
# This is necessary because some exercise images (e.g., sharingcodeability/fact) only provide amd64.
if [ "$(uname -m)" = "arm64" ]; then
    export DOCKER_DEFAULT_PLATFORM="linux/arm64"
    echo "Detected ARM64 architecture, using linux/arm64 platform for Artemis build"
    echo "LocalCI will use amd64 images (emulated via Rosetta when needed)"
fi

# Change to docker directory
cd "$(dirname "$0")/../../docker"

# Create override file if test filter is specified
OVERRIDE_ARGS=""
if [ -n "$TEST_FILTER" ]; then
    echo "Creating test filter override..."
    cat > playwright-local-override.yml << EOF
# AUTO-GENERATED - DO NOT COMMIT
services:
    artemis-playwright:
        command: >
            sh -c '
            cd /app/artemis/src/test/playwright &&
            chmod 777 /root &&
            npm ci &&
            npm run playwright:setup &&
            PLAYWRIGHT_JUNIT_OUTPUT_NAME=test-reports/results.xml npx playwright test e2e --grep "${TEST_FILTER}" --reporter=list,junit,monocart-reporter
            '
EOF
    OVERRIDE_ARGS="-f playwright-local-override.yml"
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
docker compose -f $COMPOSE_FILE pull $DB nginx 2>/dev/null || true

# Build Artemis image from external WAR file
echo ""
echo "Building Artemis Docker image from WAR file..."
docker compose -f $COMPOSE_FILE build \
    --build-arg WAR_FILE_STAGE=external_builder \
    --no-cache \
    --pull \
    artemis-app

# Run the tests
echo ""
echo "Starting containers and running tests..."
echo "This may take 10-30 minutes..."
echo ""

# Disable exit on error to capture exit code
set +e
docker compose -f $COMPOSE_FILE $OVERRIDE_ARGS up --exit-code-from artemis-playwright
EXIT_CODE=$?
set -e

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

for xml_file in "$REPORT_DIR"/results*.xml; do
    if [ -f "$xml_file" ]; then
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
    fi
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
        for xml_file in "$REPORT_DIR"/results*.xml; do
            if [ -f "$xml_file" ]; then
                # Extract failed test names from failure message attributes
                # The message attribute contains the test file and name
                grep '<failure message=' "$xml_file" 2>/dev/null | \
                    sed 's/.*message="\([^"]*\)".*/  - \1/' || true
            fi
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

# Exit with failure if any tests failed
if [ $((TOTAL_FAILURES + TOTAL_ERRORS)) -gt 0 ]; then
    exit 1
fi
exit $EXIT_CODE
