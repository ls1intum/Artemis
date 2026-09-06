# GitHub Actions Workflows

This directory hosts the Artemis CI/CD pipeline. The model is **one entry point + reusable
children + one required status check**.

## Entry point — `ci.yml`

`ci.yml` is the single CI entry point. It registers every trigger that should run the main
CI pipeline (pull requests, pushes to `develop` / `main` / `release/*`, published
releases, merge-queue runs, and a manual `workflow_dispatch`) so that the answer to "what
runs on event X?" is in exactly one file.

```text
ci.yml                                                            (single entry workflow)
├── detect-changes               (dorny/paths-filter, emits per-area booleans)
│
│   REQUIRED — gated by `All required CI Passed` (fast + deterministic, run when relevant):
├── build           ── uses ci-build.yml          (the .war + Docker image)
├── test            ── uses ci-test.yml           (server + client test suites)
├── quality         ── uses ci-quality.yml        (server + client style/lint/type-check, Java analyses)
├── gradle-wrapper  ── uses ci-gradle-wrapper.yml (if has_gradle; wrapper-jar integrity)
├── docs            ── uses ci-docs.yml           (if has_docs)
├── translation     ── uses ci-translation.yml    (if has_i18n)
├── workflows       ── uses ci-workflows.yml      (if .github changed; actionlint)
├── version-consistency ─ uses ci-version-consistency.yml (if has_version; build.gradle/openapi/README in sync)
├── bean-instantiations ─ uses ci-bean-instantiations.yml (if has_beans; boots the app, checks startup bean metrics)
├── e2e             ── uses ci-e2e.yml            (after build; required but flakiness-aware — reds only on a real, non-flaky regression; a known-flaky-only run is exonerated)
│
│   ADVISORY — runs for signal, never blocks merge:
├── codeql          ── uses ci-codeql.yml         (Java + JS/TS security scan; non-fork; not gated)
├── coverage-report                 (internal PRs; posts the coverage table at ~test time; not a check, not gated)
│
│   DEPLOY — develop only, never on a PR:
├── deploy-docs                  (publishes the docs to GitHub Pages; needs `docs`; job-level `pages` concurrency)
├── coverage-badge               (publishes the README coverage figure to the orphan `badges` branch; needs a GREEN `test`)
│
├── all-required-ci-passed       (jq gate over the required jobs — excludes the advisory codeql/coverage-report — the required check)
└── ci-summary                   (Gantt timeline + per-job table; informational)
```

`ci-summary` is a second terminal job (`needs:` every job, `if: always()`). On the run's
**Summary** page it renders a per-job table (job · required/advisory · result), a failure-only
local-fix table, and a Gantt timeline (`Kesin11/actions-timeline`) covering the reusable
children (`Build / …`, `Test / …`), so the critical-path bottleneck is visible at a glance. It
is informational — never required, never in another job's `needs:` — so it never blocks merging.
Its only permission is `actions: read` (the timeline reads the jobs API).

### Required vs. advisory

The single required check is `CI / All required CI Passed`. It gates on every job that is
**fast and deterministic** — `build`, `test`, `quality`, `gradle-wrapper`, `translation`,
`docs`, `workflows`, `version-consistency`, `bean-instantiations`. They run in parallel and finish
within `test`'s window (the lightweight area checks in a minute or two; `bean-instantiations` boots
the app on H2 in a few minutes; `quality`'s slowest job, the ArchUnit run, still under `test`),
so requiring them adds no merge latency. The one slow required check is `e2e` (detailed below):
it is gated too, but with a flakiness-aware verdict, so it blocks only on a real, non-flaky
regression and never on ambient flakiness. Path-skipped jobs report `skipped`, which the gate
accepts — so a job only blocks merge when it is *relevant and red*.

