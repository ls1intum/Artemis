# Repository Guidelines

## Project Structure & Module Organization
- `src/main/java` contains Spring Boot services, domain models, and REST controllers; keep module-specific code in the existing package hierarchies (e.g., `de.tum.in.www1.artemis.exercise`).
- `src/main/webapp` hosts the Angular client (`app/` for features, `shared/` for cross-cutting utilities) and static assets under `content/`.
- Tests live alongside their targets: backend suites in `src/test/java`, Angular unit tests in `src/test/javascript`, and resource fixtures in `src/test/resources`.

## Build, Test, and Development Commands
- `./gradlew -Pprod -Pwar clean bootWar` builds a deployable server WAR with production settings.
- `./gradlew test -x webapp` runs the backend test suite; append `-DincludeModules=athena` (or similar) to scope modules during local debugging.
- `npm run start` serves the Angular client with HMR against your local backend; use `proxy.conf.mjs` defaults for API routing.
- `npm run webapp:prod` produces an optimized frontend bundle; `npm run webapp:build` is faster for iterative QA builds.

## Coding Style & Naming Conventions
- Java code follows the Spotless profile (`artemis-spotless-style.xml`) with 4-space indentation; run `./gradlew spotlessCheck` (or `spotlessApply`) before committing.
- Additional backend checks include `./gradlew checkstyleMain` and `./gradlew modernizer` to prevent legacy API usage.
- Frontend TypeScript, HTML, and SCSS are linted via `npm run lint` and formatted with `npm run prettier:check`; prefer Angular’s `kebab-case` for directories and `UpperCamelCase` for components/services.
- Enforce descriptive names for feature branches, e.g., `integrated-code-lifecycle/retry-missing-jobs` to mirror module ownership.

## Testing Guidelines
- Back-end: `./gradlew test jacocoTestReport -x webapp` generates coverage; keep new code above the module thresholds enforced by `check-module-coverage`.
- Front-end: `npm run test` runs Jest with coverage; `npm run test-diff` focuses on changes relative to `origin/develop` and should stay green before review.
- Place Angular specs as `*.spec.ts` adjacent to implementation files; backend integration tests belong in `src/test/java/.../integration` with `Test` suffixes.
- Record any manual verification steps (database migrations, Docker compose adjustments) in the PR description.

## Commit & Pull Request Guidelines
- Follow the repository convention `Module: Short imperative summary (#12345)`, mirroring recent commits such as `Development: Add test coverage config (#11386)`.
- Keep commits focused and rebase onto `develop` before opening a PR; avoid merge commits.
- PRs must include linked issues (e.g., `Closes #NNNN`), screenshots or REST payload examples for UI/api tweaks, and summaries of executed commands.
- Validate that both `./gradlew test -x webapp` and `npm run test` pass locally; attach relevant logs when flaky behavior occurs.
