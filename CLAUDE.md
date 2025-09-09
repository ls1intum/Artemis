# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Artemis is an interactive learning platform with individual feedback for programming exercises, quizzes, modeling tasks, and more. It's built using Spring Boot for the backend and Angular for the frontend, serving thousands of students simultaneously at major universities.

## Development Commands

### Essential Build Commands
```bash
# Development server with Integrated Code Lifecycle
./gradlew bootRun --spring.profiles.active=dev,localci,localvc,artemis,scheduling,buildagent,core,local

# Development server (default task)
./gradlew bootRun

# Production build
./gradlew -Pprod -Pwar clean bootWar

# Run tests (server-side)
./gradlew test -x webapp

# Run single test
./gradlew test --tests ExamIntegrationTest -x webapp

# Run tests for specific modules
./gradlew test -DincludeModules=athena,atlas -x webapp

# Test coverage report
./gradlew test jacocoTestReport -x webapp
```

### Code Quality & Formatting
```bash
# Check Java code format
./gradlew spotlessCheck -x webapp

# Apply Java code formatter
./gradlew spotlessApply -x webapp

# Check JavaDoc
./gradlew checkstyleMain -x webapp

# Check for vulnerabilities
./gradlew dependencyCheckAnalyze -x webapp

# Find dependency updates
./gradlew dependencyUpdates -Drevision=release
```

### Database & OpenAPI
```bash
# Clear Liquibase checksums
./gradlew liquibaseClearChecksums

# Generate OpenAPI client services
./gradlew openApiGenerate -x webapp

# Generate OpenAPI spec
./gradlew generateApiDocs -x webapp
```

## Architecture Overview

Artemis follows a modular monolith architecture with clear domain boundaries:

### Backend Structure (Spring Boot)
- **Core Module (`core/`)**: Base configuration, security, user management, and shared utilities
- **Assessment Module (`assessment/`)**: Grading, feedback, complaints, and rating systems
- **Exercise Module (`exercise/`)**: Base exercise functionality and versioning
- **Programming Module (`programming/`)**: Programming exercise management, version control integration, CI/CD
- **Quiz Module (`quiz/`)**: Multiple choice, drag-and-drop, and short answer questions
- **Modeling Module (`modeling/`)**: UML diagram exercises using Apollon editor
- **Text Module (`text/`)**: Text-based exercises with NLP assessment
- **File Upload Module (`fileupload/`)**: File submission exercises
- **Exam Module (`exam/`)**: Online exam functionality with variants and plagiarism checks
- **Communication Module (`communication/`)**: Course announcements, posts, and messaging
- **Atlas Module (`atlas/`)**: Competency management and adaptive learning
- **Iris Module (`iris/`)**: AI-powered virtual assistant integration
- **Athena Module (`athena/`)**: Machine learning assessment automation
- **Build Agent Module (`buildagent/`)**: Local CI/CD infrastructure

### Frontend Structure (Angular)
- Matches backend module structure with corresponding Angular components
- Located in `src/main/webapp/app/`
- Uses standalone components and Angular 17+ features

### Key Design Patterns
- **Domain-Driven Design**: Each module represents a bounded context
- **Repository Pattern**: Data access through Spring Data JPA repositories
- **Service Layer**: Business logic encapsulated in service classes
- **REST APIs**: RESTful web services with OpenAPI documentation
- **WebSockets**: Real-time updates using STOMP over WebSocket

## Technology Stack

### Backend
- **Java 21** with Spring Boot 3.x (based on JHipster)
- **Spring Security** for authentication/authorization
- **Spring Data JPA** with Hibernate ORM
- **MySQL/PostgreSQL** database support
- **Liquibase** for database migrations
- **Docker** for containerized builds and tests
- **Git** integration (JGit) for version control
- **Apache SSH** for secure git operations
- **Hazelcast** for caching and distributed computing

### Frontend
- **Angular 17+** with TypeScript
- **Bootstrap** and custom SCSS for styling
- **NgBootstrap** components
- **Monaco Editor** for code editing
- **Apollon** for UML diagram editing
- **WebSocket** for real-time communication