`quality` (`ci-quality.yml`) is where all static analysis lives, for **both** server and
client — Java/TypeScript style, lint, type-check, architecture, plus the Java-only analyses
(class-dependency caps, query over-fetching). `test` (`ci-test.yml`) runs only the server and
client test suites. This split (mirroring Angular/TypeScript/Vite) keeps a 30-second style
failure from being buried behind the multi-minute test jobs; the Java analyses are the server
half of a symmetric `quality` stage, alongside the client checks.

`e2e` **is** part of the required `all-required-ci-passed` gate, but with a flakiness-aware verdict
so it blocks only on genuine regressions rather than on noise:

- **`e2e`.** E2E takes up to ~2 hours and is flaky enough that a naive required gate would block
  good PRs on noise. Instead, `report-results` classifies each surviving failure against Helios
  history (`classify-failures.js`): a real (non-flaky) regression fails the job and **blocks
  merge**, while a run whose only failures are known-flaky is **exonerated and passes green**. The
  per-test detail (✅/⚪/❌ per phase, plus Helios flakiness scores) lives in the E2E PR comment. The
  test steps are `continue-on-error`, so the honest verdict is decided once in `report-results`, not
  by any single phase job.

`codeql` is deliberately **advisory** (not in the gate's `needs:`): it runs for signal and reds the
run on a genuine failure, but never blocks merge.

- **`codeql`.** Static security analysis (Java + JS/TS) on every code-relevant PR/push. It is
  advisory because CodeQL must build the code itself to trace it (it cannot reuse `build`'s WAR),
  so it is a slow, heavyweight job that should not pace merge — but it runs on the abundant
  GitHub-hosted pool, in parallel, so it adds no load on the self-hosted runners and no merge
  latency. Fork PRs are excluded (the SARIF upload needs `security-events: write`, which fork
  tokens lack); forks are still covered by the post-merge push scan and the weekly scheduled
  scan. The `schedule:` cron lives in `codeql-analysis.yml` because cron cannot be expressed in a
  `workflow_call` reusable; that thin remnant reuses `ci-codeql.yml`, so the scan steps exist in
  one place.

To change the required set, edit `all-required-ci-passed`'s `needs:` (and mirror it in
`ci-summary`'s `ADVISORY` env, which labels the table). Branch protection still references one
context, so it never needs touching again.

### Why one entry point and not many

- **One trigger surface.** Path filters, branch filters, and concurrency live in one place.
- **No `workflow_run` chain.** E2E is a direct `needs: [build]` dependency, never a separate
  `workflow_run`-triggered workflow. A `workflow_run` listener executes the *default-branch*
  copy of the workflow file — so a PR editing E2E couldn't test its own changes — and needs a
  hand-rolled cancellation workaround for queue-stacking. Keep E2E a direct `needs:` edge.
- **Stable required check.** Branch protection requires exactly one job:
  `CI / All required CI Passed`. Renaming or adding child jobs does not require updating it.

### Why `build_relevant` uses ignore-semantics

`detect-changes` decides whether the required `build`, `test`, and `quality` jobs run. It uses
**ignore-semantics**: they run unless *every* changed file is clearly irrelevant
(markdown, `LICENSE`, or under `documentation/`). A new code or config path therefore causes
an *over-run* (safe), never a silent skip of a required check (which would merge unbuilt
code). This is implemented as a dedicated `dorny/paths-filter` step with
`predicate-quantifier: every` and negation patterns — under the action's default `some`
quantifier the negations are inert, so the area filters (positive allow-lists) live in a
second step. Area filters include their own `ci-*.yml` workflow and helper-script paths, so
a PR that edits a required check also runs that check. Cascading skips don't bypass the gate
either: `detect-changes` is in the gate's `needs:`, so a change-detection failure fails the
gate closed.

## Action pinning policy

- **Third-party actions** (anything outside `actions/*`, `github/*`) are pinned to a
  40-character commit SHA with a `# vX.Y.Z` trailing comment. This is the
  [GitHub-recommended supply-chain mitigation](https://docs.github.com/en/actions/security-for-github-actions/security-guides/security-hardening-for-github-actions#using-third-party-actions)
  and matches the org policy GitHub now supports enforcing.
- **First-party `actions/*` and `github/*` actions** may use a major-version tag
  (`@v6`, `@v9`) because they are governed by GitHub's own release process.
  `actionlint`'s install script is pinned to a release tag because the script itself
  is the third party, not the binary it downloads.
- **Don't hand-bump the SHAs.** Renovate (`renovate.json`) reads the `@<sha> # vX.Y.Z`
  format and opens PRs that update both the SHA and the comment together — keep the comment
  in that exact shape so it stays auto-maintained.

## Reusable workflows — `ci-*.yml`

Each `ci-*.yml` file has `on: workflow_call:` and is invoked only by `ci.yml`. Rules:

1. **No `concurrency:` block inside a reusable.** The parent's group already applies. A
   child-level `concurrency:` block can cancel a queued child while the parent run stays
   alive, leaving the parent hung. This is the documented pitfall in
   [actions/runner#3205](https://github.com/actions/runner/issues/3205).
2. **Per-job `permissions:`** with default-deny at workflow level. Reusables cannot elevate
   permissions above what the caller grants — they can only narrow them.
3. **Secrets declared explicitly.** No `secrets: inherit`. The
   [2026 Actions security roadmap](https://github.blog/news-insights/product-news/whats-coming-to-our-github-actions-2026-security-roadmap/)
   removes implicit inheritance.
4. **Inputs typed as `boolean` / `number` where appropriate** (not stringy).

## Retained top-level workflows

These workflows are intentionally NOT folded into the umbrella:

| Workflow | Reason |
|---|---|
| `codeql-analysis.yml` | Holds only the weekly `schedule:` cron (cron cannot live in a `workflow_call` child). The PR/push CodeQL signal is folded into the umbrella as the advisory `codeql` job (→ `ci-codeql.yml`), which the scheduled remnant also reuses. |
| `test-android.yml` | Different self-hosted runner pool, clones a separate repo, 60-minute job. |
| `test-mysql.yml` | Manual-only (`workflow_dispatch`). Sibling DB engine to PostgreSQL. |
| `nightly-lti-interop.yml` | Scheduled default-branch interop check; not part of PR/push CI. |
| `deploy-documentation.yml` | **Split:** the automatic develop-push docs deploy is now ci.yml's `deploy-docs` job (reusing `ci-docs.yml`'s build); this file is the `workflow_dispatch` manual-redeploy fallback. Both share the `pages` concurrency group. |
| `testserver-deployment.yml`, `prod-like-deployment.yml` | Manual deploy workflows. |
| `pullrequest-coverage-reporter.yml` | **Fork PRs only.** Posts the coverage table for fork PRs (where an in-run token can't write); hardened against a `workflow_run` pwn-request. Internal PRs use `ci.yml`'s in-run `coverage-report` job instead, so the table appears at ~test time rather than after the run. |
| All `pull_request_target` / `issues` / `schedule` workflows | Need elevated tokens or run on different trigger surfaces. |

## Concurrency model

| Event | Group key | Cancel in progress? |
|---|---|---|
| `pull_request` | `ci-pr-{N}` | yes |
| `merge_group` | `ci-mq-{head_sha}` | no |
| `release` | `ci-release-{tag}` | no |
| `push` / `workflow_dispatch` | `ci-{github.ref}` | no |

Concurrency lives only on the umbrella. Reusables share the umbrella's `run_id`, so the
parent's concurrency lock applies transitively. **Never** add a `concurrency:` block to a
`ci-*.yml` reusable — it creates a second lock that can deadlock the parent
([actions/runner#3205](https://github.com/actions/runner/issues/3205)).

The one exception is the `deploy-docs` **job** in `ci.yml`, which carries a job-level
`concurrency: { group: pages, cancel-in-progress: false }`. Job-level concurrency is safe (it is
not the reusable-workflow lock that #3205 warns about), and the repo-global `pages` group is what
serializes every GitHub Pages deploy — across umbrella runs and against the manual redeploy in
`deploy-documentation.yml`. `coverage-badge` deliberately has **no** job-level group: the umbrella's
own `ci-${{ github.ref }}` group already serializes develop runs end to end, and nothing outside
`ci.yml` writes the `badges` branch.

## The coverage badge

The `coverage` badge in the root `README.md` is served by Shields from `coverage.json` on the
orphan **`badges`** branch. `ci.yml`'s `coverage-badge` job recomputes it on every `develop` push,
from the same `Server JaCoCo XML` and `Client Coverage Summaries` artifacts the `test` job already
uploads — no test re-run. The figure is
`(server.covered + client.covered) / (server.total + client.total)` over **lines**, the one metric
JaCoCo and Vitest both emit natively, so it is weighted by codebase size rather than being a mean
of two percentages. E2E contributes nothing: `ci-e2e.yml` is black-box Playwright with no JaCoCo
agent and no instrumented client build, so it produces no coverage data.

**The badge is deliberately sticky.** Three guards each cause the previous value to stand rather
than publishing a worse one:

1. The job runs only when `test` **succeeded**. A run with a flaky failure has artificially low
   coverage — tests that never ran cover nothing — so it is never published.
2. `compute-coverage-badge.mjs` refuses a report that is missing, truncated, or measured/covered
   zero lines. Truncation matters more than it sounds: the parser anchors on the last `</package>`,
   so a report cut short mid-package would otherwise read a *sourcefile*-level counter and publish a
   small but entirely plausible wrong number. It therefore asserts that nothing but report-level
   counters separates that anchor from `</report>`.
3. It refuses a value whose combined line **total** collapsed below half the published total. That
   catches a vanished module report without freezing on a genuine coverage regression, which leaves
   the denominator intact.

Every rejection is a no-op: the job exits 0 without committing, and Shields keeps serving the last
good file. Nothing about the badge can turn `develop` red.

**Guard 3 latches, by construction.** It compares against the last *published* total, which only
advances when something is published. So a legitimate halving of the combined line total — a large
module deleted, or Vitest's `include` narrowed — trips it on every subsequent develop push rather
than settling. That is why the script exits **4** for a tripped guard and **3** for the routine
"value unchanged" case: the job turns 4 into a `::warning::`, which is the only signal that would
make such a freeze noticeable. The escape hatch is a manual re-seed, documented in
[the script's README](../../supporting_scripts/code-coverage/coverage-badge/README.md).

Commits land on `badges` only, never on `develop`, and only when the rendered value actually
changes. Between changes the file's `exact`/`sha`/`updatedAt` fields go stale by design — they feed
only the coarse guard in (3), which does not need to be current. Nothing loops: no push trigger in
this repo matches `badges`, and GitHub does not fire workflows for `GITHUB_TOKEN` pushes.

The logic is unit-tested in `supporting_scripts/code-coverage/coverage-badge/compute-coverage-badge.spec.mjs`,
which runs as part of `pnpm run test:rules` in the client test job.

## Adding a new CI check

1. Create a new reusable `ci-<name>.yml` with `on: workflow_call:` (no concurrency block).
2. Add a job in `ci.yml` (the `permissions:` block is mandatory — the workflow defaults to
   `{}`, so a job without it cannot even `checkout`):
   ```yaml
   <name>:
     name: <Human-readable name>
     needs: detect-changes
     if: needs.detect-changes.outputs.<flag> == 'true'
     uses: ./.github/workflows/ci-<name>.yml
     permissions:
       contents: read  # widen to whatever the reusable's jobs actually need
   ```
3. Wire it into the two terminal jobs:
   - Add `<name>` to the `needs:` list of `all-required-ci-passed` (for a **required** check) —
     the gate accepts `success` and `skipped`, so path-filtered skips pass naturally. No
     branch-protection change is needed; the gate's `name:` field is what's required.
   - Add `<name>` to `ci-summary`'s `needs:` so it appears in the Summary table. For an
     **advisory** check, add it to `ci-summary`'s `ADVISORY` env **instead of** the gate's `needs:`.
