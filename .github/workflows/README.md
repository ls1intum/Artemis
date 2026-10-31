# GitHub Actions Workflows

This directory hosts the Artemis CI/CD pipeline. The model is **one entry point + reusable
children + one required status check**.

## Entry point — `ci.yml`

`ci.yml` is the single CI entry point. It registers every trigger that should run the main
CI pipeline (pull requests, pushes to `develop` / `main` / `release/*`, published Artemis
releases, merge-queue runs, and a manual `workflow_dispatch`) so that the answer to "what
runs on event X?" is in exactly one file. Scoped package releases (`@tumaet/*`)
do not run Artemis builds or Android tests. TUM AET UI uses `release-tum-aet-ui.yml`: a manual run on a
branch validates; a run on an `@tumaet/ui-angular@<version>` tag validates and stages for npm approval.

The one deliberate exception is `ci-gradle-wrapper.yml`: besides being called by `ci.yml` for
Gradle-related pull requests, it also runs directly on pushes to `develop`, `main`, and `release/*`.
OpenSSF Scorecard only treats the committed `gradle-wrapper.jar` as a verified binary when it can
associate a successful Gradle wrapper validation workflow with the latest commit, which it cannot do
reliably through a reusable-workflow call alone. The direct push trigger provides that evidence while
the reusable call keeps wrapper validation inside the existing required PR gate.

`scorecard.yml` starts after that direct validation succeeds on `develop`, using a `workflow_run`
trigger. Starting both workflows on the same push lets Scorecard finish before validation and flag
all six wrapper JARs as unverified binaries. The weekly, manual and branch-protection triggers remain
available to refresh repository settings independently of a push.

```text
ci.yml                                                            (single entry workflow)
├── detect-changes               (dorny/paths-filter, emits per-area booleans)
│
│   REQUIRED — gated by `All required CI Passed` (fast + deterministic, run when relevant):
├── build           ── uses ci-build.yml          (the .war + Docker image)
├── test            ── uses ci-test.yml           (server + client test suites)
├── quality         ── uses ci-quality.yml        (server + client style/lint/type-check, Java analyses)
├── gradle-wrapper  ── uses ci-gradle-wrapper.yml (if has_gradle; wrapper-jar integrity)
├── docs            ── uses ci-docs.yml           (if has_docs, or on every develop push)
├── translation     ── uses ci-translation.yml    (if has_i18n)
├── workflows       ── uses ci-workflows.yml      (if .github changed; actionlint)
├── version-consistency ─ uses ci-version-consistency.yml (if has_version; build.gradle/openapi/README in sync)
├── bean-instantiations ─ uses ci-bean-instantiations.yml (if has_beans; boots the app, checks startup bean metrics)
├── skills          ── uses ci-skills.yml         (if has_skills; every path an agent skill cites still resolves)
├── terminology     ── uses ci-terminology.yml    (always, incl. docs-only PRs; repo-wide component-naming gate)
├── dead-code       ── uses ci-dead-code.yml      (always; unreachable Java classes + unreachable client files)
├── liquibase       ── uses ci-liquibase.yml      (if has_liquibase; changelogs, seed data and baseline replayed on PostgreSQL and MySQL)
├── e2e             ── uses ci-e2e.yml            (after build; required; known-flaky-only failures are exonerated when history is available)
│
│   ADVISORY — runs for signal, never blocks merge:
├── codeql          ── uses ci-codeql.yml         (Java + JS/TS security scan; non-fork; not gated)
├── coverage-report                 (internal PRs; posts the coverage table at ~test time; not a check, not gated)
│
│   DEPLOY — develop only, never on a PR:
├── deploy-docs                  (publishes the docs to GitHub Pages; needs `docs`; job-level `pages` concurrency)
├── coverage-badge               (publishes the README coverage figure to the orphan `badges` branch; needs a GREEN `test`)
├── sonar           ── uses ci-sonar.yml          (SonarQube Cloud grade; needs a GREEN `test`; also dispatchable standalone)
│
├── all-required-ci-passed       (jq gate over the required jobs — excludes the advisory codeql/coverage-report — the required check)
└── ci-summary                   (Gantt timeline + per-job table; informational)
```

