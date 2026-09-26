# Client eager-chunk loading guard

Detects the class of regression fixed in PR #13027: a component statically importing another
component it _also_ declares as an Angular lazy route (`loadComponent()`), which silently pulls
that component's entire dependency subtree into the eager (immediately-downloaded) chunk graph
instead of loading it on demand.

## How it works

1. Build with `statsJson: true` (an `@angular/build:application` option) to get an esbuild
   metafile — a module → chunk map where every dependency edge is tagged `import-statement`
   (static/eager), `dynamic-import` (lazy), or `url-token` (asset reference).
2. `discover_route_entries.mjs` scans every `*.route.ts` file under `src/main/webapp/app` and
   extracts every `loadComponent: () => import(...)` target — i.e. every route Angular itself
   lazy-loads — deduped by source file (the same detail component reused across several routes
   only counts once). No hand-maintained list: a new lazy route is picked up automatically the
   next time this runs, currently around 177 routes.
3. `analyze_eager_chunks.mjs` computes, for each discovered route component, the **eager set**:
   everything reachable from that route's own chunk by following only static edges. That's exactly
   what downloads the instant the route's chunk loads, regardless of whether the user ever
   interacts further.
4. `diff_eager_chunks.mjs` compares a fresh eager-set report against a baseline report. This is a
   full delta report, not a pass/fail gate: **every** route is classified `regressed` (⚠️),
   `improved` (✅), or `unchanged` (➖) based on the same threshold applied in both directions, with
   a chunk-level drill-down for both regressions (what newly became eager) and improvements (what's
   no longer eager). A long list of new chunks is usually one static import's dependency tree, so
   the drill-down leads with the chunk(s) that caused it (e.g. `course-overview` →
   `course-exercises.component.ts` — 83 chunks, 2.8 MB including its dependencies) and folds the
   full size-sorted list away. Each chunk names its largest source modules (npm package for vendor
   code, file name for app code) since the content-hashed filename alone says nothing about why it
   is there. Reports without import edges (an older baseline) fall back to the plain list. Only non-`unchanged` routes get a table row — with ~177 routes, tabulating
   every unchanged one would bury the ones that actually need a look; `unchanged` routes are still
   counted in the summary line, just not listed individually.

No browser or live server is needed — this is a pure build-time static analysis.

## Where the baseline comes from — fully automatic, no committed file

There is **no committed baseline file**. With as many PRs merging to `develop` daily as this repo
has, a manually-maintained snapshot goes stale fast and either false-positives on unrelated PRs or
silently stops catching anything — worse than not having the check at all.

Instead, `ci-build.yml`'s `build-war` job (which already runs the real production Angular build on
**every** PR and **every** push to `develop`) generates a report on every run and uploads it as a
workflow artifact (`chunk-eager-report`). The `chunk-eager-quality` job (PR runs only) then:

1. Downloads its own PR's report (same workflow run, no extra build).
2. Resolves `develop`'s latest commit via the GitHub API, then resolves _that commit's_ CI run
   using the shared `.github/actions/resolve-artifact-run` composite action (the same one deploy
   paths use to find a `.war`/Docker artifact for an exact SHA).
3. Downloads that run's `chunk-eager-report` — this is the baseline, always built from `develop`'s
   exact latest commit. `resolve-artifact-run` only looks at runs of that one commit (a commit can
   have several, e.g. a re-run) and picks the first that still carries a non-expired artifact; if
   none does, the comparison is skipped (see Known limitations).
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

## How routes are named, and what's excluded

A route's key is its component's filename minus `.component.ts` (e.g. `course-overview.component.ts`
→ `course-overview`), matching the names used since this guard's first version. Two unrelated
components that happen to share a filename are disambiguated with their parent directory name;
run `node discover_route_entries.mjs` on its own to print the current `{ slug: sourcePath }` map.

Only `loadComponent` targets are discovered, not `loadChildren` (a handful of routes lazy-load a
child _routes_ module rather than a single component) — that's a different chunk shape than the
"one component, one chunk" model the rest of this guard's analysis assumes, and out of scope for
the bug class this guards against (see `discover_route_entries.mjs`'s own header comment).

## Known limitations

- **Advisory only, not build-blocking.** `chunk-eager-quality` never fails the CI run — it's a new,
  unproven check; see the plan discussion before making it gate merges.
- **The baseline must exist for `develop`'s exact latest commit.** The comparison resolves
  `develop`'s tip SHA and needs a `chunk-eager-report` artifact on that commit's `ci.yml` run. If
  that commit's build has not published one yet (still running, failed, or expired), the comparison
  is skipped for that run and no comment is posted — it does not fall back to an older `develop`
  report.
- **Fork PRs may not get a posted comment.** The default `GITHUB_TOKEN` for `pull_request` events
  from forked repositories is read-only regardless of the `permissions:` block, so
  `createComment`/`updateComment` can silently no-op for fork-originated PRs (the report is still
  computed, just not posted). Internal PRs are unaffected. A fork-safe two-workflow split exists
  elsewhere in this repo for the coverage reporter (`pullrequest-coverage-reporter.yml`) if this
  ever needs the same treatment.
- **The "pulled in by" attribution is a heuristic.** esbuild lists every chunk a chunk needs, not
  only the immediate imports, so the report cannot read the causing import edge directly. It picks
  the changed chunk that needs the most other changed chunks, then repeats for what is left. That
  matches the real regression it was checked against, but a chunk that merges an unrelated module
  with the culprit is labelled by its largest module, which may be the neighbour. The full chunk
  list stays available below it.
- **The drill-down lists** (new chunks for regressions, removed chunks for improvements) match
  chunks by their stable module inputs (not the content-hashed filename), but are still noisiest
  when comparing builds from commits far apart in history — unrelated dependency/vendor-chunk
  drift shows up as spurious entries on both sides. Usually close together on the real CI path
  (PR vs. `develop`'s latest), but a long-lived PR can still drift far from `develop`; the
  aggregate eager chunk count / byte size numbers are the primary, unaffected signal either way.
