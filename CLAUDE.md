# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Artemis is an interactive learning platform for programming exercises, quizzes, modeling tasks, and exams with automatic and manual assessment. It integrates with AI services (Iris for virtual tutoring, Athena for automated assessment, Hyperion for exercise creation).

## Tech Stack

- **Server**: Spring Boot 3.5 (Java 25), MySQL, Hibernate, Hazelcast
- **Client**: Angular 21, TypeScript, SCSS
- **Build**: Gradle 9.3, npm/Node 24
- **Testing**: JUnit 6, Vitest (preferred; Jest is deprecated and being migrated to Vitest), Playwright

## Build & Development Commands

### Server
```bash
./gradlew bootRun                    # Start dev server (includes Angular build)
./gradlew bootRun -x webapp          # Server only (use with npm start)
./gradlew -Pprod -Pwar clean bootWar # Production WAR artifact
```

### Client
```bash
npm install                          # Install dependencies
npm start                            # Angular dev server with HMR (runs prebuild + ng serve)
npm run webapp:build                 # Development build
npm run webapp:prod                  # Production build
npm run build                        # Alternative production build
```

### Build Output
- Client assets: `build/resources/main/static`
- Production WAR: `build/libs/Artemis-<version>.war`

### Code Quality
```bash
./gradlew spotlessCheck              # Check Java formatting
./gradlew spotlessApply              # Fix Java formatting
./gradlew checkstyleMain             # Java linting
./gradlew modernizer                 # Check for legacy API usage
npm run lint                         # ESLint
npm run lint:fix                     # Fix ESLint issues
npm run stylelint                    # SCSS linting
npm run prettier:check               # Check formatting
npm run prettier:write               # Fix formatting
```

### Testing
```bash
# Server
./gradlew test                                                    # All server tests
./gradlew test --tests ExamIntegrationTest -x webapp              # Single test class
./gradlew test --tests ExamIntegrationTest.testGetExamScore       # Single test method

# Client (Vitest - preferred for new tests)
npm run vitest                       # Watch mode
npm run vitest:run                   # Single run
npm run vitest:coverage              # With coverage
npm run vitest -- path/to/spec.ts    # Single Vitest file

# Client (Jest - deprecated, being migrated to Vitest)
npm test                             # Jest with coverage
npm run test-diff                    # Test changed files vs origin/develop
npm run test:ci                      # Full CI with module coverage check
# Single test:
npm run test:one -- --test-path-pattern='src/main/webapp/app/path/to/spec\.ts$'
```

## Project Structure

### Server (`src/main/java/de/tum/cit/aet/artemis/`)
Organized by feature module:
- `core/` - Configuration, security, utilities, base entities
- `exercise/` - Base exercise functionality
- `programming/` - Programming exercises with CI/CD
- `quiz/` - Quiz exercises
- `modeling/` - UML diagram exercises
- `text/` - Text exercises
- `fileupload/` - File upload exercises
- `exam/` - Exam mode
- `assessment/` - Grading and assessment
- `communication/` - Channels, messaging, notifications
- `lecture/` - Lecture management
- `atlas/` - Competency-based learning, learning analytics
- `iris/` - LLM-based virtual tutor
- `athena/` - ML-based assessment
- `plagiarism/` - Plagiarism detection (JPlag)
- `lti/` - LTI integration
- `tutorialgroup/` - Tutorial group management

### Client Web App (`src/main/webapp/app/`)
- `core/` - Core services (HTTP, auth, guards)
- `shared/` - Shared components, pipes, utilities
- `openapi/` - Generated TypeScript client code
- Feature modules mirror server structure
- Assets and translations in `content/`
- Client tests are co-located with their TypeScript components

### Tests
- `src/test/java/` - JUnit server tests
- `src/test/playwright/` - E2E tests

### Other Directories
- `src/main/resources/` - Spring profiles (`config/application-*.yml`), Liquibase changelogs, static files
- `documentation/` - Project documentation
- `docker/` - Deployment helpers

### API Specification
- Generated at runtime by springdoc: `/v3/api-docs` and `/swagger-ui`

## Coding Conventions

### Java
- PascalCase for classes, camelCase for fields/methods
- No wildcard imports (Spotless enforces)
- Package-by-feature organization
- 4-space indentation
- Avoid `@Transactional` scope
- Use DTOs (Java records) for REST endpoints
- Prefer constructor injection for Spring beans
- Use Java 25 features (records, sealed classes, pattern matching)

### TypeScript/Angular
- kebab-case for filenames (`course-detail.component.ts`)
- PascalCase for classes, camelCase for members
- Single quotes, 4-space indentation
- Standalone components preferred
- **Angular 21 signal-based APIs are mandatory for new code:**
  - Use `input()` / `input.required()` instead of `@Input()`
  - Use `output()` instead of `@Output()`
  - Use `viewChild()` / `viewChild.required()` instead of `@ViewChild()`
  - Use `viewChildren()` instead of `@ViewChildren()`
  - Use `signal()`, `computed()`, and `effect()` for reactive state management
  - Use `inject()` for dependency injection instead of constructor injection
  - Legacy decorators (`@Input`, `@Output`, `@ViewChild`, `@ViewChildren`, `@ContentChild`, `@ContentChildren`) must not be used in new code
  - In modules not yet fully migrated, prefer signal-based APIs for new components but maintain consistency within existing components
  - An ESLint rule (`enforce-signal-apis-in-migrated-modules`) enforces this in fully migrated modules
- **Angular template control flow: use `@if`, `@for`, `@switch`; never use `*ngIf`, `*ngFor`, `*ngSwitch`**
- Avoid `null`, use `undefined` where possible
- Avoid spread operator for objects
- Prefer 100% type safety
- **UI components: Use PrimeNG instead of Bootstrap components**
  - All new UI elements must be implemented using PrimeNG components
  - We are migrating from Bootstrap to PrimeNG; do not introduce new Bootstrap components
  - Existing Bootstrap usage will be migrated incrementally

### General
- LF line endings
- Final newlines required
- UTF-8 encoding
- YAML: 2-space indentation

## Testing Guidelines

- Keep tests deterministic; mock external services and WebSockets
- CI enforces coverage thresholds per module
- Use `npm run test-diff` for incremental client work
- **Client tests: Prefer Vitest over Jest for new tests**
  - Jest is deprecated and being migrated to Vitest
  - Use `vi.spyOn()`, `vi.fn()`, `vi.clearAllMocks()` instead of Jest equivalents
  - Run Vitest: `npm run vitest` (watch), `npm run vitest:run` (single run), `npm run vitest:coverage`
- Name server tests `*Test.java`; reuse module base classes when present
- Add screenshots for UI changes in PRs
- Verify linting before submitting: `npm run lint`, `./gradlew checkstyleMain -x webapp`

## Commit & PR Guidelines

- Concise, imperative commit messages scoped where useful (e.g., `Exam mode: adjust live updates`, `build: bump version`); wrap bodies near 72 chars
- PRs: include problem/solution summary, linked issue, commands/tests run, screenshots for UI, and doc updates if relevant
- Target `develop` branch; rebase to reduce noise
- Run lint and tests before submitting
- Follow `CONTRIBUTING.md` and the guidelines in `documentation/docs/developer/guidelines/`
- Use the PR description template in `.github/PULL_REQUEST_TEMPLATE.md`
