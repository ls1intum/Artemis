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
│   REQUIRED — gated by `All required CI Passed`:
├── build           ── uses ci-build.yml          (workflow_call; the .war + Docker image)
├── test            ── uses ci-test.yml           (workflow_call; server + client tests/style)
│
│   ADVISORY — run for signal, never block merge:
├── java-analysis   ── uses ci-java-analysis.yml  (if has_java)
├── gradle-wrapper  ── uses ci-gradle-wrapper.yml (if has_gradle)
├── docs            ── uses ci-docs.yml           (if has_docs)
├── translation     ── uses ci-translation.yml    (if has_i18n)
├── workflows       ── uses ci-workflows.yml      (if .github changed; actionlint)
├── e2e             ── uses ci-e2e.yml            (after build; advisory)
│
├── all-required-ci-passed       (jq gate over build + test — the single required check)
└── ci-summary                   (renders a table of every job's result; informational)
```

`ci-summary` is a second terminal job: it `needs:` every job with `if: always()` and writes a
markdown table (job · required/advisory · result) to the run's **Summary** page, so the whole
pipeline's status is visible at a glance. It is informational — never required, never in another
job's `needs:` — so it never blocks merging even though it waits for the long E2E run to report.

### Required vs. advisory

Branch protection on `develop` today requires exactly five checks — `Build .war artifact`,
`server-tests`, `server-style`, `client-tests`, `client-style` — all produced by the
`build` and `test` reusables. This PR keeps that surface and nothing more: the single
required check is `CI / All required CI Passed`, which depends only on `build` and `test`.

Everything else (`e2e`, `java-analysis`, `gradle-wrapper`, `docs`, `translation`,
`workflows`) runs in parallel, posts its own status, and is **advisory** — exactly as it is
on `develop` today, where E2E and Java quality are not required. Keeping them out of the
gate is deliberate:

- **Speed.** The gate (and therefore merge) is unblocked the moment `build` + `test` finish.
  It does not wait on the up-to-2-hour E2E suite.
- **Reliability.** E2E and other suites are known to be flaky; if they were required, a flaky
  run would block an otherwise-good PR. Advisory keeps flakiness off the merge path.

To promote an advisory job to required, add it to `all-required-ci-passed`'s `needs:` and to
the branch-protection ruleset — both, or branch protection blocks on a check the gate ignores.

### Why one entry point and not many

- **One trigger surface.** Path filters, branch filters, and concurrency live in one place.
- **No `workflow_run` chain.** E2E is invoked directly via `needs: [build]`. The old
  `workflow_run` listener (a) ran from the default-branch copy of the workflow file, so PRs
  modifying E2E never tested their own changes, and (b) needed a hand-rolled cancellation
  workaround for queue-stacking on the self-hosted runner pool.
- **Stable required check.** Branch protection requires exactly one job:
  `CI / All required CI Passed`. Renaming or adding child jobs does not require updating it.

### Why `build_relevant` uses ignore-semantics

`detect-changes` decides whether the required `build`/`test` jobs run. It uses
**ignore-semantics**: build/test run unless *every* changed file is clearly irrelevant
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
| `bean-instantiations.yml` | Java-source-only, independent. Out of scope for this consolidation. |
| `version-consistency.yml` | Trivial, fires on a tiny path set. |
| `deploy-documentation.yml` | Owns the `pages` concurrency slot. |
| `testserver-deployment.yml`, `prod-like-deployment.yml` | Manual deploy workflows. |
| `pullrequest-coverage-reporter.yml` | `workflow_run` consumer of CI artifacts. Listens for `CI`. |
| All `pull_request_target` / `issues` / `schedule` workflows | Need elevated tokens or run on different trigger surfaces. |

## Branch protection migration

`develop` uses **legacy branch protection** (not a ruleset) for required status checks.
As verified at the time of this PR, it requires exactly these five contexts, all produced
by the now-deleted `build.yml` / `test.yml`:

```text
Build .war artifact   server-tests   server-style   client-tests   client-style
```

After merge, these five must be replaced with the single gate. Note the context string:
the PR-checks UI shows it as **`CI / All required CI Passed`**, but the branch-protection
API stores the bare job name — pass **`All required CI Passed`** (no `CI / ` prefix) to the
`required_status_checks` API, exactly as the legacy contexts were stored as `server-tests`
rather than `Test / server-tests`.

1. **Before merging**, snapshot the current state for rollback:
   ```bash
   gh api repos/ls1intum/Artemis/branches/develop/protection/required_status_checks \
     --jq '{strict, contexts}'
   ```

2. **Merge this PR.** The five legacy contexts can no longer report (their workflows are
   deleted), so update branch protection in the same change window:
   ```bash
   gh api -X PATCH repos/ls1intum/Artemis/branches/develop/protection/required_status_checks \
     -F strict=true -f 'checks[][context]=All required CI Passed'
   ```
   Repeat for `main` and each active `release/*`.

3. **Verify** by opening a fresh PR; wait for `CI / All required CI Passed` to turn green.

4. **Rollback** (if merges get blocked): re-PUT the five contexts from the step-1 snapshot
   and restore the deleted workflows from git history. This PR keeps the deletions in one
   commit so `git revert` is clean.

> The advisory checks (`CodeQL / Analyse`, `Android E2E Tests`, `Validate PR Title/Description`)
> are not part of this migration — they were never in the required set and keep reporting as before.

## Helios re-mapping (operations / Helios admin)

Helios (`helios.aet.cit.tum.de`) keys deployment-readiness gates on GitHub workflow IDs.
Deleting the eight legacy workflows leaves any
`environment_required_pre_deployment_workflow` row that referenced them unsatisfiable —
Helios will report `MISSING_RUN` and block deployment to the affected environment.

Run on the Helios DB (or read-replica) post-merge:

```sql
-- Audit: any Artemis env gated on a deleted workflow?
SELECT e.id          AS environment_id,
       e.name        AS environment_name,
       w.id          AS workflow_id,
       w.path
  FROM environment_required_pre_deployment_workflow erpw
  JOIN environment e ON e.id = erpw.environment_id
  JOIN workflow    w ON w.id = erpw.workflow_id
 WHERE w.path IN (
   '.github/workflows/build.yml',
   '.github/workflows/test.yml',
   '.github/workflows/test-e2e.yml',
   '.github/workflows/quality.yml',
   '.github/workflows/query-quality.yml',
   '.github/workflows/gradle-wrapper-validation.yml',
   '.github/workflows/check-translation-keys.yml',
   '.github/workflows/build-documentation.yml');
```

For each row returned, re-point the gate to `ci.yml` via the Helios environment-edit UI
(Settings → Environments → Required pre-deployment workflows → select `CI`).

Empty result set = no action required. Other Helios-tracked fields stay stable: workflow
rows for the deleted files persist with `state=DELETED` (so historical runs are still
queryable), and the flakiness API contract (`HELIOS_REPO_SECRET`, `POST /api/tests/flakiness-scores`)
is unchanged.

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
