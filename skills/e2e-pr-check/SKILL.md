---
name: e2e-pr-check
description: Select, run, or debug Artemis Playwright tests for a branch or pull request.
---

# Run the E2E tests this change affects

Select affected specs before running Playwright. Use the result and the runner's topology to
classify failures.

## Step 1: work out which specs are affected

Use the CI resolver instead of selecting specs by inspection. Its output depends on the base
revision and committed diff:

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

- **It diffs commits, not the working tree.** The script runs `git diff --name-only <base>...HEAD`.
  Uncommitted changes are invisible; identify their affected specs separately. Do not commit only
  to make the selector work. If there are no committed changes, the resolver selects all tests.
- **Use the pull request's actual base.** The base is the first argument. `origin/develop` is only
  correct for a pull request that targets `develop`.

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
providers. Redis has to pass the same tests as Hazelcast, and with `--middleware redis` no Hazelcast
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
