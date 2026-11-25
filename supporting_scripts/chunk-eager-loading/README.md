# Client eager-chunk loading guard

Detects the class of regression fixed in PR #13027: a component statically importing another
component it _also_ declares as an Angular lazy route (`loadComponent()`), which silently pulls
that component's entire dependency subtree into the eager (immediately-downloaded) chunk graph
instead of loading it on demand.

## How it works

1. Build with `statsJson: true` (an `@angular/build:application` option) to get the browser
   bundle's esbuild metafile, `browser-stats.json` — a module → chunk map where every dependency edge is tagged `import-statement`
   (static/eager), `dynamic-import` (lazy), or `url-token` (asset reference).
2. `discover_route_entries.mjs` scans every route config file (`*.route.ts`, `*.routes.ts`,
   `*-routes.ts`) under `src/main/webapp/app` and extracts every `loadComponent: () => import(...)`
   target — i.e. every route Angular itself lazy-loads — deduped by source file (the same detail component reused across several routes
   only counts once). No hand-maintained list: a new lazy route is picked up automatically the
   next time this runs, currently around 215 routes.
3. `analyze_eager_chunks.mjs` computes, for each discovered route component, the **eager set**:
   everything reachable from that route's own chunk by following only static edges. That's exactly
   what downloads the instant the route's chunk loads, regardless of whether the user ever
   interacts further. The report stores every chunk once, with its size, its static imports and
   every module in it with the bytes that module contributes (a module is an npm package, summed
   over all its files, or one of the project's own files); a route just names its chunks.
4. `diff_eager_chunks.mjs` compares a fresh report against a baseline report. This is a full
   delta report, not a pass/fail gate: **every** route is classified `regressed` (⚠️), `improved`
   (✅) or `unchanged` (➖) based on the same threshold applied in both directions, and the same
   order is used everywhere: regressions (largest growth first), then improvements (largest saving
   first), then skipped routes. A long list of new chunks is usually one static import's dependency
   tree, so for each changed route the report names the **imports** that caused the change, each with
   its size and the modules it brings along. Only changed routes get a table row — with ~215 routes,
   tabulating every unchanged one would bury the ones that actually need a look.

    It writes two documents:

    - **The PR comment** (`--out`): a short legend and a collapsed "How to read the table" before the
      table; one row per changed route with a collapsed line under it that names the imports ("imports
      `a` and `b`"); opened, each import with its size and its five largest modules; a collapsed
      "How to fix a regression" after the table. The route table is an HTML table inside markdown,
      because a pipe table cannot hold a fold or a cell that spans columns. GitHub keeps tables,
      `<details>`, `<pre>`, `colspan` and `align` but strips `style` and `class`, and stripes and
      borders every table row, so the open folds are text lines in a monospace block: the hierarchy
      is carried by characters (↳ and indentation) and the sizes are padded to one column.
    - **The full report** (`--full-out`): a standalone HTML page for people, the same layout with
      nothing cut. Per route a table with the route row and the imports fold (every module, one row
      each), a fold with all chunks (file name, size, every module), and at the end the unchanged
      routes. It carries its own style, so it looks the same in any browser, light or dark.

No browser or live server is needed — this is a pure build-time static analysis.

## Where the baseline comes from — fully automatic, no committed file

There is **no committed baseline file**. With as many PRs merging to `develop` daily as this repo
has, a manually-maintained snapshot goes stale fast and either false-positives on unrelated PRs or
silently stops catching anything — worse than not having the check at all.

Instead, `ci-build.yml`'s `build-war` job (which already runs the real production Angular build on
**every** PR and **every** push to `develop`) generates a report on every run and uploads it as a
workflow artifact (`chunk-eager-report`). The `chunk-eager-quality` job (PR runs only) then:

1. Downloads its own PR's report (same workflow run, no extra build).
2. Resolves the **merge-base** of the PR head and `develop` via the GitHub compare API — the commit
   the branch forked from, or the last `develop` commit merged into it — then resolves _that
   commit's_ CI run using the shared `.github/actions/resolve-artifact-run` composite action (the
   same one deploy paths use to find a `.war`/Docker artifact for an exact SHA). The merge-base is
   used instead of `develop`'s tip on purpose: against the tip, every client change merged to
   `develop` since the PR branched would show up as a regression or improvement of the PR.
