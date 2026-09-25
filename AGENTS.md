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

Read the relevant guideline for a rule-governed change. Keep procedures in the matching skill.
When changing a convention, update its skill and supporting documentation in the same change.

## Non-negotiables

These are Artemis-specific rules. The linked guidelines give reasons and exceptions; the skills give procedures.

### Server

- Transactions belong in repositories, not services or controllers. Do not use `@Transactional`,
  `TransactionTemplate` or `PlatformTransactionManager` there. [server development](documentation/docs/developer/guidelines/server-development.mdx)
- Do not keep `EntityManager` or `EntityManagerFactory` fields in production code. Use Spring Data
  repositories; raw JDBC access is limited to `core.config`. [server development](documentation/docs/developer/guidelines/server-development.mdx)
- Do not use `FetchType.EAGER` for `@OneToOne`, `@OneToMany` or `@ManyToMany`. Explicitly set
  `@OneToOne` to LAZY; do not grow `FIELDS_ALLOWED_TO_FETCH_EAGERLY`. [database](documentation/docs/developer/guidelines/database.mdx)
- Do not fetch a lazy configuration through its parent entity, even with `@EntityGraph` or
  `JOIN FETCH`. A dependent configuration keeps the parent's key; the parent has no inverse
  `@OneToOne` field. Read it through its own repository. [database](documentation/docs/developer/guidelines/database.mdx)
- Do not add `@Lob` or Hibernate second-level `@Cache`. Use Spring caching only with explicit
  eviction. [database](documentation/docs/developer/guidelines/database.mdx) · [caching](documentation/docs/developer/guidelines/caching.mdx)
- Cross-node state goes through `DistributedDataProvider`, never direct Hazelcast or Redis access.
  Use `getExpiringMap(name, ttl)` for entries with a lifetime. [distributed data](documentation/docs/developer/guidelines/distributed-data.mdx)
- Specify a locale for Java case conversion, including in tests. Use `Locale.ROOT` for machine
  values; keep `Locale.ENGLISH` only where the same login convention already applies.
  [server development](documentation/docs/developer/guidelines/server-development.mdx)
- Jackson 3 uses `tools.jackson`; annotations remain `com.fasterxml.jackson.annotation`. Inject
  the configured `JsonMapper` in Spring beans; see the [REST API guideline](documentation/docs/developer/guidelines/rest-api.mdx)
  for other contexts.

### Client

- Use signal APIs. `@Input`, `@Output`, `@ViewChild`, `@ViewChildren`, `@ContentChild` and
  `@ContentChildren` are banned in application and test support code; `ngOnChanges` is banned.
  [client development](documentation/docs/developer/guidelines/client-development.mdx)
- Use `@if`, `@for` and `@switch`, not structural directives, and give every `@switch` a `@default`.
  Bind styles with `[style]`, not `[ngStyle]`. [client development](documentation/docs/developer/guidelines/client-development.mdx)
- Declare root services with `@Service()`, not `@Injectable({ providedIn: 'root' })`, and declare
  `inject()` fields before any other member. [client development](documentation/docs/developer/guidelines/client-development.mdx)
- Route guards and resolvers redirect by returning `router.createUrlTree(...)` or a `RedirectCommand`,
  or by throwing a `RedirectCommand` inside an observable; never by calling `router.navigate()`.
  `localRules/no-navigation-in-guard-or-resolver` enforces this. [client development](documentation/docs/developer/guidelines/client-development.mdx#redirecting-from-guards-and-resolvers)
- In production client TypeScript, do not copy objects with spread, `Object.assign` or
  `structuredClone`; use the repository's deep-clone helpers. Array spread and object rest are
  allowed. [client development](documentation/docs/developer/guidelines/client-development.mdx)
- Use TUM UI and Tailwind. Do not add Bootstrap or ng-bootstrap. If TUM UI lacks a reusable
  feature, extend it; use PrimeNG only if that cannot reasonably be done in the same change,
  and explain the fallback in the PR. [TUM UI](documentation/docs/developer/guidelines/tum-ui-kit.mdx)
- Use semantic colour tokens, not primitive colours, Bootstrap classes or hand-written PrimeNG
  root classes. [client theming](documentation/docs/developer/guidelines/client-theming.mdx)

### Everywhere

- Remove unreachable code, even when its only user is its own test. Required checks run on every
  PR: `python3 supporting_scripts/check_dead_code.py` and `pnpm run dead-code:client`.
  [dead code](documentation/docs/developer/guidelines/dead-code.mdx)
- Use **client** and **server**, not "frontend" or "backend". <!-- terminology-check: allow -->
  Use **provider** for a swappable distributed-data implementation and **adapter** for its glue.
  This also applies to commit messages and PR text. [terminology](documentation/docs/developer/guidelines/terminology.mdx)

## Repository boundaries

- The server uses Spring Boot 4.1 and Java 25; the client uses Angular 22. Use the Gradle wrapper,
  Node 24, and the pnpm version pinned in `package.json` (`corepack enable`). Exact versions live
  in `gradle.properties`, `pnpm-workspace.yaml`, and `package.json`.
- Server features live under `src/main/java/de/tum/cit/aet/artemis/`; the Angular application is
  under `src/main/webapp/app/`. Keep reusable TUM UI components in `packages/tum-ui`, with no
  imports from the Artemis application. Client tests are co-located; server tests are in
  `src/test/java`, Playwright tests in `src/test/playwright`.
- `src/main/webapp/app/openapi/` is generated client code. Change the API source/generation input
  rather than hand-editing generated output.
- Before starting or stopping local services, identify the environment and who owns it. The E2E
  runners can kill processes on ports 8080, 9000 and 7921. Reuse a suitable running environment;
  do not stop unrelated services or run mutating tests against production.

## Documentation

- User-facing documentation belongs in `documentation/docs/`, grouped by audience. Tool-local
  READMEs stay beside their tools. Do not create a top-level `docs/` directory.
- When writing documentation, follow the [documentation guideline](documentation/docs/developer/guidelines/documentation.mdx).
  Register new pages in the matching sidebar and link them from related pages.
- Do not commit plans, design specs or scratch notes. Keep working notes in the issue or PR;
  maintained documentation belongs on the documentation site.

## Testing

- For Playwright locators, use `data-testid` first; never use styling classes. See
  [E2E testing](documentation/docs/developer/e2e-testing-playwright.mdx) and `write-tests`.
- Do not edit `src/main/webapp` during an E2E run; hot reload can invalidate the test.
- `pnpm run vitest:run -- <path>` runs the whole suite. Use `pnpm exec vitest run <path>` for one
  file. See [write-tests](skills/write-tests/SKILL.md) for the other test commands.

## Commits and pull requests

- Target `develop`; rebase to reduce noise. Follow `CONTRIBUTING.md` and the guidelines in
  `documentation/docs/developer/guidelines/`. Use `.github/PULL_REQUEST_TEMPLATE.md`, including
  the problem and solution, linked issue when applicable, checks run, screenshots for UI changes
  and documentation updates when relevant.
- PR titles use a backticked module followed by a colon, e.g. `` `Development`: Improve documentation ``.
  Allowed modules and the exact pattern are in `.github/workflows/validate-pr-title.yml`.
  Do not infer the format from squash-merge subjects, which omit the backticks.
- Commit subjects are concise and imperative, without backticks; wrap bodies near 72 characters.
