# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Artemis is an interactive learning platform for programming exercises, quizzes, modeling tasks, and exams with automatic and manual assessment. It integrates with AI services (Iris for virtual tutoring, Athena for automated assessment, Hyperion for exercise creation).

## Tech Stack

- **Server**: Spring Boot 3.5 (Java 25), MySQL, Hibernate, Hazelcast
- **Client**: Angular 21, TypeScript, SCSS
- **Build**: Gradle 9.3, pnpm 11 / Node 24 (pnpm version pinned via the `packageManager` field in package.json; activate with `corepack enable`)
- **Testing**: JUnit 6, Vitest (preferred; Jest is deprecated and being migrated to Vitest), Playwright

## Build & Development Commands

### Server
```bash
./gradlew bootRun                          # Start dev server (includes Angular build)
./gradlew bootRun -x webapp                # Server only (use with pnpm start)
./gradlew -Pprod -Pwar clean bootWar       # Production WAR (no SBOM, fast)
./gradlew -Pprod -Pwar -Psbom clean bootWar # Production WAR including server + client SBOM
```

SBOM generation (`cyclonedxBom` + `generateClientSbom`) is gated behind the `-Psbom` Gradle property. CI release-eligible jobs (pushes to `develop`/`main`/`release/*`, version tags, and published releases) set it automatically in `.github/workflows/ci-build.yml`. Local builds and PR CI ship a WAR without the SBOM — `AdminSbomResource` returns 404 and the admin UI renders an informational banner in that case.

### Client
```bash
corepack enable                      # One-time: activate the pnpm version pinned in package.json
pnpm install --frozen-lockfile       # Install dependencies (CI-style, asserts lockfile is authoritative)
pnpm install                         # Install + allow lockfile updates (for dependency changes)
pnpm start                           # Angular dev server with HMR (runs prebuild + ng serve)
pnpm run webapp:build                # Development build
pnpm run webapp:prod                 # Production build
pnpm run build                       # Alternative production build
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
pnpm run lint                        # ESLint
pnpm run lint:fix                    # Fix ESLint issues
pnpm run stylelint                   # SCSS linting
pnpm run prettier:check              # Check formatting
pnpm run prettier:write              # Fix formatting
```

### Testing
```bash
# Server (requires Docker — tests run against PostgreSQL via Testcontainers by default)
./gradlew test -x webapp                                          # All server tests (PostgreSQL)
./gradlew test --tests ExamIntegrationTest -x webapp              # Single test class
./gradlew test --tests ExamIntegrationTest.testGetExamScore       # Single test method

# Client (Vitest - preferred for new tests)
pnpm run vitest                      # Watch mode
pnpm run vitest:run                  # Single run
pnpm run vitest:coverage             # With coverage
pnpm run vitest -- path/to/spec.ts   # Single Vitest file

# Client (Jest - deprecated, being migrated to Vitest)
pnpm test                            # Jest with coverage
pnpm run test-diff                   # Test changed files vs origin/develop
pnpm run test:ci                     # Full CI with module coverage check
# Single test:
pnpm run test:one -- --test-path-pattern='src/main/webapp/app/path/to/spec\.ts$'

# E2E Tests (Playwright) — preferred way to run locally
# The script auto-kills processes on ports 8080/9000/7921, starts Postgres, server, and client.
./run-e2e-tests-local-fast.sh                              # Run all E2E tests
./run-e2e-tests-local-fast.sh --filter "Quiz"              # Run tests matching "Quiz"
./run-e2e-tests-local-fast.sh --filter "ExamAssessment|SystemHealth"  # Multiple patterns
./run-e2e-tests-local-fast.sh --stop                       # Stop all services

# Multi-node E2E (catches Hazelcast cluster / L2 cache coherence regressions)
# Boots the full production-faithful stack: Postgres, JHipster Registry (Eureka),
# ActiveMQ, 3 Artemis nodes, nginx LB, containerised Playwright. Slower than the
# single-node fast script, but the only way to reproduce multi-node bugs locally.
./run-e2e-tests-local-multinode.sh                         # Full multi-node run (build WAR + image + stack + tests)
./run-e2e-tests-local-multinode.sh --filter "Quiz"         # Multi-node, filtered
./run-e2e-tests-local-multinode.sh --skip-build --skip-up  # Quick re-run against an already-running stack
./run-e2e-tests-local-multinode.sh --stop                  # Tear everything down

# Multi-node E2E (fast variant) — same topology, host-launched JVMs instead of Docker images
# Skips the Docker image build that dominates the slow path (~5–8 min). Reuses the WAR built by
# Gradle and runs 3 java -jar processes on the host; Postgres/Eureka/ActiveMQ/nginx still run as
# containers. Use this for server-side iteration on multi-node bugs. Cold ~1–2 min, warm ~30 s.
./run-e2e-tests-local-multinode-fast.sh                       # Full run (build WAR + infra + 3 host JVMs + tests)
./run-e2e-tests-local-multinode-fast.sh --filter "Quiz"       # Filter to a subset of tests
./run-e2e-tests-local-multinode-fast.sh --skip-build --skip-up  # Re-run tests against the running stack
./run-e2e-tests-local-multinode-fast.sh --stop                # Tear everything down
```

