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

Use the relevant skill for task-specific procedures. When a convention changes, update its skill
and guideline together.

## Non-negotiables

These are Artemis-specific limits. The linked skills and guidelines explain the exceptions and
implementation details.

### Server

- Transactions belong in repositories, not services or controllers. Do not inject `EntityManager`
  or use JDBC outside `core.config`; use Spring Data repositories. See [server architecture](skills/server-arch-gates/SKILL.md).
- Do not use eager fetching on collections or `@OneToOne` associations. Read configurations
  through their own repositories, not their parent entities. Do not add `@Lob` or Hibernate
  second-level `@Cache`.
  See [database](documentation/docs/developer/guidelines/database.mdx) and
  [caching](documentation/docs/developer/guidelines/caching.mdx).
- Cross-node state goes through `DistributedDataProvider`, not direct Hazelcast or Redis access.
  See [distributed data](documentation/docs/developer/guidelines/distributed-data.mdx).
- Use an explicit locale for case conversion in Java, including tests. Jackson 3 uses
  `tools.jackson`; annotations remain `com.fasterxml.jackson.annotation`.
  See [server development](documentation/docs/developer/guidelines/server-development.mdx) and
  [REST API](documentation/docs/developer/guidelines/rest-api.mdx).

### Client

- Use signal APIs and built-in control flow; legacy decorators and `ngOnChanges` are banned.
  Production client code uses the repository's deep-clone helpers instead of object spread,
  `Object.assign`, or `structuredClone`. See [client conventions](skills/client-conventions/SKILL.md).
- Use TUM UI and semantic colour tokens. Do not add Bootstrap or ng-bootstrap. Use PrimeNG
  only when the needed capability cannot reasonably be added to TUM UI in the same change.
  See [TUM UI](documentation/docs/developer/guidelines/tum-ui-kit.mdx) and
  [client theming](documentation/docs/developer/guidelines/client-theming.mdx).

### Everywhere

- Remove unreachable code, including code used only by its own test. The dead-code checks run
  on every pull request. See [dead code](documentation/docs/developer/guidelines/dead-code.mdx).
- Use **client** and **server**, not "frontend" or "backend". <!-- terminology-check: allow -->
  Use **provider** for a swappable distributed-data implementation and **adapter** for its glue.
  This also applies to commit messages and pull request text. See
  [terminology](documentation/docs/developer/guidelines/terminology.mdx).

## Repository boundaries

- Use the Gradle wrapper and the Node/pnpm versions pinned by the repository.
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
- Register new documentation pages in the matching sidebar and link them from related pages.
  See the [documentation guideline](documentation/docs/developer/guidelines/documentation.mdx)
  when editing documentation.
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
