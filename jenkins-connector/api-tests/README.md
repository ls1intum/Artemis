# Jenkins Connector API Tests

This directory contains Bruno API test files for the Jenkins Connector microservice. These tests cover all endpoints and various scenarios including edge cases and load testing.

## Prerequisites

1. **Bruno CLI** - Install Bruno CLI for running tests:
   ```bash
   npm install -g @usebruno/cli
   ```

2. **Running Service** - Ensure the Jenkins Connector service is running:
   ```bash
   # Option 1: Run locally
   ./gradlew bootRun
   
   # Option 2: Run with Docker
   docker run -p 8081:8081 jenkins-connector
   
   # Option 3: Run with docker-compose
   docker-compose up jenkins-connector
   ```

## Test Structure

### Environments
- `local.bru` - Configuration for local development (localhost:8081)
- `docker.bru` - Configuration for Docker environment (localhost:8081)

### Test Files

| Test File | Description | Endpoints Covered |
|-----------|-------------|-------------------|
| **Health Check.bru** | Service health and connectivity | `GET /api/v1/health` |
| **Trigger Build - Valid Request.bru** | Complete valid build request | `POST /api/v1/build` |
| **Trigger Build - Invalid Request.bru** | Validation error handling | `POST /api/v1/build` |
| **Trigger Build - Minimal Request.bru** | Minimum required fields | `POST /api/v1/build` |
| **Get Build Status - Valid UUID.bru** | Status check with valid UUID | `GET /api/v1/build/{uuid}` |
| **Get Build Status - Invalid UUID.bru** | UUID format validation | `GET /api/v1/build/{uuid}` |
| **Get Build Status - Non-existent UUID.bru** | Non-existent build handling | `GET /api/v1/build/{uuid}` |
| **Integration Test - Full Build Flow.bru** | End-to-end workflow | Multiple endpoints |
| **Load Test - Multiple Builds.bru** | Performance and concurrency | `POST /api/v1/build` |
| **Edge Cases - Empty and Special Characters.bru** | Special character handling | `POST /api/v1/build` |

## Running Tests

### Run All Tests
```bash
# Run all tests with local environment
bruno run --env local

# Run all tests with docker environment  
bruno run --env docker
```

### Run Individual Tests
```bash
# Run specific test
bruno run "Health Check" --env local

# Run build trigger tests
bruno run "Trigger Build - Valid Request" --env local
```

### Run Test Categories
```bash
# Health checks
bruno run "Health Check" --env local

# Build triggers
bruno run "Trigger Build*" --env local

# Build status checks
bruno run "Get Build Status*" --env local
```

## Test Scenarios

### 🏥 Health Check Tests
- Validates service availability
- Checks Jenkins connectivity
- Verifies database status

### 🚀 Build Trigger Tests
- **Valid requests** with complete data
- **Invalid requests** for validation testing
- **Minimal requests** with only required fields
- **Special characters** and edge cases

### 📊 Build Status Tests
- **Valid UUID** format checking
- **Invalid UUID** format handling
- **Non-existent** build lookup

### 🔄 Integration Tests
- **Full workflow** from trigger to status check
- **Load testing** with multiple concurrent requests

## Expected Responses

### Service States

The service can be in different states depending on Jenkins availability:

| State | Health Status | Build Trigger | Description |
|-------|---------------|---------------|-------------|
| **Healthy** | `UP` | `200 OK` | All systems operational |
| **Degraded** | `DEGRADED` | `500 Error` | Service up, Jenkins down |
| **Down** | `DOWN` | `500 Error` | Service unavailable |

### Response Codes

| Endpoint | Success | Client Error | Server Error |
|----------|---------|--------------|--------------|
| Health Check | 200 | - | 503 |
| Trigger Build | 200 | 400 | 500 |
| Get Build Status | 200 | 400, 404 | 500 |

## Environment Variables

Tests use these environment variables:

```bash
# Base URL for the service
BASE_URL=http://localhost:8081

# Test data
EXERCISE_ID=123
PARTICIPATION_ID=456
VALID_BUILD_ID=550e8400-e29b-41d4-a716-446655440000
```

## Continuous Integration

These tests can be integrated into CI/CD pipelines:

```yaml
# GitHub Actions example
- name: Run API Tests
  run: |
    # Start service
    docker-compose up -d jenkins-connector
    
    # Wait for service to be ready
    sleep 10
    
    # Run tests
    bruno run --env docker
    
    # Cleanup
    docker-compose down
```

## Test Data

Tests use realistic but safe test data:

- **Repository URLs**: Fictional GitHub repositories
- **Build Scripts**: Simple, safe bash commands
- **UUIDs**: Valid format examples
- **Exercise/Participation IDs**: Non-conflicting test values

## Troubleshooting

### Service Not Responding
```bash
# Check if service is running
curl http://localhost:8081/api/v1/health

# Check Docker container status
docker ps | grep jenkins-connector

# Check service logs
docker logs jenkins-connector
```

### Jenkins Connection Issues
```bash
# Expected when Jenkins is not running
# Health check will show "DEGRADED" status
# Build triggers will return 500 errors
```

### Test Failures
```bash
# Run tests with verbose output
bruno run --verbose --env local

# Run individual failing test
bruno run "failing-test-name" --env local
```

## Contributing

When adding new tests:

1. Follow the naming convention: `Category - Specific Case.bru`
2. Include comprehensive assertions
3. Add descriptive documentation in the `docs` section
4. Update this README with new test descriptions
5. Test both success and failure scenarios

## Security Notes

- Tests use fictional data that won't interfere with real systems
- No real API keys or sensitive data in test files
- Special character tests verify input sanitization
- Load tests respect service limits