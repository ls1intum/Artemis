# CLAUDE.md

Artemis is an interactive learning platform for programming exercises, quizzes, modeling tasks, and exams, with
automatic and manual assessment. It integrates AI services: Iris (tutoring), Athena (assessment), Hyperion (exercise
creation).

This file holds the **rules** and the **entry points**. It deliberately does not explain them: each rule links to the
guideline page that does, and that page is worth reading before working in the area.

**Procedures** live in [`skills/`](./skills/) and load on demand:

- `local-setup` — fresh clone to a running server and client
- `write-tests` — base class selection and the test commands that silently do the wrong thing
- `e2e-pr-check` — run only the Playwright specs a change affects, and read the result correctly
- `ci-triage` — classify a red build before changing any code
- `server-arch-gates` — the architectural rules a server change must satisfy, and how to check each locally
- `liquibase-migration` — write a changelog that applies cleanly on both databases
- `client-conventions` — Angular signal APIs, cloning, template control flow, TUM UI styling

Install them with `npx skills add ls1intum/Artemis`, or in Claude Code with `/plugin marketplace add ls1intum/Artemis`
then `/plugin install artemis@artemis`. When a rule here changes, update the matching skill in the same pull request.
The contract between this file, the skills, and the guidelines is described in
[`work-with-ai.mdx`](./documentation/docs/developer/work-with-ai.mdx).

## Non-negotiables

Most of these are enforced by a build check; the rest are policy that review enforces. None is a style preference.

### Server

- No `@Transactional`, `TransactionTemplate`, or `PlatformTransactionManager` in services or controllers. Transaction boundaries belong in repositories only. [server-development](./documentation/docs/developer/guidelines/server-development.mdx)
- No `EntityManager` or `EntityManagerFactory` fields anywhere in production code, and no `JdbcClient`, `JdbcTemplate`, or `DataSource` outside `core.config`. Everything else goes through Spring Data repositories; use `@Query(nativeQuery = true)` where there is no entity to name. [server-development](./documentation/docs/developer/guidelines/server-development.mdx)
- No `FetchType.EAGER` on `@OneToOne`, `@OneToMany`, `@ManyToMany`. A `@OneToOne` must spell out `fetch = FetchType.LAZY`, since its default is eager. `@ManyToOne` is out of scope. The allowlist `FIELDS_ALLOWED_TO_FETCH_EAGERLY` may only shrink. [database](./documentation/docs/developer/guidelines/database.mdx)
- Do not fetch a lazy configuration through the entity that owns it, including via `@EntityGraph` or `JOIN FETCH`. Give it its own repository and read it at the decision point — `CourseConfigurationRepository` and `CourseAthenaConfigRepository` are the pattern. [database](./documentation/docs/developer/guidelines/database.mdx)
- A configuration that must not outlive its parent holds the parent's key, and the parent carries no field for it at all: an inverse `@OneToOne` cannot be proxied, so mapping it costs a select on every parent read. Read it through its own repository at the point of use, or pass it alongside the parent; the API carries it in the request and response records. `ProgrammingExerciseBuildConfig` is the pattern. [database](./documentation/docs/developer/guidelines/database.mdx)
- No `@Lob`. A CLOB on PostgreSQL is a large object, so Hibernate stores the value in `pg_largeobject` and the column keeps only its id, while the long text columns here are Liquibase `longtext` or `clob` - both `text` on PostgreSQL - holding the text itself. A `String` or a converted attribute needs no annotation; use `@JdbcTypeCode(SqlTypes.JSON)` over a `json` column for structured values. [database](./documentation/docs/developer/guidelines/database.mdx)
- No `@Cache` (Hibernate L2) on entities or associations. For DTO or projection caching use Spring `@Cacheable`, always paired with explicit eviction: `@CacheEvict` on the writer service, or a Hibernate `PostUpdateEventListener` / `PostDeleteEventListener`. See `TitleCacheEvictionService`, and `PerNodeCacheEvictionService` for propagating a per-node eviction across the cluster. The default answer is: do not cache. [caching](./documentation/docs/developer/guidelines/caching.mdx)
- Never use Hazelcast or Redis directly. All cross-node state goes through `DistributedDataProvider`. Request entry lifetimes at the call site with `getExpiringMap(name, ttl)`; `getMap(name)` rejects a per-entry TTL. Missing capability? Add it to the provider, implement it for all three, extend `AbstractDistributedDataTest`. [distributed-data](./documentation/docs/developer/guidelines/distributed-data.mdx)
- No `String.toLowerCase()` / `toUpperCase()` without a locale, in production **and** test code. `Locale.ROOT` for machine-facing values; `Locale.ENGLISH` only where surrounding code already does for the same kind of value (logins); `equalsIgnoreCase` where only the comparison matters. [server-development](./documentation/docs/developer/guidelines/server-development.mdx)
- Jackson 3: import `tools.jackson`, never `com.fasterxml.jackson.{databind,core,dataformat,datatype}`. **Annotations are the exception** and stay on `com.fasterxml.jackson.annotation` — do not "fix" those imports. Inject the auto-configured `JsonMapper` in Spring beans, use `JsonObjectMapper.get()` outside them, and build with `JsonMapper.builder()` since mappers are immutable. [rest-api](./documentation/docs/developer/guidelines/rest-api.mdx)

