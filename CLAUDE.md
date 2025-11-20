# CLAUDE.md
## Project Overview

Artemis is an interactive learning platform with individual feedback for programming exercises, quizzes, modeling tasks, and more. It's a full-stack application built with:
- **Backend (Server)**: Spring Boot 3.5.7 (Java 25) with Gradle
- **Frontend**: Angular 20 with TypeScript
- **Database**: MySQL with Liquibase migrations
- **Architecture**: Modular monolith with feature-based packages

The main branch is `develop`.

## Essential Commands
### Development

```bash
# Start the server (requires MySQL and local setup)
./gradlew bootRun

# Start the Angular dev server with HMR
npm run start

# Run both in parallel during development (see docs)
```

#### Style Checks
##### Server
```bash
./gradlew checkstyleMain -x webapp 
```

#### Client
```bash
npm run lint
```

```bash
npm run lint -- pathToEditedFile
```

### Testing

```bash
# Backend tests
./gradlew test                         # Run all Java tests
./gradlew test --tests ClassName       # Run specific test class

# Frontend tests
ng test                                  # Run all tests with coverage
ng test --test-path-pattern testFileName # e.g. passkey-authentication-page.component.spec.ts
npm run lint -- pathToEditedFile         # make sure to run it for edited files, so no issues are introduced, file path can be e.g. src/main/webapp/app/core/auth/passkey-content/passkey-content.component.spec.ts

# Pre-build step (required before some npm commands)
npm run prebuild                        # Generate required files
```

### Building

```bash
# Development build (client only)
npm run webapp:build

# Production build (client)
npm run webapp:prod

# Full production build (server + client)
./gradlew -Pprod -Pwar clean bootWar
```

### Code Quality

```bash
# Linting and formatting
npm run lint                            # Check TypeScript/Angular code
npm run lint:fix                        # Fix linting issues
npm run prettier:check                  # Check formatting
npm run prettier:write                  # Auto-format code

# Backend code quality
./gradlew checkstyleMain                # Java style check
./gradlew spotlessCheck                 # Check Java formatting
./gradlew spotlessApply                 # Apply Java formatting
```

## Architecture

### Backend Module Structure

The backend follows a modular monolith architecture organized by feature domains in `src/main/java/de/tum/cit/aet/artemis/`:

- **core**: Core functionality (authentication, user management, configuration, utilities)
- **exercise**: Base exercise functionality shared across exercise types
- **programming**: Programming exercises with version control and CI integration
- **quiz**: Quiz exercises (multiple choice, drag & drop, short answer)
- **modeling**: Modeling exercises using Apollon UML editor
- **text**: Text exercises with NLP-based assessment
- **fileupload**: File upload exercises
- **exam**: Exam mode functionality
- **assessment**: Assessment and grading functionality
- **communication**: Posts, messages, notifications
- **lecture**: Lecture management and units
- **atlas**: Competency-based learning and learning paths
- **iris**: AI-powered virtual assistant (LLM integration)
- **athena**: Machine learning-based assessment support
- **hyperion**: AI-driven exercise creation assistant
- **buildagent**: Build agent management for programming exercises
- **plagiarism**: Plagiarism detection (JPlag integration)
- **tutorialgroup**: Tutorial group management
- **lti**: LTI integration with external LMS

#### Guidelines 
- classesOfThisModuleThat().areAnnotatedWith(Service.class).should().resideInAPackage("..service..").because("services should be in the package 'service'.");

Each module typically contains:
- `domain/`: JPA entities
- `repository/`: Spring Data repositories
- `service/`: Business logic
- `web/`: REST controllers
- `dto/`: Data transfer objects
- `config/`: Module-specific configuration

### Frontend Module Structure

Angular application in `src/main/webapp/app/` organized by feature:

- **core**: Core services, guards, interceptors, authentication
- **shared**: Shared components, pipes, directives
- **exercise**: Exercise components (programming, quiz, modeling, text, fileupload)
- **exam**: Exam mode UI
- **assessment**: Assessment and grading UI
- **communication**: Communication features UI
- **lecture**: Lecture management UI
- **atlas**: Competency and learning path UI
- **iris**: AI assistant UI
- **buildagent**: Build agent UI

Key files:
- `app.config.ts`: Application configuration and providers
- `app.routes.ts`: Route definitions
- `app.constants.ts`: Global constants

### Key Architectural Patterns

1. **Spring Profiles**: Development uses profiles like `dev,localci,localvc,artemis,scheduling,buildagent,core,local,atlas`
   - `dev`: Development mode
   - `localci`: Local continuous integration
   - `localvc`: Local version control
   - `artemis`: Artemis-specific configuration
   - `core`: Core functionality
   - `atlas`: Atlas/competency features

