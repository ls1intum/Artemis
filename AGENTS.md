# Repository guidelines

## Task-specific guidance

Read the checked-in skill for the task at hand. Installation is optional for reading these files;
see [Work with AI](documentation/docs/developer/work-with-ai.mdx) for native skill discovery.

| Task                                                        | Guidance                                                   |
| ----------------------------------------------------------- | ---------------------------------------------------------- |
| Run, write, or debug Playwright E2E tests                   | [e2e-pr-check](skills/e2e-pr-check/SKILL.md)               |
| Diagnose failed, stuck, or missing CI checks                | [ci-triage](skills/ci-triage/SKILL.md)                     |
| Change server Java or investigate an architecture violation | [server-arch-gates](skills/server-arch-gates/SKILL.md)     |
| Change a database schema or fix a failing changeset         | [liquibase-migration](skills/liquibase-migration/SKILL.md) |
| Change Angular application or TUM UI code                   | [client-conventions](skills/client-conventions/SKILL.md)   |
| Write or debug JUnit or Vitest tests                        | [write-tests](skills/write-tests/SKILL.md)                 |
| Set up, build, or troubleshoot the local application        | [local-setup](skills/local-setup/SKILL.md)                 |

When changing a convention, update its skill and supporting documentation in the same change.

## Non-negotiables

Most of these are enforced by a build check; the rest are policy that review enforces. None is a style preference.

### Server

- No `@Transactional`, `TransactionTemplate`, or `PlatformTransactionManager` in services or controllers. Transaction boundaries belong in repositories only. [server-development](documentation/docs/developer/guidelines/server-development.mdx)
- No `EntityManager` or `EntityManagerFactory` fields anywhere in production code, and no `JdbcClient`, `JdbcTemplate`, or `DataSource` outside `core.config`. Everything else goes through Spring Data repositories; use `@Query(nativeQuery = true)` where there is no entity to name. [server-development](documentation/docs/developer/guidelines/server-development.mdx)
- No `FetchType.EAGER` on `@OneToOne`, `@OneToMany`, `@ManyToMany`. A `@OneToOne` must spell out `fetch = FetchType.LAZY`, since its default is eager. `@ManyToOne` is out of scope. The allowlist `FIELDS_ALLOWED_TO_FETCH_EAGERLY` may only shrink. [database](documentation/docs/developer/guidelines/database.mdx)
- Do not fetch a lazy configuration through the entity that owns it, including via `@EntityGraph` or `JOIN FETCH`. Give it its own repository and read it at the decision point — `CourseConfigurationRepository` and `CourseAthenaConfigRepository` are the pattern. [database](documentation/docs/developer/guidelines/database.mdx)
- A configuration that must not outlive its parent holds the parent's key, and the parent carries no field for it at all: an inverse `@OneToOne` cannot be proxied, so mapping it costs a select on every parent read. Read it through its own repository at the point of use, or pass it alongside the parent; the API carries it in the request and response records. `ProgrammingExerciseBuildConfig` is the pattern. [database](documentation/docs/developer/guidelines/database.mdx)
- No `@Lob`. A CLOB on PostgreSQL is a large object, so Hibernate stores the value in `pg_largeobject` and the column keeps only its id, while the long text columns here are Liquibase `longtext` or `clob` - both `text` on PostgreSQL - holding the text itself. A `String` or a converted attribute needs no annotation; use `@JdbcTypeCode(SqlTypes.JSON)` over a `json` column for structured values. [database](documentation/docs/developer/guidelines/database.mdx)
- No `@Cache` (Hibernate L2) on entities or associations. For DTO or projection caching use Spring `@Cacheable`, always paired with explicit eviction: `@CacheEvict` on the writer service, or a Hibernate `PostUpdateEventListener` / `PostDeleteEventListener`. See `TitleCacheEvictionService`, and `PerNodeCacheEvictionService` for propagating a per-node eviction across the cluster. The default answer is: do not cache. [caching](documentation/docs/developer/guidelines/caching.mdx)
- Never use Hazelcast or Redis directly. All cross-node state goes through `DistributedDataProvider`. Request entry lifetimes at the call site with `getExpiringMap(name, ttl)`; `getMap(name)` rejects a per-entry TTL. Missing capability? Add it to the provider, implement it for all three, extend `AbstractDistributedDataTest`. [distributed-data](documentation/docs/developer/guidelines/distributed-data.mdx)
- No `String.toLowerCase()` / `toUpperCase()` without a locale, in production **and** test code. `Locale.ROOT` for machine-facing values; `Locale.ENGLISH` only where surrounding code already does for the same kind of value (logins); `equalsIgnoreCase` where only the comparison matters. [server-development](documentation/docs/developer/guidelines/server-development.mdx)
- Jackson 3: import `tools.jackson`, never `com.fasterxml.jackson.{databind,core,dataformat,datatype}`. **Annotations are the exception** and stay on `com.fasterxml.jackson.annotation` — do not "fix" those imports. Inject the auto-configured `JsonMapper` in Spring beans, use `JsonObjectMapper.get()` outside them, and build with `JsonMapper.builder()` since mappers are immutable. [rest-api](documentation/docs/developer/guidelines/rest-api.mdx)

### Client

