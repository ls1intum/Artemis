---
name: e2e-pr-check
description: Select, run, or debug Artemis Playwright tests for a branch or pull request, or write a layout contract that measures element sizes and alignment.
---

# Run the E2E tests this change affects

Select affected specs before running Playwright. Use the result and the runner's topology to
classify failures.

## Step 1: work out which specs are affected

Use the CI resolver instead of selecting specs by inspection. Its output depends on the base
revision and committed diff:

```bash
./supporting_scripts/ci/E2E-tests/determine-relevant-tests.sh origin/develop
```

It prints five `OUTPUT:` lines. The ones that matter:

- `RUN_ALL_TESTS=true` requires the full suite. This includes configuration and Playwright
  infrastructure changes, application source changes, unmapped files, an invalid mapping, or an
  unavailable base comparison. Say why the selector chose a full
  run. Do not present a subset as sufficient coverage.
- When `RUN_ALL_TESTS=false`, `RELEVANT_TESTS` is the space-separated set of paths relative to
  `src/test/playwright`. In ordinary mode, only spec-only changes select changed specs and the always-run smoke
  specs (`e2e/Login.spec.ts`, `e2e/Logout.spec.ts`, `e2e/SystemHealth.spec.ts`).
- `REMAINING_TESTS` contains the rest of the suite, including for spec-only changes. Computing this
  set does not mean CI ran it. Ordinary eligible internal PRs run Phase 1 only and intentionally
  omit Phase 2. Full PR mode (`run_full_suite=true` on the reusable workflow) runs Phase 2 after
  Phase 1 passes its gate. Locally, use the selected scope only for spec-only changes. Application source
  always requires full coverage. Module mappings only prioritize a complete run; the workflow passes
  `E2E_FULL_SUITE=true` when its remaining phase is mandatory. Do not use that flag to narrow a local run.

Two things about the input:

- **It diffs commits, not the working tree.** The script compares `<base>...HEAD` from the merge
  base. Uncommitted changes are invisible; identify their affected specs separately. Do not commit
  only to make the selector work. No committed changes or a failed base comparison selects all
  tests. Renames classify both the old and new paths; deleted specs are omitted from test arguments.
- **Use the pull request's actual base.** The base is the first argument. `origin/develop` is only
  correct for a pull request that targets `develop`.

CI sets `run_full_suite=false` for ordinary internal PR runs. The selector can still require all
E2E tests, which run in one full-suite job for application source, helpers and unknown inputs. Existing
source consumers span multiple domains; the module mapping does not prove safe partial coverage.
Fork PRs do not start E2E; draft and test/documentation-only PRs can also skip it through the umbrella
workflow's change filters.

Non-PR E2E runs use the full suite: integration-branch pushes, release events, merge-group events
when a merge queue is configured, and manual CI runs. A green selective PR result does not prove a
full E2E run, and merge-group coverage is not guaranteed for every PR. To request full CI for a
branch explicitly:

```bash
gh workflow run ci.yml --ref <branch> -f force_build=true
```

The PR comment and job summary identify selected versus full scope. Report the scope actually
executed, including intentional Phase 2 omission.

## Step 2: choose the runner

Default to the single-node runner. It is faster and it is what most changes need. When step 1
requires the full suite, omit `--specs` from the chosen runner command.

```bash
./supporting_scripts/e2e/run-e2e-tests-local-fast.sh --specs "<RELEVANT_TESTS from step 1>"
```

Use the multi-node runner instead when the diff touches cluster-sensitive code, because a single
node cannot reproduce cross-node failures at all:

```bash
./supporting_scripts/e2e/run-e2e-tests-local-multinode-fast.sh --specs "<RELEVANT_TESTS from step 1>"
```

Treat a change as cluster-sensitive when it touches any of:

- `src/main/java/de/tum/cit/aet/artemis/core/service/distributed/` or any caller of
  `DistributedDataProvider`
- `src/main/java/de/tum/cit/aet/artemis/core/config/cache/`
- the build job queue and dispatch in `src/main/java/de/tum/cit/aet/artemis/localci/`
- websocket broker or scheduling configuration