### Testing
- **JUnit 5** for unit tests
- **Mockito** for mocking
- **Testcontainers** for integration tests
- **Awaitility** for asynchronous testing
- **ArchUnit** for architecture testing

### Development Tools
- **IntelliJ IDEA Ultimate** (recommended IDE)
- **Node.js** (LTS >=22.14.0 < 23) for Angular compilation
- **npm** (>=11.1.0) for package management
- **Graphviz** (optional, for graph generation)

## Module Development Guidelines

### When Working on Exercise Types
- Programming exercises: Focus on `programming/` module - handles git repos, build jobs, test cases
- Quiz exercises: Work in `quiz/` module - supports multiple question types with statistics
- Modeling exercises: Use `modeling/` module - integrates with Apollon UML editor
- Text exercises: Modify `text/` module - includes NLP-based assessment features
- File upload: Use `fileupload/` module for flexible file submission handling

### Assessment and Grading
- Core assessment logic in `assessment/` module
- Feedback, complaints, and grading criteria management
- Integration with automatic and manual assessment workflows

### Communication Features
- Course-wide messaging in `communication/` module
- Post, answer, and reaction system for course discussions
- FAQ management and announcement systems

### AI Integration
- Iris (virtual assistant) integration in `iris/` module
- Athena (automated assessment) in `athena/` module
- Both integrate with external EduTelligence microservices

## Database Architecture

Uses JPA entities with Hibernate for ORM:
- **Audit fields**: Most entities extend `AbstractAuditingEntity` for created/modified tracking
- **Soft deletes**: Some entities support soft deletion patterns
- **Liquibase**: All schema changes must be done via Liquibase changesets
- **Repository layer**: Spring Data JPA repositories with custom query methods

## Security Considerations

- JWT-based authentication with refresh tokens
- Role-based access control (Student, Tutor, Editor, Instructor, Admin)
- Course-level permissions and exercise-specific access control
- SAML2 and OAuth2 integration support
- Input validation on all REST endpoints
- XSS protection and CSRF handling

## Common Development Patterns

### Service Layer Pattern
```java
@Service
@Transactional
public class ExampleService {
    private final ExampleRepository repository;
    
    // Constructor injection preferred
    public ExampleService(ExampleRepository repository) {
        this.repository = repository;
    }
}
```

### REST Controller Pattern
```java
@RestController
@RequestMapping("/api/examples")
@PreAuthorize("hasRole('USER')")
public class ExampleResource {
    // Use method-level security annotations
    @PostMapping
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<Example> create(@RequestBody Example example) {
        // Implementation
    }
}
```

### Repository Queries
Use Spring Data JPA query methods or @Query annotations for complex queries. Follow naming conventions for automatic query generation.

**Important**: Always annotate query parameters with `@Param("variableName")`:
```java
@Query("""
    SELECT r
    FROM Result r
        LEFT JOIN FETCH r.feedbacks
    WHERE r.id = :resultId
    """)
Optional<Result> findByIdWithEagerFeedbacks(@Param("resultId") Long resultId);
```

### Authorization Patterns
Always use authorization annotations on REST endpoints. Use the most restrictive annotation possible:

```java
// Implicit authorization (preferred) - automatically checks course access
@EnforceAtLeastInstructorInCourse
public ResponseEntity<Void> enableLearningPathsForCourse(@PathVariable long courseId) {
    // Implementation - no manual auth check needed
}

// Explicit authorization (use only when necessary)
@EnforceAtLeastInstructor  
public ResponseEntity<Void> someMethod(@PathVariable long courseId) {
    var course = courseRepository.findByIdElseThrow(courseId);
    authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, null);
    // Implementation
}
```

### DTO Usage Best Practices
Always use DTOs for REST API responses to ensure data privacy and performance:

```java
// Good: Using Java records as immutable DTOs
public record GradeDTO(String gradeName, Boolean isPassingGrade, GradeType gradeType) {}

// Bad: Including entity objects in DTOs
public record PostDTO(Post post, MetisCrudAction action) {} // Contains too much data
```

## Performance Considerations

