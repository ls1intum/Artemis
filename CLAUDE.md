# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Agent skills

This file holds **facts** about the repository. **Procedures** live in [`skills/`](./skills/) as agent skills, which load only when used and can therefore go into far more depth than this file can afford. Install them with `npx skills add ls1intum/Artemis`, or in Claude Code with `/plugin marketplace add ls1intum/Artemis` followed by `/plugin install artemis@artemis`.

- `e2e-pr-check` — run only the Playwright specs a change affects, and read the result correctly
- `ci-triage` — classify a red build before changing any code
- `server-arch-gates` — the architectural rules a server change must satisfy, and how to check each locally
- `liquibase-migration` — write a changelog that survives a rolling deploy on both databases
- `client-conventions` — Angular signal APIs, cloning, template control flow, TUM UI styling
- `write-tests` — base class selection and the test commands that silently do the wrong thing
- `local-setup` — fresh clone to a running server and client

When a convention below changes, update the corresponding skill in the same pull request. See [`documentation/docs/developer/work-with-ai.mdx`](./documentation/docs/developer/work-with-ai.mdx).

## Project Overview

Artemis is an interactive learning platform for programming exercises, quizzes, modeling tasks, and exams with automatic and manual assessment. It integrates with AI services (Iris for virtual tutoring, Athena for automated assessment, Hyperion for exercise creation).

## Tech Stack

- **Server**: Spring Boot 4.1 (Java 25), MySQL, Hibernate, Hazelcast
- **Client**: Angular 22, TypeScript, SCSS
- **Build**: Gradle 9.6, pnpm 11 / Node 24 (pnpm version pinned via the `packageManager` field in package.json; activate with `corepack enable`)
- **Testing**: JUnit 6, Vitest, Playwright

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
pnpm run vitest:run                  # Single run, whole suite
pnpm run vitest:coverage             # With coverage
pnpm exec vitest run path/to/spec.ts # Single Vitest file
# NOT `pnpm run vitest:run -- path/to/spec.ts`: the path is not forwarded as a filter and the
# whole suite runs (1298 files instead of 1). Use `pnpm exec vitest run <path>` as shown above.

# E2E Tests (Playwright) — preferred way to run locally
# The script auto-kills processes on ports 8080/9000/7921, starts Postgres, server, and client.
./run-e2e-tests-local-fast.sh                              # Run all E2E tests
./run-e2e-tests-local-fast.sh --filter "Quiz"              # Run tests matching "Quiz"
./run-e2e-tests-local-fast.sh --filter "ExamAssessment|SystemHealth"  # Multiple patterns
./run-e2e-tests-local-fast.sh --specs "e2e/exam/ExamResults.spec.ts e2e/lecture/"  # Only these spec paths
./run-e2e-tests-local-fast.sh --stop                       # Stop all services

# --filter is Playwright --grep (matches test TITLES); --specs replaces the spec PATHS that run.
# For "only what my branch changed", resolve the paths first with the same script CI uses:
./.ci/E2E-tests/determine-relevant-tests.sh origin/develop  # prints RELEVANT_TESTS=...

# Multi-node E2E (catches cluster / cache coherence regressions)
# Boots the full production-faithful stack: Postgres, JHipster Registry (Eureka),
# ActiveMQ, 3 Artemis nodes, nginx LB, containerised Playwright. Slower than the
# single-node fast script, but the only way to reproduce multi-node bugs locally.
./run-e2e-tests-local-multinode.sh                         # Full multi-node run (build WAR + image + stack + tests)
./run-e2e-tests-local-multinode.sh --filter "Quiz"         # Multi-node, filtered
./run-e2e-tests-local-multinode.sh --middleware redis      # Run the same suite on Redis instead of Hazelcast
./run-e2e-tests-local-multinode.sh --skip-build --skip-up  # Quick re-run against an already-running stack
./run-e2e-tests-local-multinode.sh --stop                  # Tear everything down

