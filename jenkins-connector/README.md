# Jenkins Connector Microservice

A stateless microservice that manages Jenkins CI/CD integration for Artemis. This service encapsulates all Jenkins-specific logic and provides a clean REST API for triggering and monitoring builds.

## Features

- **Stateless Design**: No dependency on Artemis core state
- **Build Management**: Trigger builds and monitor their status
- **Health Monitoring**: Health checks for service and Jenkins connectivity
- **Database Integration**: Tracks build records and project mappings
- **Docker Support**: Multi-stage Dockerfile for easy deployment

## API Endpoints

### POST /api/v1/build
Triggers a new build for a programming exercise participation.

**Request Body:**
```json
{
  "exerciseId": 123,
  "participationId": 456,
  "exerciseRepository": {
    "url": "https://github.com/user/repo.git",
    "commitHash": "abc123",
    "accessToken": "token"
  },
  "testRepository": {
    "url": "https://github.com/user/tests.git",
    "commitHash": "def456",
    "accessToken": "token"
  },
  "buildScript": "#!/bin/bash\n./gradlew test",
  "programmingLanguage": "JAVA"
}
```

**Response:**
```json
{
  "buildId": "550e8400-e29b-41d4-a716-446655440000",
  "message": "Build triggered successfully"
}
```

### GET /api/v1/build/{uuid}
Gets the current status of a build by its UUID.

**Response:**
```json
{
  "buildId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "RUNNING",
  "message": null
}
```

**Build Status Values:**
- `QUEUED`: Build is waiting in the queue
- `RUNNING`: Build is currently executing
- `COMPLETED`: Build finished successfully
- `FAILED`: Build failed

### GET /api/v1/health
Health check endpoint that verifies service and Jenkins connectivity.

**Response:**
```json
{
  "status": "UP",
  "details": {
    "service": "jenkins-connector",
    "version": "1.0.0",
    "jenkinsUrl": "http://localhost:8080",
    "jenkinsConnection": "UP",
    "database": "UP"
  }
}
```

## Configuration

### Application Properties

```yaml
server:
  port: 8081

jenkins:
  url: http://localhost:8080
  username: admin
  password: admin

spring:
  datasource:
    url: jdbc:h2:mem:testdb
    username: sa
    password: password
  jpa:
    hibernate:
      ddl-auto: create-drop
```

### Environment Variables

- `JENKINS_URL`: Jenkins server URL
- `JENKINS_USERNAME`: Jenkins username
- `JENKINS_PASSWORD`: Jenkins password
- `SPRING_DATASOURCE_URL`: Database connection URL
- `SPRING_PROFILES_ACTIVE`: Active Spring profiles

## Development

### Prerequisites

- Java 21+
- Gradle 8+
- Docker (optional)

### Running Locally

```bash
# Build the project
./gradlew build

# Run the application
./gradlew bootRun
```

### Running with Docker

```bash
# Build and run with Docker Compose
docker-compose up --build

# Build Docker image only
docker build -t jenkins-connector .
```

### Running Tests

```bash
./gradlew test
```

## Database Schema

### jenkins_project
Stores Jenkins project information for exercises.

| Column | Type | Description |
|--------|------|-------------|
| id | BIGINT | Primary key |
| project_key | VARCHAR | Unique project identifier |
| exercise_id | BIGINT | Reference to Artemis exercise |
| jenkins_folder_name | VARCHAR | Jenkins folder name |
| created_at | TIMESTAMP | Creation timestamp |
| updated_at | TIMESTAMP | Last update timestamp |

### build_record
Tracks individual build executions.

| Column | Type | Description |
|--------|------|-------------|
| id | BIGINT | Primary key |
| build_id | UUID | Unique build identifier |
| exercise_id | BIGINT | Reference to Artemis exercise |
| participation_id | BIGINT | Reference to Artemis participation |
| status | VARCHAR | Build status (QUEUED, RUNNING, COMPLETED, FAILED) |
| jenkins_job_name | VARCHAR | Jenkins job name |
| jenkins_build_number | INTEGER | Jenkins build number |
| created_at | TIMESTAMP | Creation timestamp |
| updated_at | TIMESTAMP | Last update timestamp |
| error_message | VARCHAR(1000) | Error message if build failed |
| jenkins_project_id | BIGINT | Foreign key to jenkins_project |

## Architecture

The service follows a clean architecture pattern:

- **Controllers**: REST API endpoints
- **Services**: Business logic and Jenkins integration
- **Repositories**: Data access layer
- **Domain**: Entity models and DTOs
- **Configuration**: Spring configuration classes

## Integration with Artemis

The service is designed to be called by Artemis's `StatelessJenkinsCIService` which acts as a client to this microservice. The communication happens via HTTP REST calls.

## Contributing

1. Follow the existing code style and patterns
2. Add tests for new functionality
3. Update documentation as needed
4. Ensure all tests pass before submitting PR

## Security Considerations

- Jenkins credentials are configured via environment variables
- Database connections use secure configurations
- All API endpoints validate input parameters
- Service runs as non-root user in Docker container