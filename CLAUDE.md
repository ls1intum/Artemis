# CLAUDE.md

Artemis is an interactive learning platform for programming exercises, quizzes, modeling tasks, and exams, with
automatic and manual assessment. It integrates AI services: Iris (tutoring), Athena (assessment), Hyperion (exercise
creation).

This file holds the **rules** and the **entry points**. It deliberately does not explain them.

- **Why a rule exists, and how to apply it in a hard case** → [`documentation/docs/developer/guidelines/`](./documentation/docs/developer/guidelines/). Read the linked page before working in that area.
- **How to carry out a procedure** → [`skills/`](./skills/), which load on demand: `local-setup`, `write-tests`, `e2e-pr-check`, `ci-triage`, `server-arch-gates`, `liquibase-migration`, `client-conventions`.

Install the skills with `npx skills add ls1intum/Artemis`, or in Claude Code with `/plugin marketplace add ls1intum/Artemis` then `/plugin install artemis@artemis`. When a rule below changes, update the matching skill in the same pull request.

## Non-negotiables

These fail the build or corrupt production state. Each is enforced; none is a style preference.

**Server**

- No `@Transactional`, `TransactionTemplate`, or `PlatformTransactionManager` in services or controllers. Transaction boundaries belong in repositories only.
- No `EntityManager`, `JdbcClient`, `JdbcTemplate`, or `DataSource` injection. All persistence goes through Spring Data repositories; use `@Query(nativeQuery = true)` where there is no entity to name.
- No `FetchType.EAGER` on `@OneToOne`, `@OneToMany`, `@ManyToMany`. A `@OneToOne` must spell out `fetch = FetchType.LAZY`, since its default is eager. `@ManyToOne` is out of scope. The allowlist `FIELDS_ALLOWED_TO_FETCH_EAGERLY` may only shrink.
- Do not fetch a lazy configuration through the entity that owns it. Give it its own repository and read it at the decision point — `CourseAthenaConfigRepository` is the pattern.
- No `@Cache` (Hibernate L2) on entities or associations. For DTO or projection caching use Spring `@Cacheable`, always paired with explicit eviction. The default answer is: do not cache.
- Never use Hazelcast or Redis directly. All cross-node state goes through `DistributedDataProvider`. Missing capability? Add it there, implement it for all three providers, extend `AbstractDistributedDataTest`.
- No `String.toLowerCase()` / `toUpperCase()` without a locale. Use `Locale.ROOT` for machine-facing values; prefer `equalsIgnoreCase` where only the comparison matters.
- Jackson 3: import `tools.jackson`, never `com.fasterxml.jackson.{databind,core,dataformat,datatype}`. **Annotations are the exception** and stay on `com.fasterxml.jackson.annotation` — do not "fix" those imports. Mappers are immutable; build with `JsonMapper.builder()`.

**Client**

- Signal APIs are mandatory in new code: `input()`, `output()`, `viewChild()`, `signal()`, `computed()`, `effect()`, `inject()`. The legacy decorators (`@Input`, `@Output`, `@ViewChild`, …) must not appear.
- `ngOnChanges` is banned; use `computed()` / `effect()`. `ngOnInit` and `ngOnDestroy` are unaffected.
- Use `@if` / `@for` / `@switch`. Never `*ngIf` / `*ngFor` / `*ngSwitch`.
- Copy entity-like objects with `deepClone` from `app/foundation/util/deep-clone.util` — never object spread, `Object.assign`, or `structuredClone`. Companions: `cloneWith(x, {…})` and `hydrate(new Course(), dto)`. Array spread and object rest in destructuring stay fine.
- Use TUM UI (`@tumaet/ui-angular`) and Tailwind v4. No new Bootstrap or ng-bootstrap. PrimeNG is a transitional fallback only, explained in the pull request.
- Colours use semantic tokens (`text-state-danger`, component variants), never primitives (`--p-<color>-N`, `text-red-500`) or Bootstrap classes. Never hand-write PrimeNG root classes (`class="p-button"`).

**Everywhere**

- Never write "frontend" or "backend". <!-- terminology-check: allow --> Say **client** (Angular) and **server** (Spring Boot), or name the concrete system. Applies to code, comments, commit messages, and documentation; `supporting_scripts/check_terminology.py` fails CI on new occurrences.
- Do not commit design documents, specs, plans, or scratch notes. Working notes belong in the pull request or issue.