# Multi-node E2E (fast variant) — same topology, host-launched JVMs instead of Docker images
# Skips the Docker image build that dominates the slow path (~5–8 min). Reuses the WAR built by
# Gradle and runs 3 java -jar processes on the host; Postgres/Eureka/ActiveMQ/nginx still run as
# containers. Use this for server-side iteration on multi-node bugs. Cold ~1–2 min, warm ~30 s.
./run-e2e-tests-local-multinode-fast.sh                       # Full run (build WAR + infra + 3 host JVMs + tests)
./run-e2e-tests-local-multinode-fast.sh --filter "Quiz"       # Filter to a subset of tests
./run-e2e-tests-local-multinode-fast.sh --specs "e2e/exam/"   # Only these spec paths
./run-e2e-tests-local-multinode-fast.sh --middleware redis    # Same suite, Redis instead of Hazelcast
./run-e2e-tests-local-multinode-fast.sh --skip-build --skip-up  # Re-run tests against the running stack
./run-e2e-tests-local-multinode-fast.sh --stop                # Tear everything down
```

**Which middleware?** Both multi-node runners take `--middleware hazelcast|redis`. Hazelcast is the default because it is
what production runs; Redis is the supported alternative and has to pass the same suite. With `redis` no Hazelcast
instance is created at all, which is what makes the run a genuine check of the abstraction.

**Which E2E runner should I use?**

- `run-e2e-tests-local-fast.sh` — single node, Angular dev server. Best for client (UI) iteration.
- `run-e2e-tests-local-multinode-fast.sh` — multi-node, WAR run from host. Best for server iteration that needs the cluster (distributed data, ActiveMQ STOMP, LB).
- `run-e2e-tests-local-multinode.sh` — full Docker image build, prod-faithful. Use this to reproduce a CI-only failure or before pushing a multi-node-sensitive change.

## Project Structure

### Server (`src/main/java/de/tum/cit/aet/artemis/`)

Organized by feature module:

- `core/` - Configuration, security base, utilities, base entities
- `account/` - User, authority, passkey, account REST, authentication, LDAP
- `exercise/` - Base exercise functionality
- `programming/` - Programming exercises (lifecycle, grading, repositories)
- `jenkins/` - Jenkins CI backend connector
- `localvc/` - Embedded git server (HTTP + SSH), repo URI handling, VCS access tokens
- `localci/` - Local CI orchestration: build job queue, dispatch, result processing
- `quiz/` - Quiz exercises
- `modeling/` - UML diagram exercises
- `text/` - Text exercises
- `fileupload/` - File upload exercises
- `exam/` - Exam mode
- `assessment/` - Grading and assessment
- `communication/` - Channels, messaging, conversations, FAQs, saved posts
- `notification/` - Course / global / system / push notifications, mail service
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

### Documentation

- **All user-facing Artemis documentation lives under `documentation/docs/`**, split by audience: `admin/`,
  `instructor/`, `student/`, `developer/`, `about/`. There is no top-level `docs/` folder; that was the old Sphinx
  location and anything written there is invisible on the documentation site. A `README.md` next to the tool it
  explains (a script directory, a docker setup) stays where it is and does not move into the site tree.
- Pages are Docusaurus `.mdx` files with `id`, `title` and `sidebar_label` frontmatter. A new page is only reachable
  once it is listed in the matching `documentation/sidebar-*.ts`, so add it there and link it from the related pages.
- Write for the audience of the folder, in the present tense, describing what the reader sees and does in Artemis. Do
  not reference pull requests, issues, or commits, and do not describe the change relative to a previous release.
- **Do not commit design documents, specs, plans, or scratch notes.** Working notes belong in the pull request
  description or the issue, not in the repository. What is worth keeping goes into `documentation/docs/` as a proper
  page for its audience.

### API Specification

- Generated at runtime by springdoc: `/v3/api-docs` and `/swagger-ui`

## Coding Conventions

### Java

- PascalCase for classes, camelCase for fields/methods
- No wildcard imports (Spotless enforces)
- Package-by-feature organization
- 4-space indentation
- **Do not define transaction boundaries in services or controllers.** `@Transactional`, `TransactionTemplate`, and `PlatformTransactionManager` are forbidden there. Transaction boundaries may only be defined inside repositories, typically for modifying queries.
- Do not inject `EntityManager` or `EntityManagerFactory` directly into services or controllers; all persistence operations must go through Spring Data repositories
- Do not inject `JdbcClient`, `JdbcTemplate` or a `DataSource`; write the statement as a `@Query` on a repository (with `nativeQuery = true` where there is no entity to name). An ArchUnit rule (`ArchitectureTest.shouldNotUseRawJdbcDirectly`) enforces this outside `core.config`
- Use DTOs (Java records) for REST endpoints
- Prefer constructor injection for Spring beans
- Use Java 25 features (records, sealed classes, pattern matching)

### Caching

- **Do not add `@Cache` (Hibernate L2) annotations on entities or associations.** Hibernate second-level cache is disabled cluster-wide and an ArchUnit rule (`ArchitectureTest.testNoHibernateSecondLevelCacheAnnotation`) fails the build if any reappears. Reason: `@Modifying @Query` repository methods bypass L2 invalidation, and the absence of service-level `@Transactional` leaves no clean place to coordinate eviction within a REST call — both produced cross-node stale-read bugs in the multi-node cluster (issue #12574, fixed in PR #12578; further cleanup in PR #12579).
- **For DTO / projection caching, use Spring `@Cacheable`.** It resolves against the `RoutingCacheManager` in `core/config/cache/CacheManagerConfiguration`, which serves the per-node caches from a bounded Caffeine cache and every other cache from the distributed data provider. The per-node ones are the blobs of `BlobCacheConfiguration` (`files`, `plantUmlPng`, `plantUmlSvg`) and the titles of `TitleCacheConfiguration`; both expire entries after a TTL, so a cache whose staleness would be visible for long belongs in the distributed manager instead. Always pair `@Cacheable` with explicit eviction — `@CacheEvict` on the writer service, or a Hibernate `PostUpdateEventListener` / `PostDeleteEventListener`. See `TitleCacheEvictionService` for the canonical pattern, and `PerNodeCacheEvictionService` for propagating a per-node eviction across the cluster.
- The bar for adding a new cache: a measured performance gain that justifies the eviction-correctness work. The default answer is: do not cache.
- Full rationale, history, and patterns: `documentation/docs/developer/guidelines/caching.mdx`.

### Distributed data (cross-node state)

- **Never use Hazelcast or Redis directly.** All cross-node state — build job queue, feature toggles, scheduling messages, websocket broker status, LTI state, Pyris jobs, `@Cacheable` caches — goes through `DistributedDataProvider` (`core/service/distributed`). An ArchUnit rule (`DistributedDataProviderArchitectureTest`) fails the build if a production class outside a small, explicitly named set of backend adapters depends on `com.hazelcast..`, `org.redisson..` or `org.springframework.data.redis..`.
- The backend is selected by `artemis.distributed-data.provider` (`Hazelcast` default, `Redis`, `Local`). With `Redis` no Hazelcast instance is created at all, so any direct usage silently loses that state instead of failing.
- Request entry lifetimes at the call site with `getExpiringMap(name, ttl)`; a backend map configuration only applies to that backend. `getMap(name)` rejects a per-entry TTL for exactly this reason.
- Missing capability? Add it to `DistributedDataProvider`, implement it for all three backends, and add a case to `AbstractDistributedDataTest` — that suite is what keeps the backends in agreement.
- Full rationale and patterns: `documentation/docs/developer/guidelines/distributed-data.mdx`.

### TypeScript/Angular

- kebab-case for filenames (`course-detail.component.ts`)
- PascalCase for classes, camelCase for members
- Single quotes, 4-space indentation
- Standalone components preferred
- **Signal-based Angular APIs are mandatory for new code:**
    - Use `input()` / `input.required()` instead of `@Input()`
    - Use `output()` instead of `@Output()`
    - Use `viewChild()` / `viewChild.required()` instead of `@ViewChild()`
    - Use `viewChildren()` instead of `@ViewChildren()`
    - Use `signal()`, `computed()`, and `effect()` for reactive state management
    - Use `inject()` for dependency injection instead of constructor injection
    - Legacy decorators (`@Input`, `@Output`, `@ViewChild`, `@ViewChildren`, `@ContentChild`, `@ContentChildren`) must not be used in new code
    - In modules not yet fully migrated, prefer signal-based APIs for new components but maintain consistency within existing components
    - An ESLint rule (`localRules/enforce-signal-apis`, in `rules/enforce-signal-apis.mjs`) enforces this in fully migrated modules
    - **`ngOnChanges` is banned — use `computed()`/`effect()` instead.** An error-level rule (`localRules/prefer-signal-reactivity-over-ngonchanges`) enforces this across `src/main/webapp/app`, `packages/tum-ui/src/lib`, and `src/test/javascript`, including specs and undecorated base classes. Angular 21 does call inherited `ngOnChanges` hooks and fires them for signal inputs, so this is a consistency ban rather than a correctness fix. A genuinely unavoidable use of `SimpleChanges.previousValue`/`isFirstChange()` or pre-child-initialization ordering needs a detailed comment and a justified line-level `eslint-disable-next-line`. `ngOnInit` and `ngOnDestroy` are unaffected. See `documentation/docs/developer/guidelines/client-development.mdx`.
- **Angular template control flow: use `@if`, `@for`, `@switch`; never use `*ngIf`, `*ngFor`, `*ngSwitch`**
- Avoid `null`, use `undefined` where possible
- **Copy objects with `deepClone`, never with object spread, `Object.assign`, or `structuredClone`**
    - Use `deepClone` from `app/foundation/util/deep-clone.util` (a thin wrapper over lodash `cloneDeep`) whenever you copy an entity-like object — anything that may hold a `dayjs` date, a nested object, a `Map`/`Set`, or a circular reference
    - `structuredClone()` is the worst option: it does not preserve prototypes, so a cloned `dayjs` date comes back as a plain object without its methods
    - Object spread (`{ ...obj }`) and `Object.assign({}, obj)` only copy one level deep, so nested objects and arrays stay shared with the original and later edits mutate both
    - This is why signal updates need care: a signal only notifies when the reference changes, so replace the object rather than mutating it — `const updated = deepClone(current); updated.field = value; return updated;` See `AccountService.setImageUrl` for the canonical pattern
    - Two companions live in the same file: `cloneWith(x, { a, b })` replaces `{ ...x, a, b }` in a single expression (source deep-cloned, overrides applied by reference), and `hydrate(new Course(), dto)` replaces `Object.assign(new Course(), dto)` for giving a parsed server DTO a prototype
    - Enforced by `localRules/prefer-deep-clone` (error, production client TS; specs exempt). Importing `cloneDeep` from `lodash-es` is blocked by `no-restricted-imports` so all copying goes through the wrappers
    - Array spread stays fine: `items.update((items) => [...items, newItem])` is the documented way to append immutably, as does object rest in destructuring (`const { id, ...rest } = post`)
    - When you only need a signal to emit after mutating an object in place, do not copy it at all: declare the signal with `equal: () => false` and re-set the same reference (see `CourseUpdateComponent.commitCourse`). Copying detaches the nested objects children hold and can end in `NG0103`
    - Where the state is not signal-backed, build the replacement object explicitly field by field (see `MetisService.rebuildPostReference`) rather than reaching for a shallow copy
    - Full rationale and examples: `documentation/docs/developer/guidelines/client-development.mdx` (### Cloning objects)
- Prefer 100% type safety
- **UI components: use TUM UI and Tailwind CSS**
    - TUM UI is the target component system; use an existing `@tumaet/ui-angular` component whenever it covers the required behavior.
    - Use Tailwind CSS v4 utilities for application layout. Do not introduce Bootstrap or ng-bootstrap in new work.
    - If TUM UI lacks a reusable capability, add or evolve a package component around native HTML or stable Angular CDK primitives. Keep Artemis-specific composition and policy in the application.
    - PrimeNG is a transitional fallback only when the TUM UI gap cannot reasonably be closed in the same change. Explain the contained fallback in the pull request.
    - **Colours use semantic tokens, never primitives or Bootstrap classes**: use TUM UI component variants or `text-state-danger`/`text-state-success`/`text-state-warning`/`text-state-info` for plain markup. Never use `--p-<color>-N` primitives, `text-red-500`, `text-danger`, or the superseded arbitrary `text-(--danger)` form. Full decision rules and the Bootstrap migration reference: `documentation/docs/developer/guidelines/client-development.mdx` (### Styling).
    - **Never hand-write PrimeNG component root classes** (`class="p-button"`, `class="p-inputtext"`). For a contained legacy fallback, render the real PrimeNG component so its styles load deterministically; `localRules/no-primeng-component-classes` enforces this.
    - See `documentation/docs/developer/guidelines/tum-ui-kit.mdx` for package ownership, public API, theming, stories, and integration rules.

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
- **Client tests: Vitest**
    - Use `vi.spyOn()`, `vi.fn()`, `vi.clearAllMocks()` instead of Jest equivalents
    - Run Vitest: `pnpm run vitest` (watch), `pnpm run vitest:run` (single run), `pnpm run vitest:coverage`
- Name server tests `*Test.java`; reuse module base classes when present
- When comparing `ZonedDateTime` values in tests, use `toInstant()` for comparisons since PostgreSQL stores timestamps as UTC (timezone offset is not preserved through database round-trips)
- **E2E tests: Use `./run-e2e-tests-local-fast.sh`** — this is the intended way to run Playwright E2E tests locally (for both developers and AI agents)
    - The script automatically kills processes on ports 8080, 9000, and 7921 before starting
    - Use `--filter "TestName"` to run specific tests; supports regex patterns (e.g., `--filter "Quiz|Exam"`)
    - After the first run, reuse running services with `--skip-server --skip-client --skip-db`
    - **Never edit files under `src/main/webapp` while a run is in progress** — the dev server rebuilds and reloads the
      page in the browsers Playwright is driving, which fails whichever test is mid-action and looks like a product bug
- **E2E locators: `data-testid` first.** Reach for `page.getByTestId()` (or `[data-testid="..."]` when it has to be
  combined with another attribute, as in `page.locator('[data-testid="archive-download-button"][data-mode="Course"]')`).
    - **Never bind a locator to a styling class.** Bootstrap, PrimeNG and Tailwind class names describe how an element
      looks, so a restyle silently breaks the test and nothing in the diff points at it. `button.btn-primary` in the two
      archive specs is what made them wait ~11 minutes each once that button became a TUM UI button.
    - Use an `id` only when it already exists for a production reason (a label `for`, an `aria-*` reference, an anchor
      target). An id that exists only so a test can find something should be a `data-testid` instead: the test id is a
      contract that tells the next person editing the template that a test depends on it.
    - When adding a hook, name it after what the element is, kebab-cased (`archive-download-button`)
    - For markup a third party renders, use its structural API, not its classes: PrimeNG's `[pt]` pass-through carries
      a `data-testid` onto any internal section (declare it as a component field, not a template literal), a `pc`-prefixed
      section forwards to a component so the attribute goes on its `root`, and `data-p-icon` separates two elements that
      share one section. `page.getByRole('dialog')` covers "whichever dialog is open". Monaco is the one exception: it
      builds its own DOM and its decoration API takes only a class name, so say so in a comment.
    - Asserting a state class is not the same as locating by one. Find the element by its own hook, then assert the
      class, and only when the library exposes that state no other way. Artemis-owned markup should expose an attribute
      (`data-selected`, `data-invalid`) instead.
    - Full rules: `documentation/docs/developer/e2e-testing-playwright.mdx` (### 3. Use uniquely identifiable locators)
- Add screenshots for UI changes in PRs
- Verify linting before submitting: `pnpm run lint`, `./gradlew checkstyleMain -x webapp`

## Commit & PR Guidelines

- Concise, imperative commit messages scoped where useful (e.g. Exam mode: adjust live updates, build: bump version); wrap bodies near 72 chars. Commit messages contain no backticks
- **A PR title wraps the module name in literal backticks and follows it with a colon:** ``​`Development`: Improve documentation``. Only the module before the colon is wrapped, never the whole title and never the text after the colon. The backticks are characters in the title rather than markdown, so quote the title with single quotes so the shell leaves them alone: ``gh pr create --title '`Development`: Improve documentation'``. The allowed module names and the exact pattern live in `.github/workflows/validate-pr-title.yml`, and `validate-pr-title` fails the PR when the title does not match. Do not infer the format from `git log`: GitHub strips the backticks when it squashes, so merged subjects read `Development: ...` without them
- PRs: include problem/solution summary, linked issue, commands/tests run, screenshots for UI, and doc updates if relevant
- Target `develop` branch; rebase to reduce noise
- Run lint and tests before submitting
- Follow `CONTRIBUTING.md` and the guidelines in `documentation/docs/developer/guidelines/`
- Use the PR description template in `.github/PULL_REQUEST_TEMPLATE.md`
