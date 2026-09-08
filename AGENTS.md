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

## Repository boundaries

- Server features live under `src/main/java/de/tum/cit/aet/artemis/`; the Angular application is
  under `src/main/webapp/app/`. Keep reusable TUM UI components in `packages/tum-ui`, with no
  imports from the Artemis application. Client tests are co-located; server tests are in
  `src/test/java`, Playwright tests in `src/test/playwright`.
- `src/main/webapp/app/openapi/` is generated client code. Change the API source/generation input
  rather than hand-editing generated output.
- Use the Gradle wrapper and the pnpm version pinned by `package.json` (`corepack enable`).
  Runtime and build versions are maintained in `gradle.properties` and `package.json`.
- Artemis deliberately differs from common Spring examples: transaction boundaries belong in
  repositories, not services/controllers; cross-node state goes through `DistributedDataProvider`;
  Hibernate second-level caching is disabled. Read the server skill for the rules and exceptions.
- For new client work use signal-based Angular APIs, TUM UI and Tailwind. Read the client skill
  for migration boundaries, cloning rules and contained PrimeNG fallbacks.
- Before starting or stopping local services, identify the environment and who owns it. The E2E
  runners can kill processes on ports 8080, 9000 and 7921. Reuse a suitable running environment;
  do not stop unrelated services or run mutating tests against production.

## Documentation

- User-facing documentation belongs in `documentation/docs/`, grouped by audience. Tool-local
  READMEs stay beside their tools. Do not create a top-level `docs/` directory.
- Use Docusaurus `.mdx` pages and register new pages in the matching `documentation/sidebar-*.ts`.
  Write in the present tense for the audience, without PR/issue history or release-relative prose.
- Do not commit plans, design specs or scratch notes. Keep working notes in the issue or PR;
  maintained documentation belongs on the documentation site.

## Commits and pull requests

- Target `develop` unless working on a stack. Follow `CONTRIBUTING.md` and use
  `.github/PULL_REQUEST_TEMPLATE.md`, including checks run and screenshots for UI changes.
- PR titles use a backticked module followed by a colon, e.g. `` `Development`: Improve documentation ``.
  Allowed modules and the exact pattern are in `.github/workflows/validate-pr-title.yml`.
  Do not infer the format from squash-merge subjects, which omit the backticks.
- Commit subjects are concise and imperative, without backticks; wrap bodies near 72 characters.
- Run checks relevant to the change and report verification gaps.
