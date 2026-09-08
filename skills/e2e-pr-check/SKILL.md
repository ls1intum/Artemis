---
name: e2e-pr-check
description: Select, run, write, or debug Artemis Playwright E2E tests, including cluster-sensitive changes.
---

# Run the E2E tests this change affects

## Environment and test authoring

Before starting a runner, identify the current environment and its owner. The single-node runner
`./run-e2e-tests-local-fast.sh` can kill processes on ports 8080, 9000 and 7921. Reuse a suitable
running stack only after confirming it serves this branch; do not stop unrelated services.
Do not edit `src/main/webapp` during a run: HMR reloads the page Playwright is driving.

When writing locators, prefer `data-testid` and `page.getByTestId()`, not styling classes. Use IDs
only for existing production semantics. For third-party markup and exceptions, read
`documentation/docs/developer/e2e-testing-playwright.mdx` (Use uniquely identifiable locators).

## Step 1: work out which specs are affected

Use CI's resolver with the branch's actual base (the parent branch for a stack):

```bash
./.ci/E2E-tests/determine-relevant-tests.sh origin/develop
```

- `RUN_ALL_TESTS=true`: run the full suite, or report explicitly that narrower coverage is incomplete.
- `RELEVANT_TESTS`: space-separated paths relative to `src/test/playwright`, including always-run specs.
- `REMAINING_TESTS`: specs CI runs in its second phase.

The resolver uses committed changes only. Inspect staged, unstaged and untracked changes separately;
use full-suite coverage when their affected specs cannot be established. Do not create a commit
merely to select tests. A failed base comparison must be resolved before using the selection.

## Step 2: choose the runner

Use the single-node runner for changes without cluster-sensitive behavior.

```bash
./run-e2e-tests-local-fast.sh --specs "<RELEVANT_TESTS from step 1>"
```

Use the multi-node runner instead when the diff touches cluster-sensitive code, because a single
node cannot reproduce cross-node failures at all:

```bash
./run-e2e-tests-local-multinode-fast.sh --specs "<RELEVANT_TESTS from step 1>"
```

For image/container-sensitive failures or production-faithful CI reproduction, use
`./run-e2e-tests-local-multinode.sh` instead of the host-JVM fast variant.

Treat a change as cluster-sensitive when it touches any of:

- `src/main/java/de/tum/cit/aet/artemis/core/service/distributed/` or any caller of
  `DistributedDataProvider`
- `src/main/java/de/tum/cit/aet/artemis/core/config/cache/`
- the build job queue and dispatch in `src/main/java/de/tum/cit/aet/artemis/localci/`
- websocket broker or scheduling configuration

For distributed-data abstraction changes, test both Hazelcast and Redis: the Redis configuration
does not create a Hazelcast instance.

```bash
./run-e2e-tests-local-multinode-fast.sh --middleware redis --specs "<paths>"
```

## Step 3: re-runs

The runners keep services alive between runs. After the first run, reuse them:

```bash
./run-e2e-tests-local-fast.sh --skip-server --skip-client --skip-db --specs "<paths>"
```

For the multi-node runner the equivalent is `--skip-build --skip-up`.

Use `--stop` only for services this run owns and that are no longer needed. Leave a borrowed
stack running; see the wrong-client trap below.

## Step 4: interpret the result

- When reusing services, confirm they serve this checkout and include the changed code. Skip flags
  do not rebuild or reload Java changes.
- Compare a suspected pre-existing failure with the same spec on the base branch, in a separate
  environment or after finishing work against the current branch.
- Shared counters can change under parallel workers. Isolate test-owned state; where other workers
  can legitimately add to an aggregate count, assert the required lower bound rather than an exact delta.
- Investigate what a timed-out assertion is waiting for; do not increase timeouts to hide a race.
- `Error: No tests found.` can indicate an incorrect `--specs` path. Check selection and executed
  counts before interpreting a run as coverage of the change.

## Reporting back

State which specs ran, which passed, and which failed. If step 1 reported `RUN_ALL_TESTS=true` and
a subset was run anyway, say so plainly. Do not describe a change as E2E tested when the selection
was narrowed for time.
