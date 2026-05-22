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
├── build           ── uses ci-build.yml                          (workflow_call)
├── test            ── uses ci-test.yml                           (workflow_call)
├── java-analysis   ── uses ci-java-analysis.yml                  (workflow_call, if has_java)
├── gradle-wrapper  ── uses ci-gradle-wrapper.yml                 (workflow_call, if has_gradle)
├── docs            ── uses ci-docs.yml                           (workflow_call, if has_docs)
├── translation     ── uses ci-translation.yml                    (workflow_call, if has_i18n)
├── workflows       ── uses ci-workflows.yml                      (workflow_call, if has_workflows — actionlint)
├── e2e             ── uses ci-e2e.yml                            (workflow_call, after build + test)
└── all-ci-passed                (jq-based gate — the single required status check)
```

### Why one entry point and not many

- **One trigger surface.** Path filters, branch filters, and concurrency live in one place.
- **No `workflow_run` chain.** E2E is invoked directly with `needs: [build, test]`. The old
  `workflow_run` listener (a) ran from the default-branch copy of the workflow file, so PRs
  modifying E2E never tested their own changes, and (b) needed a hand-rolled cancellation
  workaround for queue-stacking on the self-hosted runner pool.
- **Stable required check.** Branch protection requires exactly one job: `CI / All CI Passed`.
  Renaming or adding child jobs does not require updating branch protection.

### Why one required status check and not many

The community consensus pattern in 2026 (TypeScript, Vite, Ruff) is: require a single
umbrella job, evaluate every child's `result` via `jq`, and accept both `success` and
`skipped`. Listing every child as required is fragile — every rename or refactor breaks
branch protection, and path-filtered children leave required checks stuck on "pending".

Cascading skips don't bypass the gate because `detect-changes` (the gating job) is itself
in `needs:` — if it fails, its own `failure` result fails the gate, even when downstream
children all show `skipped`.

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

When this PR lands, branch protection (or the corresponding ruleset) must be updated
from per-workflow checks to one umbrella check.

Recommended path — **GitHub Rulesets** (not legacy branch protection):

1. **Before merging**: dump the current required checks.
   ```bash
   gh api repos/ls1intum/Artemis/branches/develop/protection/required_status_checks/contexts
   ```
   Keep the output in a runbook in case rollback is needed.

2. **Merge this PR**. The new `CI` workflow runs in parallel with the dropped checks
   (since the dropped checks no longer exist, branch protection will allow PRs to merge
   once those required checks are removed from the rule — proceed to step 3 immediately).

3. **Replace the required checks** for `develop`, `main`, and `release/*`:
   - Remove: every per-workflow check that used to come from the deleted files (`Build /
     Build .war artifact`, `Test / server-tests`, `Test / client-tests`, `Test / server-style`,
     `Test / client-style`, `Test / client-compilation`, `Java Code Quality Analysis /
     code-quality-analysis`, `Query Quality Check / query-quality-check`, `Build
     Documentation / build`, `Validate Gradle Wrapper / Gradle Wrapper Validation`, `Check
     if German and English translations are consistent / build`, `End-to-End (E2E) Tests`).
   - Add: **`CI / All CI Passed`** (the only required check from `ci.yml`).
   - Keep: checks coming from retained workflows (e.g. `CodeQL / Analyse`, `Android E2E
     Tests / Android E2E Tests`, `Validate PR Title / validate-pr-title`, `Validate PR
     Description / validate-pr-description`).

4. **Verify** by opening a fresh PR. Wait for `CI / All CI Passed` to turn green.

5. **Rollback path** (only if something blocks merges): re-add the old required-check
   contexts from the dump in step 1 — the deleted files would need to be restored from
   git history. Keep this PR's deletions in a single commit to make `git revert` easy.

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
3. Add `<name>` to the `needs:` list of `all-ci-passed`. The gate accepts `success`
   and `skipped`, so path-filtered skips pass naturally. No branch-protection change
   is needed — the gate's `name:` field is what's required.