Enforcement lives in ArchUnit (`ArchitectureTest`, `DistributedDataProviderArchitectureTest`), ESLint local rules (`rules/*.mjs`), and the terminology script. One ArchUnit violation reds two CI jobs.

## Tech stack

Spring Boot 4.1 on Java 25, MySQL and PostgreSQL, Hibernate, Hazelcast. Angular 22, TypeScript, SCSS, Tailwind v4.
Build with Gradle (wrapper-pinned) and pnpm 11 / Node 24 — pnpm comes from `packageManager` in `package.json`, so run
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
./gradlew spotlessApply checkstyleMain -x webapp
pnpm run lint:fix && pnpm run prettier:write

# Tests
./gradlew test -x webapp                              # needs Docker: PostgreSQL via Testcontainers
./gradlew test --tests ExamIntegrationTest -x webapp
pnpm run vitest                                       # watch
pnpm exec vitest run path/to/spec.ts                  # single file — see the trap below
./run-e2e-tests-local-fast.sh --filter "Quiz"         # E2E; --stop tears services down
```

Build output: client assets in `build/resources/main/static`, WAR in `build/libs/`.

**Traps that silently do the wrong thing**

- `pnpm run vitest:run -- path/spec.ts` does **not** filter — it runs all ~1300 spec files. Use `pnpm exec vitest run <path>`.
- Never edit files under `src/main/webapp` while an E2E run is in progress. The dev server reloads the page mid-test and the failure looks like a product bug.

**Which E2E runner** — `run-e2e-tests-local-fast.sh` (single node) for client work; `run-e2e-tests-local-multinode-fast.sh` for server work needing the cluster; `run-e2e-tests-local-multinode.sh` (full Docker build, prod-faithful) to reproduce a CI-only failure. The multi-node runners take `--middleware hazelcast|redis`. Details and flags: the `e2e-pr-check` skill.

## Structure

Server (`src/main/java/de/tum/cit/aet/artemis/`) is organised package-by-feature, and the client
(`src/main/webapp/app/`) mirrors it. Most module names say what they hold (`quiz/`, `lecture/`, `exam/`, `iris/`); the
ones that do not: `core/` (config, security base, base entities), `atlas/` (competencies and learning analytics),
`localvc/` (embedded git server), `localci/` (build job queue and dispatch), `athena/` (ML assessment), `hyperion/`
(LLM exercise creation), `globalsearch/` (Weaviate).

Server tests in `src/test/java/`, E2E in `src/test/playwright/`, client tests co-located with their components.
Spring profiles and Liquibase changelogs in `src/main/resources/`. The API spec is generated at runtime by springdoc
at `/v3/api-docs`.

## Conventions

**Java** — PascalCase classes, camelCase members, 4-space indent, no wildcard imports. DTOs are records annotated
`@JsonInclude(NON_EMPTY)`. Constructor injection. Use Java 25 features.

**TypeScript** — kebab-case filenames, PascalCase classes, single quotes, 4-space indent. Standalone components.
Prefer `undefined` over `null`. Aim for full type safety.

**Everything** — LF endings, final newline, UTF-8, 2-space YAML.

## Testing

Server tests need Docker. Keep tests deterministic and mock external services. Name server tests `*Test.java` and
reuse the module base class. Compare `ZonedDateTime` with `toInstant()`, since PostgreSQL does not preserve the
offset.

E2E locators use `data-testid` first, via `page.getByTestId()`. **Never bind a locator to a styling class** —
Bootstrap, PrimeNG, and Tailwind names describe appearance, so a restyle breaks the test with nothing in the diff to
show it. Use an `id` only where one already exists for a production reason. For third-party markup use the
structural API (PrimeNG `[pt]` pass-through, `getByRole`).

Full guidance: the `write-tests` and `e2e-pr-check` skills.

## Documentation

All user-facing documentation lives under `documentation/docs/`, split by audience (`admin/`, `instructor/`,
`student/`, `developer/`, `about/`). There is no top-level `docs/`. Pages are Docusaurus `.mdx` with `id`, `title`,
and `sidebar_label` frontmatter and **no H1 in the body**. A new page is unreachable until listed in the matching
`documentation/sidebar-*.ts`. Write in the present tense for the audience of the folder; do not reference pull
requests, issues, or commits. Conventions: [`guidelines/documentation.mdx`](./documentation/docs/developer/guidelines/documentation.mdx).

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
issue, commands run, screenshots for UI changes. Run lint and the relevant tests before submitting.
