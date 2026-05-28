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
│
│   ADVISORY — runs for signal, never blocks merge:
├── e2e             ── uses ci-e2e.yml            (after build; slow + flaky → not gated)
│
├── all-required-ci-passed       (jq gate over all of the above except e2e — the required check)
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
`docs`, `workflows`. They run in parallel and finish within `test`'s window (the lightweight
area checks in a minute or two; `quality`'s slowest job, the ArchUnit run, still under `test`),
so requiring them adds no merge latency. Path-skipped jobs report `skipped`, which the gate
accepts — so a job only blocks merge when it is *relevant and red*.

`quality` (`ci-quality.yml`) is where all static analysis lives, for **both** server and
client — Java/TypeScript style, lint, type-check, architecture, plus the Java-only analyses
(class-dependency caps, query over-fetching). `test` (`ci-test.yml`) runs only the server and
client test suites. This split (mirroring Angular/TypeScript/Vite) keeps a 30-second style
failure from being buried behind the multi-minute test jobs; the Java analyses are the server
half of a symmetric `quality` stage, alongside the client checks.

`e2e` is the deliberate exception: it is **advisory** (not in the gate's `needs:`). It runs
for signal and posts its own status, but never blocks merge.

- **Speed.** E2E takes up to ~2 hours; the gate must not wait on it.
- **Reliability.** E2E is flaky enough that requiring it would block good PRs on noise. Once it
  is stabilised behind a merge queue (the `merge_group` trigger is already wired), it can move
  into the gate.

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
second step. Cascading skips don't bypass the gate either: `detect-changes` is in the gate's
`needs:`, so a change-detection failure fails the gate closed.

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
| `codeql-analysis.yml` | Security/compliance scan. Independent schedule, default-branch only. |
| `test-android.yml` | Different self-hosted runner pool, clones a separate repo, 60-minute job. |
| `test-mysql.yml` | Manual-only (`workflow_dispatch`). Sibling DB engine to PostgreSQL. |
| `bean-instantiations.yml` | Java-source-only; independent, niche path filter. |
| `version-consistency.yml` | Trivial, fires on a tiny path set. |
| `nightly-lti-interop.yml` | Scheduled default-branch interop check; not part of PR/push CI. |
| `deploy-documentation.yml` | Owns the `pages` concurrency slot. |
| `testserver-deployment.yml`, `prod-like-deployment.yml` | Manual deploy workflows. |
| `pullrequest-coverage-reporter.yml` | `workflow_run` consumer of CI artifacts. Listens for `CI`. |
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

## Adding a new CI check

1. Create a new reusable `ci-<name>.yml` with `on: workflow_call:` (no concurrency block).
2. Add a job in `ci.yml`:
   ```yaml
   <name>:
     name: <Human-readable name>
     needs: detect-changes
     if: needs.detect-changes.outputs.<flag> == 'true'
     uses: ./.github/workflows/ci-<name>.yml
   ```
3. Add `<name>` to the `needs:` list of `all-required-ci-passed`. The gate accepts `success`
   and `skipped`, so path-filtered skips pass naturally. No branch-protection change
   is needed — the gate's `name:` field is what's required.
