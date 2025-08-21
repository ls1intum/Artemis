#!/bin/bash

# API Test Script for Jenkins Connector
BASE_URL="http://localhost:8081"

echo "Testing Jenkins Connector API..."

# Test 1: Health Check
echo "1. Testing health endpoint..."
HEALTH_RESPONSE=$(curl -s -X GET "$BASE_URL/api/v1/health")
echo "Health Response: $HEALTH_RESPONSE"

# Test 2: Invalid Build Status Request
echo "2. Testing invalid build ID..."
INVALID_RESPONSE=$(curl -s -w "HTTP_%{http_code}" -X GET "$BASE_URL/api/v1/build/invalid-uuid")
echo "Invalid Build ID Response: $INVALID_RESPONSE"

# Test 3: Valid Build Trigger Request
echo "3. Testing build trigger..."
BUILD_REQUEST='{
    "exerciseId": 123,
    "participationId": 456,
    "exerciseRepository": {
        "url": "https://github.com/user/repo.git",
        "commitHash": "abc123"
    },
    "buildScript": "#!/bin/bash\n./gradlew test",
    "programmingLanguage": "JAVA"
}'

BUILD_RESPONSE=$(curl -s -w "HTTP_%{http_code}" -X POST "$BASE_URL/api/v1/build" \
    -H "Content-Type: application/json" \
    -d "$BUILD_REQUEST")
echo "Build Trigger Response: $BUILD_RESPONSE"

# Test 4: Extract Build ID and check status (if build was successful)
if [[ $BUILD_RESPONSE == *"buildId"* ]]; then
    BUILD_ID=$(echo "$BUILD_RESPONSE" | grep -o '"buildId":"[^"]*"' | cut -d'"' -f4)
    if [[ ! -z "$BUILD_ID" ]]; then
        echo "4. Testing build status for ID: $BUILD_ID"
        STATUS_RESPONSE=$(curl -s -X GET "$BASE_URL/api/v1/build/$BUILD_ID")
        echo "Build Status Response: $STATUS_RESPONSE"
    fi
fi

echo "API tests completed!"