- Signal APIs are mandatory in new code: `input()`, `input.required()`, `output()`, `viewChild()`, `viewChild.required()`, `viewChildren()`, `signal()`, `computed()`, `effect()`, `inject()`. All six legacy decorators are banned: `@Input`, `@Output`, `@ViewChild`, `@ViewChildren`, `@ContentChild`, `@ContentChildren`. [client-development](documentation/docs/developer/guidelines/client-development.mdx)
- `ngOnChanges` is banned; use `computed()` / `effect()`. `ngOnInit` and `ngOnDestroy` are unaffected.
- Use `@if` / `@for` / `@switch`. Never `*ngIf` / `*ngFor` / `*ngSwitch`.
- Never object spread, `Object.assign`, or `structuredClone` in production client TypeScript — `localRules/prefer-deep-clone` is error-level over all of it, not just entity graphs (specs are exempt). Use `deepClone` from `app/foundation/util/deep-clone.util`, or its companions `cloneWith(x, {…})` and `hydrate(new Course(), dto)`. Importing `cloneDeep` from `lodash-es` is blocked, so all copying goes through the wrappers. Array spread and object rest in destructuring stay fine. [client-development](documentation/docs/developer/guidelines/client-development.mdx)
- Use TUM UI (`@tumaet/ui-angular`) and Tailwind v4; no new Bootstrap or ng-bootstrap. If TUM UI lacks a reusable capability, **add or evolve a package component** around native HTML or stable Angular CDK primitives, keeping Artemis-specific composition in the application. PrimeNG is a fallback only when the gap cannot reasonably be closed in the same change, and the pull request says so. [tum-ui-kit](documentation/docs/developer/guidelines/tum-ui-kit.mdx)
- Colours use semantic tokens (`text-state-danger`, component variants), never primitives (`--p-<color>-N`, `text-red-500`) or Bootstrap classes. Never hand-write PrimeNG root classes (`class="p-button"`). [client-theming](documentation/docs/developer/guidelines/client-theming.mdx)

### Everywhere

- **A class or file nothing can reach is deleted, not left behind**, including one whose only user is its own test. Two required CI checks enforce it on every pull request, even ones touching neither language: `check_dead_code.py` for Java and `knip` for the client. [dead-code](documentation/docs/developer/guidelines/dead-code.mdx)
- Never write "frontend" or "backend". <!-- terminology-check: allow --> Say **client** (Angular) and **server** (Spring Boot), **provider** for a swappable distributed data implementation and **adapter** for the glue binding one, or name the concrete system. Applies to code, comments, commit messages, **pull request descriptions**, and documentation. Do not label people either: prefer `client developer` / `server developer`. `supporting_scripts/check_terminology.py` fails CI on new occurrences. [terminology](documentation/docs/developer/guidelines/terminology.mdx)

Enforcement lives in ArchUnit (`ArchitectureTest`, `DistributedDataProviderArchitectureTest`), ESLint local rules
(`rules/*.mjs`), the dead-code and terminology scripts. One ArchUnit violation fails two CI jobs.

## Repository boundaries

- Server features live under `src/main/java/de/tum/cit/aet/artemis/`; the Angular application is
  under `src/main/webapp/app/`. Keep reusable TUM UI components in `packages/tum-ui`, with no
  imports from the Artemis application. Client tests are co-located; server tests are in
  `src/test/java`, Playwright tests in `src/test/playwright`.
- `src/main/webapp/app/openapi/` is generated client code. Change the API source/generation input
  rather than hand-editing generated output.
- Follow `.editorconfig`: UTF-8, LF, final newlines and two-space YAML indentation.
- Use the Gradle wrapper and the pnpm version pinned by `package.json` (`corepack enable`).
  Runtime and build versions are maintained in `gradle.properties` and `package.json`.
- Before starting or stopping local services, identify the environment and who owns it. The E2E
  runners can kill processes on ports 8080, 9000 and 7921. Reuse a suitable running environment;
  do not stop unrelated services or run mutating tests against production.

## Documentation

- User-facing documentation belongs in `documentation/docs/`, grouped by audience. Tool-local
  READMEs stay beside their tools. Do not create a top-level `docs/` directory.
- Use Docusaurus `.mdx` pages with `id`, `title` and `sidebar_label` frontmatter and no H1 in the body. Register new pages in the matching `documentation/sidebar-*.ts` and link them from related pages. Write in the
  present tense for the audience, without PR/issue history or release-relative prose.
- Do not commit plans, design specs or scratch notes. Keep working notes in the issue or PR;
  maintained documentation belongs on the documentation site.

- For Playwright locators, use `data-testid` first; do not use styling classes. See
  [E2E testing](documentation/docs/developer/e2e-testing-playwright.mdx) and `write-tests`.

## Commits and pull requests

- Target `develop`; rebase to reduce noise. Follow `CONTRIBUTING.md` and the guidelines in
  `documentation/docs/developer/guidelines/`. Use `.github/PULL_REQUEST_TEMPLATE.md`, including
  the problem and solution, linked issue when applicable, checks run, screenshots for UI changes
  and documentation updates when relevant.
- PR titles use a backticked module followed by a colon, e.g. `` `Development`: Improve documentation ``.
  Allowed modules and the exact pattern are in `.github/workflows/validate-pr-title.yml`.
  Do not infer the format from squash-merge subjects, which omit the backticks.
- Commit subjects are concise and imperative, without backticks; wrap bodies near 72 characters.
- Run lint and tests before submitting; report the commands run and any verification gaps.