### Database Query Optimization
- **Avoid N+1 queries**: Use `JOIN FETCH` for collections that need eager loading
- **Limit JOIN FETCH usage**: Don't fetch more than 3 `OneToMany` relationships in a single query
- **Use projections and DTOs**: Fetch only required fields to minimize data transfer
- **Database-level filtering**: Always filter at the database level, never in memory
- **Avoid sub-queries**: Use JOINs instead for better performance
- **Proper indexing**: Index frequently queried columns (`WHERE`, `JOIN`, `ORDER BY`)

### General Performance Guidelines
- Use `@Transactional(readOnly = true)` for read operations
- **Avoid transactions when possible**: Transactions can kill performance and introduce locking issues
- Implement proper pagination for list endpoints (but avoid `LEFT JOIN FETCH` with pagination)
- Use DTOs for API responses to avoid over-fetching and maintain data privacy
- Cache frequently accessed data with Hazelcast
- Test with realistic loads (up to 2,000 students per course)

### Query Examples
```java
// Good: Using JOIN instead of sub-query
@Query("""
    SELECT COUNT(DISTINCT p)
    FROM StudentParticipation p
        JOIN p.submissions s
    WHERE p.exercise.id = :exerciseId
        AND s.submitted = TRUE
    """)
long countByExerciseIdSubmitted(@Param("exerciseId") long exerciseId);

// Good: Using projection for minimal data transfer
@Query("""
    SELECT new com.example.dto.ExerciseDTO(e.id, e.title)
    FROM Exercise e
    WHERE e.course.id = :courseId
    AND e.releaseDate >= :releaseDate
    """)
List<ExerciseDTO> findExerciseDTOsByCourseAndReleaseDate(@Param("courseId") Long courseId, @Param("releaseDate") ZonedDateTime releaseDate);
```

## Module Dependencies

Core module is the foundation - all other modules depend on it. Avoid circular dependencies between domain modules. Use events or service interfaces for cross-module communication when necessary.

## Development Setup & Configuration

### Local Development with Integrated Code Lifecycle
For a complete development setup with programming exercises, create `src/main/resources/config/application-local.yml`:

```yaml
artemis:
    user-management:
        use-external: false  # Use internal user management for development
    version-control:
        url: http://localhost:8080
        repository-authentication-mechanisms: password,token,ssh
    continuous-integration:
        # Use arm64 for Apple M-series, default is amd64
        image-architecture: arm64  # Only for ARM-based systems
        # Windows only: 
        docker-connection-uri: tcp://localhost:2375
        specify-concurrent-builds: true
        concurrent-build-size: 2  # Adjust based on CPU cores
eureka:
    client:
        register-with-eureka: false
        fetch-registry: false
```

### File Handling Best Practices
- Always use OS-independent file paths with `Path.of(firstPart, secondPart, ...)`
- Use `existingPath.resolve(subfolder)` to append paths
- Never use OS-specific separators like `/` or `\`

### Important Development Rules
- **RestControllers should be stateless** and focus only on HTTP layer concerns
- **Use constructor injection** over field injection (`@Autowired`)
- **Avoid service dependencies** - prefer repository default methods for simple operations
- **Always use `findByIdElseThrow()`** instead of `findById()` and null checks
- **Use `var` sparingly** - prefer explicit types for primitives and different collection types
- **Never trust user input** - always validate request data and check authorization
- **Use ObjectMapper (Jackson)** for JSON serialization, not Gson

### Repository Method Naming
Move simple service methods to repositories as `default` methods with clear naming:
```java
default Course findByIdWithLecturesElseThrow(long courseId) {
    return getValueElseThrow(findWithEagerLecturesById(courseId), courseId);
}
```

## Exercise Versioning

The current branch implements exercise versioning functionality:
- `ExerciseVersionService` manages version creation and tracking
- Versions are created automatically when exercises are created or updated
- Integration points in exercise resources for version management

## Integrated Code Lifecycle Features

### Local Version Control (LocalVC)
- Git repositories managed directly by Artemis server
- No external Git server required for development
- Supports HTTPS, SSH, and token authentication

### Local Continuous Integration (LocalCI) 
- Docker-based build execution for student submissions
- Build agents integrated into Artemis server
- Concurrent build management with configurable limits
- Support for multiple programming languages through Docker templates