### Client

- Signal APIs are mandatory in new code: `input()`, `input.required()`, `output()`, `viewChild()`, `viewChild.required()`, `viewChildren()`, `signal()`, `computed()`, `effect()`, `inject()`. All six legacy decorators are banned: `@Input`, `@Output`, `@ViewChild`, `@ViewChildren`, `@ContentChild`, `@ContentChildren`. [client-development](./documentation/docs/developer/guidelines/client-development.mdx)
- `ngOnChanges` is banned; use `computed()` / `effect()`. `ngOnInit` and `ngOnDestroy` are unaffected.
- Use `@if` / `@for` / `@switch`. Never `*ngIf` / `*ngFor` / `*ngSwitch`.
- Never object spread, `Object.assign`, or `structuredClone` in production client TypeScript — `localRules/prefer-deep-clone` is error-level over all of it, not just entity graphs (specs are exempt). Use `deepClone` from `app/foundation/util/deep-clone.util`, or its companions `cloneWith(x, {…})` and `hydrate(new Course(), dto)`. Importing `cloneDeep` from `lodash-es` is blocked, so all copying goes through the wrappers. Array spread and object rest in destructuring stay fine. [client-development](./documentation/docs/developer/guidelines/client-development.mdx)
- Use TUM UI (`@tumaet/ui-angular`) and Tailwind v4; no new Bootstrap or ng-bootstrap. If TUM UI lacks a reusable capability, **add or evolve a package component** around native HTML or stable Angular CDK primitives, keeping Artemis-specific composition in the application. PrimeNG is a fallback only when the gap cannot reasonably be closed in the same change, and the pull request says so. [tum-ui-kit](./documentation/docs/developer/guidelines/tum-ui-kit.mdx)
- Colours use semantic tokens (`text-state-danger`, component variants), never primitives (`--p-<color>-N`, `text-red-500`) or Bootstrap classes. Never hand-write PrimeNG root classes (`class="p-button"`). [client-theming](./documentation/docs/developer/guidelines/client-theming.mdx)

### Everywhere

- **A class or file nothing can reach is deleted, not left behind**, including one whose only user is its own test. Two required CI checks enforce it on every pull request, even ones touching neither language: `check_dead_code.py` for Java and `knip` for the client. [dead-code](./documentation/docs/developer/guidelines/dead-code.mdx)
- Never write "frontend" or "backend". <!-- terminology-check: allow --> Say **client** (Angular) and **server** (Spring Boot), **provider** for a swappable distributed data implementation and **adapter** for the glue binding one, or name the concrete system. Applies to code, comments, commit messages, **pull request descriptions**, and documentation. Do not label people either: prefer `client developer` / `server developer`. `supporting_scripts/check_terminology.py` fails CI on new occurrences. [terminology](./documentation/docs/developer/guidelines/terminology.mdx)
- Do not commit design documents, specs, plans, or scratch notes. Working notes belong in the pull request or issue.

Enforcement lives in ArchUnit (`ArchitectureTest`, `DistributedDataProviderArchitectureTest`), ESLint local rules
(`rules/*.mjs`), the dead-code and terminology scripts. One ArchUnit violation fails two CI jobs.

## Tech stack

Spring Boot 4.1 on Java 25, MySQL and PostgreSQL, Hibernate, Hazelcast. Angular 22, TypeScript, SCSS, Tailwind v4.
Build with Gradle (wrapper-pinned) and pnpm 12 / Node 24 — pnpm comes from `packageManager` in `package.json`, so run
`corepack enable` once. Tests: JUnit 6, Vitest, Playwright.

## Commands

```bash
# Server
./gradlew bootRun                     # dev server (includes the Angular build)
./gradlew bootRun -x webapp           # server only, pair with `pnpm start`
./gradlew -Pprod -Pwar clean bootWar  # production WAR; add -Psbom for the SBOM

# Client
pnpm install --frozen-lockfile        # use plain `pnpm install` when changing dependencies
pnpm start                            # dev server with HMR

# Quality — run before pushing
./gradlew spotlessApply checkstyleMain modernizer -x webapp
pnpm run lint:fix && pnpm run stylelint && pnpm run prettier:write
python3 supporting_scripts/check_dead_code.py && pnpm run dead-code:client
python3 supporting_scripts/check_terminology.py

# Tests
./gradlew test -x webapp                              # needs Docker: PostgreSQL via Testcontainers
./gradlew test --tests ExamIntegrationTest -x webapp
pnpm run vitest                                       # watch
pnpm exec vitest run path/to/spec.ts                  # single file — see the trap below
pnpm run test-diff                                    # incremental client work
./run-e2e-tests-local-fast.sh --filter "Quiz"         # E2E; --stop tears services down
```