`ci-summary` is a second terminal job (`needs:` every required and advisory job, `if: ${{ !cancelled() }}`).
The develop-only deploy jobs — `deploy-docs`, `coverage-badge`, `sonar` — are deliberately outside
its `needs:`, so they never appear in its table and never hold it up. On the run's
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
it is gated too, with a flakiness-aware verdict that exonerates failures known to be flaky.
Without history, failures after Playwright retries remain blocking. Path-skipped jobs report `skipped`, which the gate
accepts — so a job only blocks merge when it is *relevant and red*.

`quality` (`ci-quality.yml`) is where all static analysis lives, for **both** server and
client — Java/TypeScript style, lint, type-check, architecture, plus the Java-only analyses
(class-dependency caps, query over-fetching). `test` (`ci-test.yml`) runs only the server and
client test suites. This split (mirroring Angular/TypeScript/Vite) keeps a 30-second style
failure from being buried behind the multi-minute test jobs; the Java analyses are the server
half of a symmetric `quality` stage, alongside the client checks.

`e2e` **is** part of the required `all-required-ci-passed` gate. Helios history can exonerate known
flakes; without history, the ordinary Playwright verdict applies:

- **`e2e`.** E2E takes up to ~2 hours and is flaky enough that a naive required gate would block
  good PRs on noise. Instead, `report-results` classifies each surviving failure against Helios
  history (`classify-failures.js`): a real (non-flaky) regression fails the job and **blocks
  merge**, while a run whose only failures are known-flaky is **exonerated and passes green**. The
  per-test detail (✅/⚪/❌ per phase, plus Helios flakiness scores) lives in the E2E PR comment. The
  test steps are `continue-on-error`, so the honest verdict is decided once in `report-results`, not
  by any single phase job.

  Helios is optional. A missing secret, HTTP 401/403, outage, timeout or invalid response produces a
  warning and falls back to Playwright: passing tests (including those recovered by its existing
  retries) pass, while failures after retries remain blocking. Tests without matching history are
  classified as `unknown`, not as confirmed regressions or known flakes. An unknown failure cannot
  be exonerated by a known-flaky failure in another phase, and an unknown Phase 1 failure stops
  Phase 2. Infrastructure failures and missing results remain blocking. The history request has a
  ten-second deadline covering the complete response.

