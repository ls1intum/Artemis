# Client eager-chunk loading guard

Detects the class of regression fixed in PR #13027: a component statically importing another
component it *also* declares as an Angular lazy route (`loadComponent()`), which silently pulls
that component's entire dependency subtree into the eager (immediately-downloaded) chunk graph
instead of loading it on demand.

## How it works

1. Build with `statsJson: true` (an `@angular/build:application` option) to get an esbuild
   metafile — a module → chunk map where every dependency edge is tagged `import-statement`
   (static/eager) or `dynamic-import` (lazy).
2. `analyze_eager_chunks.mjs` computes, for a curated list of route-entry components, the
   **eager set**: everything reachable from that route's own chunk by following only static
   edges. That's exactly what downloads the instant the route's chunk loads, regardless of
   whether the user ever interacts further.
3. `diff_eager_chunks.mjs` compares a fresh eager-set report against a baseline report. This is a
   full delta report, not a pass/fail gate: **every** route is shown, classified `regressed` (⚠️),
   `improved` (✅), or `unchanged` (➖) based on the same threshold applied in both directions, with
   a chunk-level drill-down for both regressions (what newly became eager) and improvements (what's
   no longer eager) — not just the routes that got worse.

No browser or live server is needed — this is a pure build-time static analysis.

## Where the baseline comes from — fully automatic, no committed file

There is **no committed baseline file**. With as many PRs merging to `develop` daily as this repo
has, a manually-maintained snapshot goes stale fast and either false-positives on unrelated PRs or
silently stops catching anything — worse than not having the check at all.

Instead, `ci-build.yml`'s `build-war` job (which already runs the real production Angular build on
**every** PR and **every** push to `develop`) generates a report on every run and uploads it as a
workflow artifact (`chunk-eager-report`). The `chunk-eager-quality` job (PR runs only) then:

1. Downloads its own PR's report (same workflow run, no extra build).
2. Resolves `develop`'s latest commit via the GitHub API, then resolves *that commit's* CI run
   using the shared `.github/actions/resolve-artifact-run` composite action (the same one deploy
   paths use to find a `.war`/Docker artifact for an exact SHA).
3. Downloads that run's `chunk-eager-report` — this is the baseline, current as of `develop`'s
   last successful build. Usually that's the most recent push, but if one or more pushes in a row
   had a failed, cancelled, or skipped `build-war` job, `resolve-artifact-run` falls back to the
   nearest earlier run that actually published an artifact — so the baseline can occasionally be
   more than one push behind, not a strict "never more than one push stale" guarantee.
4. Diffs and posts/updates a PR comment.

No build runs twice: the PR side reuses `build-war`'s own build, and `develop`'s side is whatever
that same job already computed the last time it ran on `develop` — nothing is rebuilt just for the
comparison.

**Bootstrap note**: until this PR itself merges to `develop` and `develop` rebuilds once, there is
no `chunk-eager-report` artifact on any `develop` run yet. The `chunk-eager-quality` job handles
this gracefully — it skips the comparison (no PR comment) rather than failing. The mechanism
activates starting with the first PR opened after this one merges.

## Usage (manual / local)

```bash
# 1. Build with the stats flag
NG_BUILD_OPTIMIZE_CHUNKS=1 npx ng build --configuration production --stats-json

# 2. Generate a report for this build
node supporting_scripts/chunk-eager-loading/analyze_eager_chunks.mjs \
  build/resources/main/static/stats.json --out report.json

# 3. Diff against another report (e.g. one downloaded from a develop CI run, or a second local build)
node supporting_scripts/chunk-eager-loading/diff_eager_chunks.mjs report.json --baseline other-report.json
```

Exits with code 1 if any route is classified `regressed` (0 otherwise, regardless of improvements —
`ci-build.yml` currently ignores this exit code entirely; see Known limitations). Every report
embeds `meta: { sourceCommit, sourceBranch }` (from `GITHUB_SHA`/`GITHUB_REF_NAME` in CI, or
`git rev-parse` locally), since any report might later be used as someone else's baseline.

## Adding more routes

Extend the `ROUTE_ENTRIES` map at the top of `analyze_eager_chunks.mjs` with `routeName: sourceFilePath`.
Currently covers `course-overview` and `course-management-container` (the two components #13027 fixed).

## Known limitations

- **Advisory only, not build-blocking.** `chunk-eager-quality` never fails the CI run — it's a new,
  unproven check; see the plan discussion before making it gate merges.
- **Fork PRs may not get a posted comment.** The default `GITHUB_TOKEN` for `pull_request` events
  from forked repositories is read-only regardless of the `permissions:` block, so
  `createComment`/`updateComment` can silently no-op for fork-originated PRs (the report is still
  computed, just not posted). Internal PRs are unaffected. A fork-safe two-workflow split exists
  elsewhere in this repo for the coverage reporter (`pullrequest-coverage-reporter.yml`) if this
  ever needs the same treatment.
- **The drill-down lists** (new chunks for regressions, removed chunks for improvements) match
  chunks by their stable module inputs (not the content-hashed filename), but are still noisiest
  when comparing builds from commits far apart in history — unrelated dependency/vendor-chunk
  drift shows up as spurious entries on both sides. Not an issue for the real CI path (PR vs.
  `develop`'s latest, always close together); the aggregate eager chunk count / byte size numbers
  are the primary, unaffected signal either way.
