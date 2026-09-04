---
name: local-setup
description: Get a local Artemis development environment running from a fresh clone, or fix one that has stopped working. Use when setting up the project for the first time, when the server or client will not start, when Gradle or pnpm complain about versions, or when unsure which command to run for server-only versus full-stack development. Covers prerequisites, the two run modes, test users, and mail capture.
---

# Get Artemis running locally

## Prerequisites

| Tool   | Version          | Note                                                                  |
| ------ | ---------------- | --------------------------------------------------------------------- |
| JDK    | 25               | Pinned by the Gradle toolchain                                        |
| Node   | 24.20.0 or newer | Pinned in `gradle.properties` and `package.json`                      |
| pnpm   | 11.25.0          | Pinned by the `packageManager` field; activate with `corepack enable` |
| Docker | current          | Required for the database and for server tests                        |

Run `corepack enable` once. It activates the exact pnpm version the repository pins, which avoids a
whole category of lockfile arguments.

On macOS, Homebrew's `openjdk@25` is keg-only, so it is not on the path after installation. Symlink
it rather than exporting `JAVA_HOME` in each shell; a permanent symlink means Gradle finds it
without a per-command prefix.

## Install dependencies

```bash
corepack enable
pnpm install --frozen-lockfile
```

Use `--frozen-lockfile` unless you are deliberately changing dependencies, in which case plain
`pnpm install` lets the lockfile update.

## Two ways to run

**Full stack in one command.** Slower to restart, fine for server work where the client rarely
changes:

```bash
./gradlew bootRun
```

**Server and client separately.** This is what you want for client work, because the Angular dev
server does hot module replacement:

```bash
./gradlew bootRun -x webapp   # terminal 1: server only
pnpm start                    # terminal 2: Angular dev server with HMR
```

The client is then on port 9000 and the server on 8080.

Expect roughly thirty seconds of startup. That is the normal cold start, not a symptom. Disabling
feature modules barely changes it, because most of it is Spring context work that lazy
initialisation already defers.

## Test users

The E2E tooling creates the Playwright test users:

```bash
supporting_scripts/create_test_users.sh
```

The fast E2E runner does this as part of its setup, so if you have run
`./run-e2e-tests-local-fast.sh` you already have them.

## Seeing outgoing mail

Artemis only sends mail when it is configured to. To view what it would send, run a local Mailpit
alongside the server and point the mail configuration at it. See
`documentation/docs/developer/mailpit-setup.mdx`.

## When it will not start

**"Unable to determine Dialect".** The Spring profile set does not include a database profile, or
an `autoconfigure.exclude` is replacing rather than merging the expected exclusions.

**The server logs "Started ArtemisApp" but then shuts down.** A Spring Boot and Spring Cloud version
mismatch does exactly this. The two are coupled: a Boot minor bump needs the matching Cloud release
train. Both are pinned in `gradle.properties`.

**Aggregate health reports DOWN.** This does not by itself mean the server is broken. Check the
readiness and liveness endpoints and look for "Started ArtemisApp" in the log; a single unconfigured
optional integration pulls the aggregate down.

**Port already in use.** The E2E runner kills processes on 8080, 9000, and 7921 before starting.
`./run-e2e-tests-local-fast.sh --stop` is a quick way to clear all three.

## Running things

```bash
./gradlew test -x webapp        # server tests, needs Docker
pnpm run vitest                 # client tests, watch mode
./run-e2e-tests-local-fast.sh   # E2E, brings up everything it needs
pnpm run lint                   # client lint
./gradlew spotlessApply         # fix Java formatting
```

Full setup documentation, including IDE configuration and the optional integrations:
`documentation/docs/developer/setup.mdx`.