3. Downloads that run's `chunk-eager-report` — this is the baseline. `resolve-artifact-run` only
   looks at runs of that one commit (a commit can have several, e.g. a re-run) and picks the first
   that still carries a non-expired artifact; if none does, the comparison is skipped (see Known
   limitations).
4. Diffs and posts/updates a PR comment. GitHub rejects comments over 65,536 characters, so when
   many routes change at once (e.g. a shared dependency) the comment is cut to 60,000: the summary
   and legend always stay, then routes follow with their fold while they fit, later routes as a bare
   row, and the rest are named in a note. Only a comment posted by `github-actions[bot]` with the report
   marker is updated; all of the PR's comments are searched for it. When no baseline can be resolved
   for a later push, an existing report comment is replaced by a short "comparison skipped" note, so
   a report for an older commit is not left standing; no comment is created just to say that.
5. Uploads the full report as the artifact `chunk-eager-diff-report` (kept 14 days), which holds
   `full-report.html`. Every comment links to it in its last line. GitHub serves artifacts as zips,
   so reading the full report means downloading it; the comment is the quick view. The report has no
   size limit to speak of, so a huge pull request does not produce an unreadable page in the run.
   The raw reports stay where they always were, as the `chunk-eager-report` artifact of each run.

No build runs twice: the PR side reuses `build-war`'s own build, and the baseline side is whatever
that same job already computed when it ran on that `develop` commit — nothing is rebuilt just for
the comparison. A PR's own report is kept for 14 days, a `develop` report for 90 (the artifact is
the baseline for every PR forked from that commit). Chunk identities (`moduleKey`) are stored as
SHA-1 hashes of the chunk's input paths, and each chunk is stored once however many routes load it,
which keeps the report around 1 MB. The report carries a `formatVersion`; the diff refuses a
report in another layout instead of misreading it.

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
  build/resources/main/static/browser-stats.json --out report.json

# 3. Diff against another report (e.g. one downloaded from a develop CI run, or a second local build)
node supporting_scripts/chunk-eager-loading/diff_eager_chunks.mjs report.json --baseline other-report.json
```

The diff prints a one-line summary and the changed routes. `--out <file>` writes the PR comment
(at most `--max-chars`, default 60,000 characters), `--full-out <file>` the full report as an HTML
page.

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

- **Advisory only, not build-blocking.** `chunk-eager-quality` is `continue-on-error` as a whole, so
  no failure in it (an API error, a missing artifact) fails the CI run — it's a new, unproven
  check; see the plan discussion before making it gate merges.
- **The baseline must exist for the merge-base commit.** The comparison needs a
  `chunk-eager-report` artifact on that commit's `ci.yml` run. If that commit's build has not
  published one (it never ran on `develop`, failed, or the artifact expired after 90 days), the
  comparison is skipped for that run — it does not fall back to another `develop` report, which
  would reintroduce the drift described above.
- **Sizes are those of an unoptimized build.** `build-war` builds with `--optimization=false`, so
  the byte figures are not minified and are larger than what a user downloads. They are only
  meaningful relative to another report from the same build.
- **Fork PRs may not get a posted comment.** The default `GITHUB_TOKEN` for `pull_request` events
  from forked repositories is read-only regardless of the `permissions:` block, so
  `createComment`/`updateComment` can silently no-op for fork-originated PRs (the report is still
  computed, just not posted). Internal PRs are unaffected. A fork-safe two-workflow split exists
  elsewhere in this repo for the coverage reporter (`pullrequest-coverage-reporter.yml`) if this
  ever needs the same treatment.
- **The "imports" attribution is a heuristic.** esbuild lists every chunk a chunk needs, not
  only the immediate imports, so the report cannot read the causing import edge directly. It picks
  the changed chunk that needs the most other changed chunks, then repeats for what is left, and
  names the import after the largest project file in that chunk. That matches the real regressions it
  was checked against, but a chunk that merges an unrelated module with the culprit may be named
  after the neighbour. Chunks shared by two imports are listed under the one that covers most. The
  full report lists every chunk, so nothing is hidden.
- **A module row is a group.** A module is an npm package or one project file, as the build sees it.
  A package that the build splits over several chunks is summed over them within an import.
- **Colour and styling are not available.** GitHub strips `style` and `class` from comments and
  step summaries. The tree is a monospace block at least 84 characters wide, so it gets no stripes;
  on a narrow screen it scrolls sideways inside its fold. GitHub also shades every second row of the
  route table itself, so a fold row is shaded and a route row is not.
