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

PR and non-PR E2E jobs can use different topologies: a pull request runs one node, develop runs three
nodes with Redis (Valkey) as distributed data provider. Compare their definitions in
`.github/workflows/ci-e2e.yml` before treating a develop-only failure as flaky.

## Classify before changing code

Use `reference/known-failure-patterns.md` to distinguish timeouts, architecture gates,
missing workflow runs, and known harness failures from application defects. A single failure
is not evidence of flakiness; compare logs and execution conditions.

A latest PR run that is pending with no jobs can be waiting for an obsolete run to release its
concurrency slot. Follow the cancellation entry in the reference before rerunning or cancelling jobs.

## Re-run when justified

- **Server Tests: never use `gh run rerun --failed`.** The suite's sharding and reporting mean a
  partial re-run does not reproduce the original conditions. Use a full re-run:
  `gh run rerun <run-id>`.
- **No run started at all**: this is not something a re-run fixes. See the "no CI at all" and
  "dropped event" entries in the reference file.
- **A workflow file changed in the branch**: classic PATs need the `workflow` scope to push it.
  Check the triggering event and checked-out revision when identifying the workflow version.

## Check SonarQube Cloud before merging

Required CI and Sonar have separate results. The Sonar workflow is advisory; a skipped job,
missing analysis, or green aggregate does not prove that Sonar found no reliability bugs.
Same-repository PRs are analysed after successful tests. Fork PRs do not receive the analysis
token. Never use `pull_request_target` to run their code with secrets.

Public Artemis results can be read without a token:

```bash
curl --fail --silent --show-error --get 'https://sonarcloud.io/api/project_pull_requests/list' \
  --data-urlencode 'project=ls1intum_Artemis'
curl --fail --silent --show-error --get 'https://sonarcloud.io/api/qualitygates/project_status' \
  --data-urlencode 'projectKey=ls1intum_Artemis' --data-urlencode 'pullRequest=<pr-number>'
curl --fail --silent --show-error --get 'https://sonarcloud.io/api/issues/search' \
  --data-urlencode 'componentKeys=ls1intum_Artemis' --data-urlencode 'pullRequest=<pr-number>' \
  --data-urlencode 'types=BUG' --data-urlencode 'issueStatuses=OPEN,CONFIRMED' \
  --data-urlencode 'ps=500' --data-urlencode 'p=1'
```

First confirm that the PR appears in the analysis list and that the GitHub Sonar check belongs
to its current head SHA. A missing PR can return zero issues; report that as **not analysed**.
Follow issue pagination until all results are read. Also query `impactSoftwareQualities=RELIABILITY`
without `types=BUG`: the newer quality model can assign reliability impact to issues with
other legacy types. Omit `pullRequest` to inspect the main branch; its results are not the PR's results. Keep issue links and the analysed revision in
the review. The [Web API guide](https://docs.sonarsource.com/sonarqube-cloud/appendices/web-api)
describes the public endpoints and authentication for restricted projects.

An on-demand branch analysis is also available with `gh workflow run ci-sonar.yml --ref <branch>`.
Wait for that commit's CI to finish first so the analysis can import its exact coverage reports.
This is a branch analysis, not a substitute for a missing PR analysis.

Fix confirmed issues with regression tests. Do not remove necessary error handling, change a
valid HTTP contract, add rule suppressions, or weaken the quality gate to improve the rating.
For a proved false positive, retain the failing mutation test and report the issue for normal
Sonar classification. Do not claim the rating is restored before a fresh analysis confirms it.

## Investigate Scorecard findings

Read the published report at
`https://api.securityscorecards.dev/projects/github.com/ls1intum/Artemis` and check its
date and commit before comparing it with the working tree. Branch-protection findings
cover release branches as well as `develop`; missing release signatures refer to release
assets, not just GitHub's attestation store.

Pin every external action, including `actions/*` and `github/*`, to a full commit SHA
with a `# vX.Y.Z` comment. Resolve the upstream tag to its commit rather than guessing
the hash. Renovate's `helpers:pinGitHubActionDigests` preset maintains these references.
See `.github/workflows/README.md` for the workflow policy. Run `actionlint -shellcheck=`
after editing workflows.

## Reproduce a defect

Use `skills/server-arch-gates/SKILL.md` for architecture and style checks,
`skills/write-tests/SKILL.md` for JUnit and Vitest, and `skills/e2e-pr-check/SKILL.md`
for Playwright. Do not mask an unexplained failure with retries or a larger timeout.