Build output: client assets in `build/resources/main/static`, WAR in `build/libs/`.

**Traps that silently do the wrong thing**

- `pnpm run vitest:run -- path/spec.ts` does **not** filter — it runs all ~1300 spec files. Use `pnpm exec vitest run <path>`.
- Never edit files under `src/main/webapp` while an E2E run is in progress. The dev server reloads the page mid-test and the failure looks like a product bug.

**Which E2E runner** — `run-e2e-tests-local-fast.sh` (single node) for client work;
`run-e2e-tests-local-multinode-fast.sh` for server work needing the cluster; `run-e2e-tests-local-multinode.sh` (full
Docker build, prod-faithful) to reproduce a CI-only failure. The multi-node runners take
`--middleware hazelcast|redis`. Details and flags: the `e2e-pr-check` skill.

## Structure

Server (`src/main/java/de/tum/cit/aet/artemis/`) is organised package-by-feature, and the client
(`src/main/webapp/app/`) mirrors it. Most module names say what they hold (`quiz/`, `lecture/`, `exam/`, `iris/`); the
ones that do not: `core/` (config, security base, base entities), `atlas/` (competencies and learning analytics),
`localvc/` (embedded git server), `localci/` (build job queue and dispatch), `athena/` (ML assessment), `hyperion/`
(LLM exercise creation), `globalsearch/` (Weaviate), `videosource/` (TUM Live integration).

Server tests in `src/test/java/`, E2E in `src/test/playwright/`, client tests co-located with their components.
Spring profiles and Liquibase changelogs in `src/main/resources/`. The API spec is generated at runtime by springdoc
at `/v3/api-docs` and `/swagger-ui`.

## Conventions

**Java** — PascalCase classes, camelCase members, 4-space indent, no wildcard imports. DTOs are records annotated
`@JsonInclude(NON_EMPTY)`. Constructor injection. Use Java 25 features.

**TypeScript** — kebab-case filenames, PascalCase classes, single quotes, 4-space indent. Standalone components.
Prefer `undefined` over `null`. Prefer 100% type safety.

**Everything** — LF endings, final newline, UTF-8, 2-space YAML.

## Testing

Server tests need Docker. Keep tests deterministic and mock external services **and WebSockets**. Name server tests
`*Test.java` and reuse the module base class. Compare `ZonedDateTime` with `toInstant()`, since PostgreSQL does not
preserve the offset. CI enforces coverage thresholds per module.

Client tests use Vitest: `vi.spyOn()`, `vi.fn()`, `vi.clearAllMocks()`, never the Jest equivalents.

E2E locators use `data-testid` first, via `page.getByTestId()` (or `[data-testid="…"]` when it must be combined with
another attribute). **Never bind a locator to a styling class** — Bootstrap, PrimeNG, and Tailwind names describe
appearance, so a restyle breaks the test with nothing in the diff to show it. Use an `id` only where one already
exists for a production reason. Name a new hook after what the element is, kebab-cased
(`archive-download-button`). For third-party markup use the structural API: PrimeNG's `[pt]` pass-through carries a
`data-testid` onto an internal section, declared as a component field rather than a template literal.
[e2e-testing-playwright](./documentation/docs/developer/e2e-testing-playwright.mdx), plus the `write-tests` and
`e2e-pr-check` skills.

## Documentation

All user-facing documentation lives under `documentation/docs/`, split by audience (`admin/`, `instructor/`,
`student/`, `developer/`, `about/`). There is no top-level `docs/`. A `README.md` next to the tool it explains (a
script directory, a docker setup) stays where it is and does not move into the site tree. Pages are Docusaurus `.mdx`
with `id`, `title`, and `sidebar_label` frontmatter and **no H1 in the body**. A new page is unreachable until listed
in the matching `documentation/sidebar-*.ts`, so add it there and link it from related pages. Write in the present
tense for the audience of the folder; do not reference pull requests, issues, or commits, and do not describe the
change relative to a previous release. Read
[documentation](./documentation/docs/developer/guidelines/documentation.mdx) before writing or restructuring a page.

## Commits and pull requests

Concise, imperative commit messages, optionally scoped (`Exam mode: adjust live updates`), bodies wrapped near 72
characters, and no backticks.

A PR title wraps **only the module name** in literal backticks, followed by a colon:

```bash
gh pr create --title '`Development`: Improve documentation'
```

The backticks are characters in the title, so quote the argument with single quotes. Allowed module names live in
`.github/workflows/validate-pr-title.yml`. Do not infer the format from `git log` — GitHub strips the backticks when
it squashes, so merged subjects read `Development: ...` without them.

Target `develop` and rebase. Use the template in `.github/PULL_REQUEST_TEMPLATE.md`: problem and solution, linked
issue, commands run, screenshots for UI changes. Run lint and the relevant tests before submitting. See also
[`CONTRIBUTING.md`](./CONTRIBUTING.md).
