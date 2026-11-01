---
name: ci-triage
description: Diagnose failed, stuck, or missing Artemis CI checks and distinguish test defects from infrastructure failures.
---

# Triage a red Artemis build

## Keep completed implementations available for CI and review

Use drafts only while the implementation is unfinished. Once implementation is complete, open the PR
as non-draft or mark it ready for review immediately. Draft `pull_request` runs defer the entire
`ci-test.yml` workflow (server, client and AI worker sandbox tests), E2E, CodeQL and review bots.
Relevant style, lint, architecture and type checks still run on public runners. Test TypeScript
compilation runs in **Quality / Client Compilation** for drafts; the WAR and draft test-server image builds
remain available. Integration-test feedback arrives after ready-for-review or a manual full run.
Pending or failing CI, reviews and test-server validation do not by themselves require draft status.
Keep unverified checklist items open and require passing checks and approvals before merge; follow
[PR readiness](../../documentation/docs/developer/development-process.mdx#pull-request-readiness).

## Identify the failing check

```bash
gh pr checks <pr-number>
gh run view <run-id> --log-failed
```

The aggregate **All required CI Passed** is defined in `.github/workflows/ci.yml`.
Trace the failing check through its called workflow; job names are prefixed by the caller
(for example, `Quality / Server Code Style`). Check the repository's required-check settings
before claiming that an advisory failure blocks merging.

Draft runs report **Draft checks passed (tests deferred)** instead of the required
**All required CI Passed** check. A green draft run therefore does not validate its untested head
for merge. The `ready_for_review` event starts a normal run, including when the head SHA is unchanged;
wait for its required checks before marking the PR ready to merge.

PR and non-PR E2E jobs can use different topologies: a pull request runs one node, develop runs three
nodes with Redis (Valkey) as distributed data provider. Compare their definitions in
`.github/workflows/ci-e2e.yml` before treating a develop-only failure as flaky.

## Check the validation scope

Ready pull requests run explicit affected server class plans and related client specs. The server planner's
summary lists its class count and modules; mandatory architecture and source-scanning guards remain included,
as does the Gocast handler guard. The client job prints every selected spec, including filesystem-reading
guardrails whose source reads have no import edges. Every changed client graph seed must have related tests;
guardrails cannot hide an untested seed. Shared leaf utilities use the import graph. Shared contracts,
configuration, missing history and ambiguous dependencies fall back to the full suite. Check the
actual planner/helper output rather than assuming a PR always ran a subset.

The two Stylelint entry points, `config/stylelint/stylelint.config.json` and
`config/stylelint/stylelint.config.js`, are client-only inputs. A PR confined to those files skips
server tests; when mixed with Java changes, they do not expand the affected server class plan.
Other configuration and server disk-input changes retain the conservative fallback.

Affected runs keep test results but omit whole-project coverage gates and artifacts. The PR coverage
section states the scope and result; a selected green run does not prove full-suite coverage. E2E
selects mapped modules plus declared cross-module consumers, changed specs, and login/logout/health smoke
tests. Its dependency contract must account for all discovered specs. Ordinary eligible PRs intentionally
skip Phase 2; full callers retain the disjoint complement. Documentation and unit tests accompanying source
changes do not force full E2E. Shared runtime, configuration, helpers and unknown inputs require the full
suite; missing expected results still fail.

Pushes to develop/main/release branches, merge-group events, releases and manual CI runs use full
suites when the Test stage is selected. To validate the complete PR branch before merging, dispatch **CI** with that branch and
**Bypass change detection and run every job** enabled. Without that override, area filtering can skip
an irrelevant Test stage, including on a draft branch. Re-running a ready PR event preserves its affected scope; re-running
a draft PR event keeps tests deferred. A merge group only provides pre-merge full validation when
the repository uses it.

The current-revision check runs immediately as a step inside **Detect Changed Areas**, using
`.github/scripts/pr-ready.js` and the job's existing checkout. It adds no separate runner or
checkout. API failures fail the required gate closed. Obsolete revisions skip expensive work
and are superseded by the new PR run.

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

## Sonar badges stay unchanged after a merge

Compare `api/project_analyses/search?project=ls1intum_Artemis` with the merged SHA, then inspect the
actual **Build and analyze** step. A scan that exits before publication leaves the previous analysis
and badges unchanged. Scanner 6+ has a separate JVM; `SONAR_SCANNER_JAVA_OPTS` sizes its heap,
while `org.gradle.jvmargs` sizes Gradle. The workflow assigns the scanner 6 GiB.

Sonar is required on relevant ready PRs and is included in **All required CI Passed**. Scanner,
upload and server-side quality-gate failures block merging. Both PR and branch scans wait for the
server verdict. To test a repaired workflow and refresh the actual default branch before merging it:

```bash
gh workflow run ci-sonar.yml --ref <workflow-fix-branch> -f analyze_default_branch=true
```

Leave that option false to analyze the selected branch itself. Check the job summary's analyzed
branch/SHA and the completed Sonar analysis revision. Coverage lookup requires that exact SHA,
both complete artifacts and successful full test jobs; unrelated CI failures do not invalidate it.
The required scanner waits for the server verdict; still confirm the published source revision
when diagnosing badges or a manually dispatched recovery. See [workflow policy](../../.github/workflows/README.md#the-code-quality-analyses).

Affected tests emit separate **Sonar Client Coverage** (selected-spec LCOV) and **Sonar Server
Coverage** (selected execution data). Sonar generates Java XML on a GitHub-hosted runner. These
partial reports never feed full-project badges or Codacy; no test suite is rerun for analysis.
Missing or failed producer data cannot be imported as successful coverage.

Fork and token-restricted PRs (including Dependabot) use a secretless exact-head/source-branch/target/public-gate verifier. A maintainer must
review the exact commit, promote it to internal `chore/sonar/pr-<number>`, dispatch the trusted default
branch's **Sonar** workflow with `pull_request_number=<number>`, then rerun only the original Sonar
verification job. Never automatically execute fork build scripts with the token. See the workflow
policy for the promotion procedure. Head or target changes invalidate evidence. Metadata-only
edits neither replace canonical validation nor cancel active coverage reporting.

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