**Which E2E runner should I use?**
- `run-e2e-tests-local-fast.sh` — single node, Angular dev server. Best for client (UI) iteration.
- `run-e2e-tests-local-multinode-fast.sh` — multi-node, WAR run from host. Best for server iteration that needs the cluster (Hazelcast, ActiveMQ STOMP, LB).
- `run-e2e-tests-local-multinode.sh` — full Docker image build, prod-faithful. Use this to reproduce a CI-only failure or before pushing a multi-node-sensitive change.

## Project Structure

### Server (`src/main/java/de/tum/cit/aet/artemis/`)
Organized by feature module:
- `core/` - Configuration, security base, utilities, base entities
- `account/` - User, authority, passkey, account REST, authentication, LDAP
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
- `calendar/` - Calendar events and iCal subscriptions
- `atlas/` - Competency-based learning, learning analytics
- `iris/` - LLM-based virtual tutor
- `athena/` - ML-based assessment
- `hyperion/` - LLM-based exercise creation assistant
- `plagiarism/` - Plagiarism detection (JPlag)
- `lti/` - LTI integration
- `tutorialgroup/` - Tutorial group management
- `globalsearch/` - Cross-entity search via Weaviate
- `videosource/` - External video source integration (TUM Live)
- `course/` - Course management, registration, archive, dashboard, statistics
- `admin/` - Admin operations: data export, vulnerability scan, cleanup, telemetry, organization management, legal documents

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
- Do not inject `EntityManager` or `EntityManagerFactory` directly into services or controllers; all persistence operations must go through Spring Data repositories
- Use DTOs (Java records) for REST endpoints
- Prefer constructor injection for Spring beans
- Use Java 25 features (records, sealed classes, pattern matching)

### Caching
- **Do not add `@Cache` (Hibernate L2) annotations on entities or associations.** Hibernate second-level cache is disabled cluster-wide and an ArchUnit rule (`ArchitectureTest.testNoHibernateSecondLevelCacheAnnotation`) fails the build if any reappears. Reason: `@Modifying @Query` repository methods bypass L2 invalidation, and the absence of service-level `@Transactional` leaves no clean place to coordinate eviction within a REST call — both produced cross-node stale-read bugs in the multi-node cluster (issue #12574, fixed in PR #12578; further cleanup in PR #12579).
- **For DTO / projection caching, use Spring `@Cacheable` with the `HazelcastCacheManager`** (defined in `HazelcastConfiguration.cacheManager`). Always pair `@Cacheable` with explicit eviction — `@CacheEvict` on the writer service, or a Hibernate `PostUpdateEventListener` / `PostDeleteEventListener`. See `TitleCacheEvictionService` for the canonical pattern.
- The bar for adding a new cache: a measured performance gain that justifies the eviction-correctness work. The default answer is: do not cache.
- Full rationale, history, and patterns: `documentation/docs/developer/guidelines/caching.mdx`.

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
  - **`@ng-bootstrap/ng-bootstrap` is deprecated** — do not use `NgbModal`, `NgbActiveModal`, `NgbModalRef`, `NgbTooltip`, `NgbDropdown`, etc. in new code. Use PrimeNG's `DialogService` (`primeng/dynamicdialog`) for modals, `p-tooltip` for tooltips, etc. ng-bootstrap is incompatible with Angular signal inputs (assigning to `modalRef.componentInstance.X` silently fails when `X` is `input()`/`input.required()`). Existing usages are being migrated.

### General
- LF line endings
- Final newlines required
- UTF-8 encoding
- YAML: 2-space indentation

## Testing Guidelines

- **Server tests require Docker** — tests run against PostgreSQL via Testcontainers by default (both locally and in CI).
- Keep tests deterministic; mock external services and WebSockets
- CI enforces coverage thresholds per module
- Use `pnpm run test-diff` for incremental client work
- **Client tests: Prefer Vitest over Jest for new tests**
  - Jest is deprecated and being migrated to Vitest
  - Use `vi.spyOn()`, `vi.fn()`, `vi.clearAllMocks()` instead of Jest equivalents
  - Run Vitest: `pnpm run vitest` (watch), `pnpm run vitest:run` (single run), `pnpm run vitest:coverage`
- Name server tests `*Test.java`; reuse module base classes when present
- When comparing `ZonedDateTime` values in tests, use `toInstant()` for comparisons since PostgreSQL stores timestamps as UTC (timezone offset is not preserved through database round-trips)
- **E2E tests: Use `./run-e2e-tests-local-fast.sh`** — this is the intended way to run Playwright E2E tests locally (for both developers and AI agents)
  - The script automatically kills processes on ports 8080, 9000, and 7921 before starting
  - Use `--filter "TestName"` to run specific tests; supports regex patterns (e.g., `--filter "Quiz|Exam"`)
  - After the first run, reuse running services with `--skip-server --skip-client --skip-db`
- Add screenshots for UI changes in PRs
- Verify linting before submitting: `pnpm run lint`, `./gradlew checkstyleMain -x webapp`

## Commit & PR Guidelines

- Concise, imperative commit messages scoped where useful (e.g., `Exam mode: adjust live updates`, `build: bump version`); wrap bodies near 72 chars
- PRs: include problem/solution summary, linked issue, commands/tests run, screenshots for UI, and doc updates if relevant
- Target `develop` branch; rebase to reduce noise
- Run lint and tests before submitting
- Follow `CONTRIBUTING.md` and the guidelines in `documentation/docs/developer/guidelines/`
- Use the PR description template in `.github/PULL_REQUEST_TEMPLATE.md`
