---
name: e2e-pr-check
description: Run the Artemis Playwright E2E tests that this branch's changes actually affect, and interpret the result correctly. Use when asked to E2E test a branch or pull request, to check a change end to end before pushing, or to investigate a failing Playwright spec. Covers selecting the affected specs, choosing between the single-node and multi-node runner, and the failure modes that look like real bugs but are not.
---

# Run the E2E tests this change affects

The full Playwright suite is over 400 tests across roughly 90 spec files, and takes tens of
minutes. Almost no change needs all of them. This skill selects the specs the change actually affects, runs them, and then reads
the result with the failure patterns of this suite in mind.

## Step 1: work out which specs are affected

Do not guess and do not hand-read `.ci/E2E-tests/e2e-test-mapping.json`. Run the same resolver CI
uses, so local selection and CI selection can never disagree:

```bash
./.ci/E2E-tests/determine-relevant-tests.sh origin/develop
```

It prints five `OUTPUT:` lines. The ones that matter:

- `RUN_ALL_TESTS=true` means the change hit `runAllTestsPatterns` (Spring config, `docker/`,
  `build.gradle`, `angular.json`) or touched Playwright infrastructure outside `e2e/`. Say so
  explicitly rather than quietly running a subset. Then either run the full suite or agree with the
  user on a narrower scope, but do not present a subset as sufficient coverage.
- `RELEVANT_TESTS` is the space-separated list of spec paths to run, relative to
  `src/test/playwright`. It always includes the always-run specs (`e2e/Login.spec.ts`,
  `e2e/Logout.spec.ts`, `e2e/SystemHealth.spec.ts`).
- `REMAINING_TESTS` is everything else. CI runs it as phase 2 only after phase 1 passes. Locally it
  is normally not worth running.

Two things about the input:

- **It diffs commits, not the working tree.** The script runs `git diff --name-only <base>...HEAD`,
  so uncommitted changes are invisible to it. **Commit before resolving.** With nothing committed at
  all it says "No changed files detected. Running all tests.", which is loud and harmless. The
  dangerous case is quieter: committed work plus uncommitted edits touching a further module gives
  a selection based only on the committed files, so the specs covering your newest edits are the
  ones left out.
- **Pass a different base for a stacked branch.** The base is the first argument. A stacked pull
  request is not cut from develop, so diffing against develop selects its parent's changes too.

## Step 2: choose the runner

Default to the single-node runner. It is faster and it is what most changes need.

```bash
./run-e2e-tests-local-fast.sh --specs "<RELEVANT_TESTS from step 1>"
```

Use the multi-node runner instead when the diff touches cluster-sensitive code, because a single
node cannot reproduce cross-node failures at all:

```bash
./run-e2e-tests-local-multinode-fast.sh --specs "<RELEVANT_TESTS from step 1>"
```

Treat a change as cluster-sensitive when it touches any of:

- `src/main/java/de/tum/cit/aet/artemis/core/service/distributed/` or any caller of
  `DistributedDataProvider`
- `src/main/java/de/tum/cit/aet/artemis/core/config/cache/`
- the build job queue and dispatch in `src/main/java/de/tum/cit/aet/artemis/localci/`
- websocket broker or scheduling configuration

If the change is specifically about the distributed data abstraction, run the suite on both
backends. Redis has to pass the same tests as Hazelcast, and with `--middleware redis` no Hazelcast
instance is created at all, which is what makes it a genuine test of the abstraction:

```bash
./run-e2e-tests-local-multinode-fast.sh --middleware redis --specs "<paths>"
```

## Step 3: re-runs

The runners keep services alive between runs. After the first run, reuse them:

```bash
./run-e2e-tests-local-fast.sh --skip-server --skip-client --skip-db --specs "<paths>"
```

For the multi-node runner the equivalent is `--skip-build --skip-up`.

Tear down with `--stop` when finished. Leaving services running is fine and normal during
iteration, but see the wrong-client trap below.

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

**Never fix a flake by raising a timeout.** Raising a timeout hides the race rather than fixing it,
and the test stays flaky in CI where the machine is slower and more loaded. Find what the test is
actually waiting for.

**Is the failure just a bad `--specs` path?** Paths are relative to `src/test/playwright`. A typo
makes Playwright print `Error: No tests found.` and exit non-zero, which the runner reports as a
failed run. So a red run with no test output at all is a path problem, not a test problem. Check
the executed count against what step 1 selected before reading anything else.

## Reporting back

State which specs ran, which passed, and which failed. If step 1 reported `RUN_ALL_TESTS=true` and
a subset was run anyway, say so plainly. Do not describe a change as E2E tested when the selection
was narrowed for time.