`codeql` is deliberately **advisory** (not in the gate's `needs:`): it runs for signal and reds the
run on a genuine failure, but never blocks merge.

- **`codeql`.** Static security analysis (Java + JS/TS) on every PR/push that changes scanned
  code. It is advisory because CodeQL must build the code itself to trace it (it cannot reuse
  `build`'s WAR), so it is a slow, heavyweight job that should not pace merge — but it runs on the
  GitHub-hosted pool, in parallel, so it adds no load on the self-hosted runners and no merge
  latency. That pool is shared with the required jobs, though, and a scan holds a slot for about
  12 minutes, so the job is skipped when it has nothing new to look at: for draft PRs (promoting
  one runs it), and when every changed file is a test, a translation, the generated OpenAPI
  client, a workflow, a skill or documentation (the `codeql_relevant` output of `detect-changes`).
  The same paths are excluded from analysis in `.github/codeql/codeql-config.yml`; keep the two
  lists in sync. Edits to `ci.yml`, `ci-codeql.yml` or the config file still run the scan. Fork
  PRs are excluded (the SARIF upload needs `security-events: write`, which fork tokens lack); forks
  are still covered by the post-merge push scan and the weekly scheduled scan. The `schedule:` cron
  lives in `codeql-analysis.yml` because cron cannot be expressed in a `workflow_call` reusable;
  that thin remnant reuses `ci-codeql.yml`, so the scan steps exist in one place.

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

### Cancellation of superseded pull request runs

The umbrella workflow groups runs by PR number and sets `cancel-in-progress` for pull requests.
A new push cancels the previous revision so its E2E jobs release their self-hosted runners and the
new revision can start.

Jobs that must evaluate failed or skipped dependencies use `!cancelled()` in their job-level `if`,
including Phase 2, the E2E reporter, coverage reporting, the required gate and the CI summary.
This overrides the implicit `success()` check while allowing cancellation. Keep the explicit
result checks that decide whether Phase 2 is eligible and whether required jobs passed.

Do not use job-level `always()` for these jobs: GitHub re-evaluates job conditions during
cancellation, and a true condition keeps the job alive. An obsolete Phase 2 can then wait hours
for a runner while holding the PR concurrency slot and blocking the latest revision. Step-level
`always()` is still appropriate for bounded cleanup and diagnostics inside a cancellable job.
See [GitHub's cancellation reference](https://docs.github.com/en/actions/reference/workflows-and-actions/workflow-cancellation).

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

### Skipping tests on a pull request

The server suite is by far the largest consumer of the self-hosted `aet-large-ubuntu` runners
(seven shards per run), and E2E is the slowest stage, so a **pull request** skips what it cannot
affect. `detect-changes` emits `run_server_tests` and `run_client_tests`, which `ci.yml` passes to
`ci-test.yml`, and `run_e2e`, which gates the `e2e` job. The skipped jobs report `skipped`; the `test` call as a whole still reports `success` when the other
suite passes, which is all the gate sees. A skipped suite produces no coverage artifact (the
coverage reporters only fetch an artifact when the matching `has_*_changes` is true, which implies
the suite ran).

- All three outputs are **deny-lists** in the `relevant` step, so a path nobody has classified runs
  the suite. The server suite is skipped only if every changed file is markdown, `LICENSE`,
  documentation, a client file (`src/main/webapp/**`), a client test (`src/test/javascript`,
  `src/test/vitest`), Playwright (`src/test/playwright`), or under `packages/` or `skills/`. The
  client suite is skipped only if every changed file is markdown, `LICENSE`, documentation, or under
  `src/main/java`, `src/main/resources`, `src/test/java` or `src/test/resources`. `build.gradle`
  and `gradle/` are deliberately in neither list: `supporting_scripts/client/prebuild.mjs` reads the
  app version from `build.gradle`, and the `test:rules` spec reads `gradle/profile_{dev,prod}.gradle`.
- Server tests read some files that the deny-lists (and `build`) treat as irrelevant: the
  translations under `src/main/webapp/i18n/`, `documentation/docs/developer/feature-usage-catalogue.mdx`
  and the markdown templates under `src/main/resources/` (`has_server_test_inputs`). Changing any of
  them runs the server suite, and `test` runs for it even when `build` would skip the whole run (a
  documentation-only change that includes the catalogue runs only the server suite). If a server
  test starts reading another client, documentation or markdown file, add it there, or the suite can
  be skipped under it.
- E2E runs against the built Docker image, so it is skipped only if every changed file is markdown,
  `LICENSE`, documentation, or a test that is not an E2E test: `src/test/java`, `src/test/resources`,
  `src/test/javascript`, `src/test/vitest`, and the `*.spec.ts` files under `src/main/webapp` and
  `packages`. Playwright specs are `*.spec.ts` too, which is why client specs are matched by
  location, and a change under `src/test/playwright` always runs E2E. `build` and the other required
  checks still run.
- A **draft** pull request does not start E2E either; marking it ready for review starts a CI run,
  which runs E2E only if the changes also pass the `e2e_tests` filter above (a pull request that only
  changes tests still skips it). The PR Docker image (`docker-pr` in `ci-build.yml`) is built for
  internal pull requests only, because fork pull requests cannot push to GHCR, and only if the changes
  pass that same filter (`build_pr_image`). That includes Playwright files and drafts: a test server
  deploys the image of an open pull request, drafts included. A pull request that only changes tests
  gets no image, because neither E2E nor a deploy needs it, and a deploy of such a commit has no
  `sha-<commit>` image to find.
- Only `pull_request` events skip. A push to `develop`, a merge group, a manual run and a release
  other than a `@tumaet/*` one run both suites, because the coverage badge and Sonar read both
  suites' coverage there and `develop` is where anything that slipped past a pull request's own run
  would show up. `@tumaet/*` release tags skip `detect-changes` and with it `test`, like every other
  Artemis job.
- A pull request with 3000 changed files or more never skips: the changed files come from the pulls
  API, which stops at 3000, so the filters may not have seen all of them. That covers the server
  suite, the client suite, E2E, the PR image and the Liquibase checks (`has_liquibase`).
- Codacy coverage is uploaded by the server report job only, so an internal pull request that skips
  the server suite uploads none.

## Action pinning policy

- **All external actions**, including `actions/*` and `github/*`, are pinned to a
  40-character commit SHA with a `# vX.Y.Z` trailing comment. This is the
  [GitHub-recommended supply-chain mitigation](https://docs.github.com/en/actions/security-for-github-actions/security-guides/security-hardening-for-github-actions#using-third-party-actions)
  and matches the org policy GitHub now supports enforcing.
- **Local actions and reusable workflows** use relative paths. The `actionlint` install
  script uses a release tag and a verified SHA-256 checksum before execution.
- **Don't hand-bump the SHAs.** Renovate (`renovate.json`) reads the `@<sha> # vX.Y.Z`
  format and opens PRs that update both the SHA and the comment together — keep the comment
  in that exact shape so it stays auto-maintained.
  The `helpers:pinGitHubActionDigests` preset also pins newly added action references.
- **The policy is enforced.** `supporting_scripts/check_action_pinning.py` runs in `ci-workflows.yml`
  and fails on any `uses:` in `.github/workflows` or `.github/actions` that is not a local path,
  a Docker image digest, or `owner/repo@<40-hex SHA> # vX.Y.Z`. It parses the files with PyYAML,
  which the Ubuntu runners provide. Run it locally with
  `python3 supporting_scripts/check_action_pinning.py`; its unit test is
  `supporting_scripts/test_check_action_pinning.py`.

## Reusable workflows — `ci-*.yml`

Most `ci-*.yml` files have `on: workflow_call:` and are invoked only by `ci.yml`. `ci-gradle-wrapper.yml` also has the direct push trigger documented above. Rules:

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
| `dependency-submission.yml` | Submits the Gradle dependency graph on `develop` pushes that change a build file, weekly, and on demand (`workflow_dispatch`). Replaces the repository setting "Automatic dependency submission", which ran on every branch and pull request although GitHub discards a snapshot of any non-default branch. Keep that setting off. |
| `deploy-documentation.yml` | **Split:** the automatic develop-push docs deploy is now ci.yml's `deploy-docs` job (reusing `ci-docs.yml`'s build); this file is the `workflow_dispatch` manual-redeploy fallback. Both share the `pages` concurrency group. |
| `testserver-deployment.yml`, `prod-like-deployment.yml` | Manual deploy workflows. |
| `pullrequest-coverage-reporter.yml` | **Fork PRs only.** Posts the coverage table for fork PRs (where an in-run token can't write); hardened against a `workflow_run` pwn-request. Internal PRs use `ci.yml`'s in-run `coverage-report` job instead, so the table appears at ~test time rather than after the run. |
| All `pull_request_target` / `issues` / `schedule` workflows | Need elevated tokens or run on different trigger surfaces. |

## E2E image identity and readiness

Both build paths upload a `docker-tag` artifact containing `sha-<commit_sha>` for the exact
revision checked out by the build. E2E setup rejects an artifact for any other revision;
branch names and PR aliases remain convenient publishing tags but are never E2E inputs.
After pulling, `verify-image-revision.sh` checks the image's
`org.opencontainers.image.revision` label before Compose starts any services. The PR build
sets this label to the PR head SHA explicitly, because the event SHA identifies a merge commit.
All nodes in the multi-node stack use the same verified image.

Nginx waits for healthy application services. Before authenticating users or warming caches,
Playwright global setup polls `/management/health/readiness` on the configured base URL and
every direct core-node URL in `MULTI_NODE_URLS`. Each must report HTTP 200 and status `UP`
within a shared two-minute readiness budget; failure lists each unavailable URL and its last
probe result. This uses the readiness group so an unrelated external connector does not block
startup. JUnit reporting jobs receive `checks: write` from the umbrella and narrow that
permission to the jobs that create report check runs.

Regression tests for delayed readiness, unavailable nodes and image revision mismatches run
with `pnpm run test:rules` (or select `supporting_scripts/ci/E2E-tests/setup.spec.mjs`).

## Concurrency model

| Event | Group key | Cancel in progress? |
|---|---|---|
| `pull_request` | `ci-pr-{N}` | yes |
| `merge_group` | `ci-mq-{head_sha}` | no |
| `release` | `ci-release-{tag}` | no |
| `push` / `workflow_dispatch` | `ci-{github.ref}-{github.sha}` | no |

Concurrency lives only on the umbrella. Reusables share the umbrella's `run_id`, so the
parent's concurrency lock applies transitively. **Never** add a `concurrency:` block to a
`ci-*.yml` reusable — it creates a second lock that can deadlock the parent
([actions/runner#3205](https://github.com/actions/runner/issues/3205)).

Distinct push commits run independently so a long E2E phase cannot hold back the next commit's
WAR or image build. Their mutable publishing aliases can therefore move while an earlier run
is still active, which is why E2E consumes the exact commit tag and checks its revision.
GitHub keeps only one pending run per concurrency group; repeated runs for the same ref and
commit can still replace a pending run. Documentation builds on every `develop` push so any
replacement run deploys the complete documentation tree. Pull requests use `has_docs`.

The `deploy-docs` **job** in `ci.yml` carries a job-level
`concurrency: { group: pages, cancel-in-progress: false }`. Job-level concurrency is safe (it is
not the reusable-workflow lock that #3205 warns about), and the repo-global `pages` group
serializes every GitHub Pages deploy across umbrella runs and manual redeploys in
`deploy-documentation.yml`. `coverage-badge` has no job-level group. Badge and release metadata
updates can overlap; the shared publisher retries a non-force push after fetching and rebasing
over the other writer. A conflict keeps the published files and emits a warning.

## The coverage badge

The `coverage` badge in the root `README.md` is a self-hosted `coverage.svg` on the orphan
**`badges`** branch, rendered locally from our own `coverage.json` without a hosted badge API.
`ci.yml`'s `coverage-badge` job recomputes it on every `develop` push,
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

Every rejection is a no-op: the job exits 0 without committing, and GitHub keeps serving the last
good SVG. Nothing about the badge can turn `develop` red.

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

## The release and license badges

`publish-badges.yml` renders `latest-version.svg` and `license.svg` locally and publishes them to
`badges`. It runs on release publication, edits, deletion and unpublication, daily, and on manual
dispatch. The daily run repairs missed events and allows the three new SVGs to be bootstrapped
from the existing coverage JSON without another test run. GitHub serves the SVGs directly from
`raw.githubusercontent.com`; the README has no Shields dependency. No badge npm packages,
external fonts, or rendering services are involved.

The version comes from the highest stable numeric Artemis release returned by GitHub's paginated
releases API, accepting tags such as `10.3` and `v10.3.1`. Drafts, prereleases, scoped `@tumaet/*`
package releases and development version files are excluded. A late maintenance release therefore
cannot make the badge move backwards. The license is the repository's static MIT license. Source
or publishing failures preserve the previous SVGs and are advisory. Codacy, Sonar and OpenSSF
badges continue to come directly from their authoritative services.

The trusted tooling is checked out from the default branch even for a release event referencing
an older tag. Only the separate `badges` checkout retains write credentials. Both publishers use
`supporting_scripts/badges/publish-badges.mjs`; concurrent writers fetch and rebase before retrying
up to three non-force pushes. No commit lands on the development branch. Tests exercise release
selection, SVG escaping, failed inputs and real Git writer races, using Node's built-in test runner
in `ci-workflows.yml`.

After deployment, manually dispatch **Publish README Badges** once to create all three SVGs before
expecting the changed README URLs to resolve. If the orphan branch is absent, follow the
[re-seeding instructions](../../supporting_scripts/code-coverage/coverage-badge/README.md#re-seeding-the-badges-branch),
then dispatch the workflow. For local rendering and tests, see
[the badge tooling README](../../supporting_scripts/badges/README.md).

## The code quality analyses

Two external services grade the code, and neither one gates a merge. The enforcing checks are
`ci-quality.yml` and `ci-test.yml`; these two publish a figure.

**Codacy** analyzes every `develop` commit on its own infrastructure — nothing in this repo triggers
it. It serves the `code quality` badge in the root `README.md` from
`app.codacy.com/project/badge/Grade/…`, and the badge links to the repository dashboard. Its scope
comes from `.codacy.yaml`, read from the default branch. Note what that grade is and is not: the
active pattern set is essentially SAST, so an "A" means very few flagged findings per line — it does
not reflect Spotless, Checkstyle, Modernizer, the ArchUnit rules, the custom ESLint rules, or the
coverage floors, all of which are enforced here instead. Codacy's *coverage* figure is separately
stale, which is why the coverage badge is self-hosted (see above).

**SonarQube Cloud** runs from `ci-sonar.yml` on `develop` pushes, after a green `test`, importing the
`Server JaCoCo XML` and `Vitest Coverage Report` artifacts that job already uploaded — no suite is
re-run. It serves five badges in the root `README.md`, straight from `sonarcloud.io/api/project_badges`
for the `develop` branch: the **quality gate** (which judges the new code of the last 30 days: ratings,
coverage, duplication, hotspots), the **reliability**, **security** and **maintainability** ratings, and
the share of **duplicated lines**. Mind what the ratings measure. The badge service reports
whole-project values and has no badge for the new-code metrics, so the three rating badges grade every
open finding of the project, including the ones from before the new-code window, while the quality gate
keeps new code at A. The reliability badge therefore trails the gate until the older bugs are fixed. The
coverage badge stays the self-hosted one described above. Configuration lives in `gradle/sonar.gradle`.

Three things about it are worth knowing before changing it:

1. **Scope is a contract with `.codacy.yaml`.** `gradle/sonar.gradle`'s exclusion list mirrors that
   file's repository-wide `exclude_paths` floor. Change one without the other and the two grades
   quietly stop measuring the same code, which is the entire point of running both. The floor is the
   shared part and not the whole scope: Sonar narrows further to the roots in `sonar.sources`, while
   `.codacy.yaml` sets no `include_paths`, so Codacy also grades supported repository tooling outside
   them.
2. **`compileJava` is required, not an optimization.** Sonar's Java analyzer needs bytecode, and the
   Gradle plugin only sets `sonar.java.binaries` if the output directory already exists. Without a
   prior compile the property is silently omitted and the Java half of the analysis degrades with no
   error, so the job runs `./gradlew compileJava sonar -x webapp`.
3. **It is dispatchable on its own.** A dispatched run normally analyzes its selected ref. For
   recovery, enable **Analyze the repository default branch** (`analyze_default_branch=true`) to
   run the selected workflow revision against the actual default-branch source. The workflow records
   the checked-out SHA and explicitly publishes that branch/revision, so a workflow repair can
   refresh develop without labeling feature-branch application code as develop. This boolean is the
   only dispatch input; it cannot select an arbitrary source SHA or build argument.

   Coverage is imported only from a completed `ci.yml` run for that exact source SHA with both
   non-expired artifacts and successful client, server-report and all seven server-shard jobs.
   An unrelated style or E2E failure does not invalidate complete test coverage. Partial or failed
   tests, missing artifacts and another source revision never qualify. When no complete result is
   available the scanner logs a warning and analyzes without coverage, which can make the quality
   gate report missing new-code coverage.
4. **The scanner has its own heap.** Scanner 6+ forks a separate JVM, so `org.gradle.jvmargs` does
   not size it. The analysis step sets `SONAR_SCANNER_JAVA_OPTS=-Xmx6g` to keep the large Java/client
   scan within its Java heap limit on a GitHub-hosted runner. A scanner crash publishes no analysis;
   the README badges keep showing the last successful result. Verify the Sonar analysis revision,
   not just the GitHub run conclusion.

CI calls remain advisory and never appear in another job's `needs:`. Standalone manual runs fail
visibly on scanner errors, so a failed recovery cannot look like a successful badge refresh.

A completed scanner upload and the server-side quality gate are separate results. SonarQube Cloud's GitHub App posts a second check
of its own, `SonarCloud Code Analysis`, carrying the quality gate verdict. Nothing in this
repository produces that check and `continue-on-error` cannot reach it, so a failed gate shows as a
red X on the `develop` commit while the job beside it is green. It still gates nothing — branch
protection requires only `All required CI Passed` — but it is the reason `develop` can look red with
every workflow passing. Which conditions that verdict applies is the quality gate the project is
assigned in SonarQube Cloud, configured there rather than here.

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