2. **REST API**: Controllers in `web/` packages expose RESTful endpoints following Spring conventions

3. **WebSocket**: Real-time communication for programming exercises, exam mode, and communication features

4. **External Integrations**:
   - Version Control: GitLab/GitHub or LocalVC (integrated)
   - CI System: Jenkins or LocalCI (integrated)
   - AI Services: EduTelligence suite (Iris, Athena)

5. **DTOs**: Heavy use of DTOs for API contracts. Domain entities are typically not exposed directly.

6. **Testing**:
   - Backend: JUnit 5, Mockito, Spring Boot Test
   - Frontend: Jest, Angular Testing Library, ng-mocks
   - Test base classes exist in `src/test/java/de/tum/cit/aet/artemis/` for integration tests

## Development Workflow

### Working with Tests

Frontend tests use Jest and should be run with the prebuild step:
```bash
npm run prebuild && ng test
```

Tests are co-located with source files (`.spec.ts` for client, `Test.java` for server).

### Code Review Process
### Database Migrations

Liquibase migrations are in `src/main/resources/config/liquibase/`. Add new changesets for schema changes.

### Build Performance

The frontend build uses:
- Angular 20 with esbuild
- Development HMR for fast reloads
- Production builds optimize chunks (controlled by `NG_BUILD_OPTIMIZE_CHUNKS`)

### Configuration Files

Key configuration:
- `application.yml` and `application-*.yml`: Spring Boot profiles in `src/main/resources/config/`
- `gradle.properties`: Dependency versions and Gradle settings
- `package.json`: npm dependencies and scripts
- `angular.json`: Angular build configuration
- `tsconfig.json`: TypeScript compiler options

## Common Tasks

### Adding a New REST Endpoint

1. Create/update DTO in appropriate `dto/` package
2. Add method to service in `service/` package
3. Create controller method in `web/` package with proper annotations
4. Add security annotations (`@PreAuthorize`, etc.)
5. Write integration tests

### Adding a New Frontend Feature

1. Create component in appropriate feature module
2. Add route to `app.routes.ts` if needed
3. Create service for API communication if needed
4. Write unit tests (`.spec.ts`)
5. Follow Angular style guide and project conventions

### Running Specific Test Suites

```bash
# Backend: Run tests for specific module
./gradlew test --tests "de.tum.cit.aet.artemis.programming.*"

# Frontend: Run tests for specific module
npm run prebuild && ng test --testPathPattern=programming
```

or for the client side:

```bash
ng test --test-path-pattern testFileName # e.g. passkey-authentication-page.component.spec.ts
npm run lint -- pathToEditedFile         # make sure to run it for edited files, so no issues are introduced, file path can be e.g. src/main/webapp/app/core/auth/passkey-content/passkey-content.component.spec.ts
```

## Important Notes

- **Prebuild Required**: Many npm commands require `npm run prebuild` to generate necessary files
- **Spring Profiles**: The application requires specific profiles to run. Check `application-*.yml` files for available profiles
- **Node/NPM Version**: Requires Node >= 24.7.0, npm >= 11.5.1
- **Java Version**: Requires Java 25
- **Database**: MySQL is required for development (or use H2 for simple testing)
- **EduTelligence Compatibility**: When working with Iris/Athena, check the compatibility matrix in the EduTelligence repository
- **Module Dependencies**: The `core` module is depended upon by all other modules. Changes here have wide impact.

## Documentation
### New Documentation
- Located in the `/documentation` folder
- Uses Docusaurus to be built
- Is the primary source of truth for all new documentation
- Shall be extended for new features and changes
- Parts from the old documentation `/docs` shall be migrated to the new documentation (/documentation) over time (e.g. if something would need to be updated in the old documentation, migrate it)
- The new documentation shall be linked to from the old documentation to ease the transition

### Old Documentation 
- Can be found in the `/docs` folder
- Uses Sphinx to be build
- Is outdated and will be replaced with the new documentation in the `/documentation` folder
- Shall not be extended any further, but instead, shall be migrated to the new documentation

### External Documentation
Comprehensive documentation: https://docs.artemis.cit.tum.de/
- [Setup Guide](https://docs.artemis.cit.tum.de/dev/setup/)
- [Server Guidelines](https://docs.artemis.cit.tum.de/dev/guidelines/server/)
- [Client Guidelines](https://docs.artemis.cit.tum.de/dev/guidelines/client/)

## Guidelines

### Client Guidelines

Do not use "Sie" instead use "Du" for addressing users in the UI.
