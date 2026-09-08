---
name: local-setup
description: Set up or build Artemis locally, or troubleshoot application startup and development tool versions.
---

# Get Artemis running locally

## Prerequisites

Use the Java toolchain in `build.gradle`, the Node version in `gradle.properties`, and the
`packageManager` version in `package.json`. Docker is required for the database and server tests.
For platform-specific installation and IDE configuration, see
`documentation/docs/developer/setup.mdx`.

## Install dependencies

```bash
corepack enable
pnpm install --frozen-lockfile
```

Use `--frozen-lockfile` unless you are deliberately changing dependencies, in which case plain
`pnpm install` lets the lockfile update.

## Two ways to run

**Server with bundled client:**

```bash
./gradlew bootRun
```

**Separate Angular dev server for client HMR:**

```bash
./gradlew bootRun -x webapp   # terminal 1: server only
pnpm start                  # terminal 2: Angular dev server
```

Default ports are 9000 for the client and 8080 for the server; use the active environment
configuration when it overrides them.

## Production builds

```bash
./gradlew -Pprod -Pwar clean bootWar
./gradlew -Pprod -Pwar -Psbom clean bootWar
```

The first builds without an SBOM; the second includes server and client SBOMs. Generation is
opt-in via `-Psbom`; release-eligible CI sets it in `.github/workflows/ci-build.yml`. Without it,
`AdminSbomResource` returns 404 and the admin UI shows an informational banner.
Client assets go to `build/resources/main/static`, WARs to `build/libs`.

## Test users

Playwright credentials and role mappings are defined in `src/test/playwright/support/users.ts`.
Liquibase seeds them from `src/main/resources/config/liquibase/e2e/users.csv` when the `e2e`
context is enabled; do not assume every migrated database contains them.
`src/test/playwright/init/importUsers.spec.ts` verifies these users rather than creating them.
The separate `supporting_scripts/create_test_users.sh` is not needed for Playwright.

## Seeing outgoing mail

For local mail capture, follow `documentation/docs/developer/mailpit-setup.mdx`.

## When it will not start

**"Unable to determine Dialect".** Check database connectivity and the active database profile.
An `autoconfigure.exclude` override can replace the expected exclusions.

**Shutdown after startup.** Read the shutdown error. If it reports Spring Cloud compatibility,
check the Boot and Cloud versions in `gradle.properties` against the supported release train.

**Aggregate health reports DOWN.** Inspect individual health contributors and the readiness and
liveness endpoints. An unavailable optional integration can make aggregate health DOWN.

**Port already in use.** Identify the listener and its owner before stopping it through the
manager that started it. `./run-e2e-tests-local-fast.sh --stop` stops processes recorded in the
runner's PID files and tears down its database; it is not a generic port cleanup command. Use it
only for an E2E stack you own. The LocalVC SSH listener on 7921 lives inside the server JVM.

## Checks

Use `skills/write-tests/SKILL.md` for JUnit and Vitest commands, and
`skills/e2e-pr-check/SKILL.md` for E2E runner selection and service ownership.
Client lint runs with `pnpm run lint`; Java formatting with `./gradlew spotlessApply`.
