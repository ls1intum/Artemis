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

On macOS, Homebrew's `openjdk@25` is keg-only, so nothing finds it after installation. Register it
with the system once, rather than exporting `JAVA_HOME` in every shell:

```bash
brew install openjdk@25
sudo ln -sfn "$(brew --prefix openjdk@25)/libexec/openjdk.jdk" /Library/Java/JavaVirtualMachines/openjdk-25.jdk
./gradlew --version   # confirms Gradle picks up JVM 25
```

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

The users you log in as locally are seeded by Liquibase, not created by a script.
`src/main/resources/config/liquibase/e2e/users.csv` provides exactly seven, each with its login as
the password:

| Login                  | Role in the Playwright suite |
| ---------------------- | ---------------------------- |
| `artemis_admin`        | `admin`                      |
| `artemis_test_user_1`  | `studentOne`                 |
| `artemis_test_user_2`  | `studentTwo`                 |
| `artemis_test_user_3`  | `studentThree`               |
| `artemis_test_user_4`  | `studentFour`                |
| `artemis_test_user_6`  | `tutor`                      |
| `artemis_test_user_16` | `instructor`                 |

The numbering is deliberately not contiguous, so do not assume `artemis_test_user_5` exists. The
names are exported from `src/test/playwright/support/users.ts`. A database that has run the
migrations already has these users, and `src/test/playwright/init/importUsers.spec.ts` verifies
them rather than creating anything.

`supporting_scripts/create_test_users.sh` is a different, much smaller thing: it creates three
users, `aa01aaa` through `aa03aaa`, through the admin REST API, and it takes the server as a
required argument:

```bash
supporting_scripts/create_test_users.sh localhost:8080
```

Called without that argument it POSTs to `http://` and silently does nothing. You do not need it
for normal development or for Playwright.

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

**Port already in use.** `./run-e2e-tests-local-fast.sh --stop` frees 8080 and 9000 by killing the
server and client. The LocalVC SSH listener on 7921 lives inside the server JVM, so it goes with it.

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
