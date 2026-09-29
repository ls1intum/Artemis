---
name: ci-triage
description: Diagnose failed, stuck, or missing Artemis CI checks and distinguish test defects from infrastructure failures.
---

# Triage a red Artemis build

## Identify the failing check

```bash
gh pr checks <pr-number>
gh run view <run-id> --log-failed
```

The aggregate **All required CI Passed** is defined in `.github/workflows/ci.yml`.
Trace the failing check through its called workflow; job names are prefixed by the caller
(for example, `Quality / Server Code Style`). Check the repository's required-check settings
before claiming that an advisory failure blocks merging.

PR and non-PR E2E jobs can use different topologies. Compare their definitions in
`.github/workflows/ci-e2e.yml` before treating a develop-only failure as flaky.

## Classify before changing code

Use `reference/known-failure-patterns.md` to distinguish timeouts, architecture gates,
missing workflow runs, and known harness failures from application defects. A single failure
is not evidence of flakiness; compare logs and execution conditions.

## Re-run when justified

- **Server Tests: never use `gh run rerun --failed`.** The suite's sharding and reporting mean a
  partial re-run does not reproduce the original conditions. Use a full re-run:
  `gh run rerun <run-id>`.
- **No run started at all**: this is not something a re-run fixes. See the "no CI at all" and
  "dropped event" entries in the reference file.
- **A workflow file changed in the branch**: classic PATs need the `workflow` scope to push it.
  Check the triggering event and checked-out revision when identifying the workflow version.

## Reproduce a defect

Use `skills/server-arch-gates/SKILL.md` for architecture and style checks,
`skills/write-tests/SKILL.md` for JUnit and Vitest, and `skills/e2e-pr-check/SKILL.md`
for Playwright. Do not mask an unexplained failure with retries or a larger timeout.
