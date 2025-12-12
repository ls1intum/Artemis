# Repository Guidelines

## Project Structure & Module Organization
- `src/main/java`: Spring Boot Server Application (domain, repositories, REST) organized by feature (exercise, exam, communication, etc.).
- `src/main/webapp`: Angular client; feature modules in `app/`, shared utilities in `app/shared`, assets/translations in `content/`.
- `src/main/resources`: Application `*.yml`, Liquibase changelogs, and static files bundled for production.
- Tests live in `src/test/java` (server), `src/main/webapp` (Jest, co-located to TS components), and `src/test/playwright` (e2e). Docs are in `documentation/`; deployment helpers sit in `docker/`. API spec is generated at runtime by springdoc (served at `/v3/api-docs` and `/swagger-ui`); `src/main/webapp/app/openapi/` contains generated TypeScript client code.

## Build, Test, Development Commands
- Application Server dev: `./gradlew bootRun` (default task); add `-x webapp` when the Angular dev server serves assets.
- Web application dev: `npm start` after `npm install` (runs `prebuild` + `ng serve` with HMR).
- Bundles: `npm run webapp:build` (dev) or `npm run build` / `npm run webapp:prod` (prod) to emit assets to `build/resources/main/static`.
- Production server artifact: `./gradlew -Pprod -Pwar clean bootWar` → `build/libs/Artemis-<version>.war`.
- Testing: `npm test` (Jest + coverage), `npm run test-diff` (changed specs vs `origin/develop`), `npm run test:ci` (coverage + module coverage check); server `./gradlew test`. Java formatting: `./gradlew spotlessCheck` / `spotlessApply`.

## Coding Style & Naming
- EditorConfig: spaces, indent 4 (YAML 2), LF, final newline; TS/JS prefer single quotes.
- Angular files kebab-case (`course-detail.component.ts`); classes/services PascalCase; members camelCase; keep module barrel exports tidy.
- Use latest Angular/TypeScript features; avoid `null` and use `undefined` where possible, avoid spread operator for objects, prefer 100% type safety. 
- Use Angular Signals for component state and obtain dependencies via inject(); the legacy decorator-based state patterns and constructor-based dependency injection are prohibited.
- In Angular templates, always use the built-in control-flow syntax (@if, @for, @switch) and never use legacy structural directives (*ngIf, *ngFor, *ngSwitch).
- Java classes PascalCase, fields camelCase; keep package-by-feature structure; no wildcard imports (Spotless enforces).
- Avoid @Transactional scope, use DTOs (Java records) for REST endpoints, prefer constructor injection, use Java 25 features (records, sealed classes, pattern matching).
- Lint/format: ESLint, Stylelint, Prettier for webapp; Checkstyle, Spotless, Modernizer via Gradle. Run `npm run lint` + `prettier:check` before PRs.

## Testing Guidelines
- Keep tests deterministic; mock external services/WebSockets. Co-locate client specs; name server tests `*Test.java` and reuse module base classes when present.
- Maintain coverage (CI guards it); run `npm run test-diff` for incremental UI work and `./gradlew test` for server changes; add screenshots and short manual test notes for UI tweaks.
- Run individual client tests like `npm run test:one -- --test-path-pattern='src/main/webapp/app/communication/directive/posting\.directive\.spec\.ts$'` (just adapt the path)
- Run individual server tests like `./gradlew test --tests ExamIntegrationTest -x webapp` or `./gradlew test --tests ExamIntegrationTest.testGetExamScore -x webapp` (just adapt the name)
- Make sure to verify linting and checkstyle, client: `npm run lint`, server: `./gradlew checkstyleMain -x webapp`

## Commit & Pull Request Guidelines
- Commits: concise, imperative, scoped where useful (e.g., `Exam mode: adjust live updates`, `build: bump version`); wrap bodies near 72 chars.
- PRs: include problem/solution summary, linked issue, commands/tests run, screenshots for UI, and doc updates if relevant. Target the active dev branch (`develop`), rebase to reduce noise.
- Follow `CONTRIBUTING.md`: in particular, make sure to follow the guidelines referenced in `docs/dev/guidelines.rst`.
