---
name: write-tests
description: Write an Artemis server or client test that passes on the first CI run. Use when adding or changing a JUnit test under src/test/java or a Vitest spec under src/main/webapp, when a test passes locally but fails in CI, or when unsure which base class or test command to use. Covers base class selection, the admin naming rule, date comparisons, and the Vitest invocations that silently do the wrong thing.
---

# Write tests that pass first time

Most of the friction in this repository's test suites comes from a handful of specific traps, not
from testing being hard. This skill is the list.

## Server tests

Server tests need Docker. They run against PostgreSQL through Testcontainers, locally and in CI.

```bash
./gradlew test -x webapp                                     # everything
./gradlew test --tests ExamIntegrationTest -x webapp         # one class
./gradlew test --tests ExamIntegrationTest.testGetExamScore  # one method
./gradlew test -DincludeTags='ArchitectureTest' -x webapp    # architecture only, fast
```

Name tests `*Test.java`. Reuse the module's base class where one exists.

Read `reference/server.md` for base class selection, the admin naming rule that forces a different
`@ResourceLock`, date comparison, and shared-spy flakiness.

The one to know before you start: **in the admin module, naming a test `*IntegrationTest` forces it
onto a batch base class carrying a shared `@ResourceLock`.** A test that mutates global state and
needs isolation must be named `*Test` and extend `AbstractSpringIntegrationIndependentTest`
instead. This is enforced by
`src/test/java/de/tum/cit/aet/artemis/admin/architecture/AdminTestArchitectureTest.java`, so getting
it wrong fails the architecture gate rather than the test.

## Client tests

Vitest, not Jest. Use `vi.spyOn()`, `vi.fn()`, `vi.clearAllMocks()`.

```bash
pnpm run vitest                          # watch
pnpm run vitest:run                      # single run, everything
pnpm exec vitest run <path/to/spec.ts>   # single file
pnpm run vitest:coverage
pnpm run test-diff                       # only specs affected by the diff
```

**`pnpm run vitest:run -- <path>` runs the entire suite.** The path is swallowed. Use
`pnpm exec vitest run <path>` for a single file. This wastes a lot of time before people notice.

**Vitest is not the type check CI runs.** CI runs a stricter spec `tsc`:

```bash
pnpm run compile:tests
```

It enforces member visibility, which Vitest does not. A spec that reaches a private member as
`component.privateThing` passes locally and fails in CI. Use bracket access,
`component['privateThing']`, and run `compile:tests` before pushing.

Read `reference/client.md` for the rest: the monaco stub, zoneless test setup, `model()` versus
`input()` plus `output()`, and why template errors need a build rather than a test run.

## Both

Keep tests deterministic. Mock external services and WebSockets. CI enforces per-module coverage
thresholds, so a new class with no test can fail the build even when nothing is broken.

For E2E tests, see `skills/e2e-pr-check/SKILL.md`. Do not add a Playwright test for something a
unit or integration test can cover; the E2E suite is the slowest feedback loop in the project.