If the change is specifically about the distributed data abstraction, run the suite on both
providers. Redis has to pass the same tests as Hazelcast, and with `--middleware redis` no Hazelcast
instance is created at all, which is what makes it a genuine test of the abstraction:

```bash
./supporting_scripts/e2e/run-e2e-tests-local-multinode-fast.sh --middleware redis --specs "<paths>"
```

## Step 3: re-runs

The runners keep services alive between runs. After the first run, reuse them:

```bash
./supporting_scripts/e2e/run-e2e-tests-local-fast.sh --skip-server --skip-client --skip-db --specs "<paths>"
```

For the multi-node runner the equivalent is `--skip-build --skip-up`.

The runners can stop services they did not start. Use `--stop` only after you have identified
the services and confirmed that the runner owns them. It also tears down its database.

## Step 4: interpret the result

A red spec is not automatically a bug in the change. Work through these before concluding anything.

**Does it already fail on develop?** Some specs fail only in develop's full-suite job, so a green
run on a pull request proves less than it looks and a red one may be pre-existing. Check the same
spec on develop before attributing the failure to the branch.

**Is the client under test actually this branch?** The runners reuse whatever is serving ports 9000
and 8080. A dev server left running from another branch will serve different code and the failure
will make no sense. If a failure looks impossible, verify what is actually being served before
debugging further.

**Is the assertion counting shared state?** Tests run across parallel workers against one server,
so anything asserting on a server-wide counter must assert a lower bound, not an exact value or an
exact delta. A single-worker local run hides this class of bug entirely, so a test that passes
locally and fails in CI with an off-by-a-few count is usually this.

**Does the test assert exact request counts for one navigation?** The page fixture enables navigation recovery by default and may reload a slow route. Set `test.use({ recoverNavigation: false })` for that contract and wait for the actual page content. This keeps automatic recovery from adding cancelled requests to the count; retain the exact request assertions and existing timeouts.

**Never fix a flake by raising a timeout.** Raising a timeout hides the race rather than fixing it,
and the test stays flaky in CI where the machine is slower and more loaded. Find what the test is
actually waiting for.

**Is the failure just a bad `--specs` path?** Paths are relative to `src/test/playwright`. A typo
makes Playwright print `Error: No tests found.` and exit non-zero, which the runner reports as a
failed run. So a red run with no test output at all is a path problem, not a test problem. Check
the executed count against what step 1 selected before reading anything else.

## Writing a layout contract

To pin how big an element is, how elements line up or that a page does not scroll sideways,
measure in the browser. Never compare screenshots, and never find an element by a styling class.

1. State the rule as a number or as a relation between elements.
2. Find the elements by `data-testid`; add one to the template when it is missing.
3. Assert inside `forEachViewport` from `src/test/playwright/support/layout.ts` with
   `expectHeight`, `expectAligned`, `expectFillsParent`, `expectInside` (with a `margin` for air),
   `expectInset` (name the scroll containers as `scrollers`, so that their scrollbars are not
   counted as inset), `expectGap` (the divider between two panels), `expectBelow`, `expectNoOverlap`,
   `expectInsideOrBelow` (a layout that depends on the room), `expectWithinViewport`,
   `expectNoHorizontalOverflow`, `expectNoHorizontalScrollAround` or `expectSameComputedStyle`, and
   give each element a `name`. A page in a card that scrolls on its own needs both sideways-scroll
   helpers, because the document keeps the width of the window. A click that must not move the page
   runs inside `expectScrollPositionKept`. A divider that is dragged keeps the width it was given in
   px, which a smaller window does not undo, so drag it after the viewport matrix.
4. For a new or changed helper, add a case that holds and one that must fail to
   `src/test/playwright/e2e/shared/LayoutHelpers.spec.ts`.

A change under `src/test/playwright/support/` makes the resolver in step 1 report
`RUN_ALL_TESTS=true`. The helpers, the viewport matrix and the exam example are in the section
"Layout contract tests" of `documentation/docs/developer/e2e-testing-playwright.mdx`.

## Reporting back

State which specs ran, which passed, and which failed. If step 1 reported `RUN_ALL_TESTS=true` and
a subset was run anyway, say so plainly. Do not describe a change as E2E tested when the selection
was narrowed for time.
