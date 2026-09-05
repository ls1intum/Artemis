---
name: ci-triage
description: Work out why an Artemis pull request is red before changing any code. Use when CI fails, a check is stuck or missing, a test looks flaky, or someone asks to fix a failing build. Distinguishes real defects from the known infrastructure and harness failures that look identical to them, and gives the correct way to re-run each job.
---

# Triage a red Artemis build

Most red builds on this repository are real. A meaningful minority are not, and the ones that are
not look exactly like the ones that are. Classify first, then debug. Changing code in response to a
harness failure wastes a full CI cycle and adds a confusing commit to the history.

## Step 1: find out what is actually red

```bash
gh pr checks <pr-number>
gh run view <run-id> --log-failed
```

The only check that gates a merge is the aggregate **All required CI Passed**
(`.github/workflows/ci.yml`). Individual advisory checks going red does not block anything, so
establish whether the failing job is inside that gate before treating it as urgent. Report PR
Coverage is deliberately outside it.

The workflows and the jobs they contain. `ci.yml` calls each one under a shorter caller name (for
example `Build`, `Quality`, `Test`), so a check named `Quality / Server Code Style` is the
`server-style` job of `ci-quality.yml`:

| Workflow                                       | Jobs                                                                                                                                                                    |
| ---------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `.github/workflows/ci-quality.yml`             | Server Code Style, Client Code Style, Client Compilation, Server Code Quality, Query Quality Check                                                                      |
| `.github/workflows/ci-test.yml`                | Server Tests (PostgreSQL), Client Tests                                                                                                                                 |
| `.github/workflows/ci-e2e.yml`                 | Determine Relevant Tests; Phase 1: Relevant E2E Tests; Phase 2: Remaining E2E Tests; Run All E2E Tests (PR); Run All E2E Tests (Non-PR); Report E2E Overall Status      |
| `.github/workflows/ci-build.yml`               | Build .war artifact, Upload Release Artifact, Build and Push Docker Image (PR, amd64), Build and Push Docker Image, Save Docker Image Tag, Sign and Attest Docker Image |
| `.github/workflows/ci-bean-instantiations.yml` | Bean Instantiation Check                                                                                                                                                |
| `.github/workflows/ci-skills.yml`              | Skill Path References                                                                                                                                                   |

Note that `Run All E2E Tests (Non-PR)` is the job that runs on develop. A spec can fail there and
pass in a pull request's phased run, because the two do not use the same topology.

## Step 2: match against the known patterns

Read `reference/known-failure-patterns.md` before reading the logs as though they describe a
defect. It covers, with the tell for each:

- Server Tests reporting no failures alongside a timeout
- one ArchUnit violation turning two separate jobs red
- a pull request that starts no CI at all
- checks that never appear after a push
- the counted gates that are at their limit, where an unrelated change trips them
- the genuinely flaky areas of the Server Tests suite

## Step 3: re-run correctly

Re-running the wrong way wastes a full CI cycle and produces a confusing result.

- **Server Tests: never use `gh run rerun --failed`.** The suite's sharding and reporting mean a
  partial re-run does not reproduce the original conditions. Use a full re-run:
  `gh run rerun <run-id>`.
- **No run started at all**: this is not something a re-run fixes. See the "no CI at all" and
  "dropped event" entries in the reference file.
- **A workflow file changed in the branch**: pushing it needs the `workflow` token scope, and the
  run uses the workflow definition from the branch, not from develop.

## Step 4: only now, debug

Once the failure is classified as real, treat it normally. Reproduce it locally before fixing it:
the local commands for each check are in `skills/server-arch-gates/SKILL.md` for architecture and
style gates, and `skills/write-tests/SKILL.md` for the test suites.

## What not to do

- Do not re-run a job repeatedly hoping it goes green. If it is flaky, say so and name the pattern.
  If it is not, re-running changes nothing.
- Do not raise a timeout to make a test pass. See `skills/e2e-pr-check/SKILL.md`.
- Do not conclude "flaky" from a single failure. A deterministic failure that only happens in CI is
  common in this repository and is usually an environment difference, not chance.
