# Coverage badge

Computes the combined server + client coverage figure shown by the `coverage` badge in the root
`README.md`, replacing the Codacy coverage badge that stopped reporting a value.

`ci.yml`'s `coverage-badge` job runs this on every `develop` push and publishes the result to
`coverage.json` on the orphan **`badges`** branch, which Shields renders via its endpoint API. The
full CI contract — triggers, permissions, and why the badge is sticky — is documented in
[`.github/workflows/README.md`](../../../.github/workflows/README.md#the-coverage-badge).

## What it measures

```
(server.covered + client.covered) / (server.total + client.total)
```

over **lines**, taken from the report-level `<counter type="LINE">` of the aggregated JaCoCo report
and from `total.lines` of the Vitest `coverage-summary.json`. Lines are the only metric both tools
emit natively. Summing the raw counts rather than averaging the two percentages means the figure is
weighted by codebase size.

The two metrics are not semantically identical — JaCoCo counts lines of compiled bytecode, Istanbul
counts instrumented TypeScript lines — so this is a deliberately rough combined figure, not an exact
one. E2E contributes nothing: `ci-e2e.yml` is black-box Playwright with no JaCoCo agent and no
instrumented client build, so it emits no coverage data.

## Running it locally

Both inputs are produced by the normal test tasks:

```bash
./gradlew test jacocoTestReport -x webapp   # build/reports/jacoco/aggregated/jacocoTestReport.xml
pnpm run vitest:coverage                    # build/test-results/vitest/coverage/coverage-summary.json
```

```bash
node supporting_scripts/code-coverage/coverage-badge/compute-coverage-badge.mjs \
  --jacoco build/reports/jacoco/aggregated/jacocoTestReport.xml \
  --vitest build/test-results/vitest/coverage/coverage-summary.json \
  --out /tmp/coverage.json
```

To reproduce exactly what CI would publish, add `--previous` (the currently published file) and
`--sha`. Without a full test run the numbers will be lower than CI's, since a partial run leaves
most lines uncovered.

You can also feed it the artifacts from a green `develop` run instead of running the suites:

```bash
gh run download <run-id> -n "Server JaCoCo XML" -n "Client Coverage Summaries" -D /tmp/cov
```

## Exit codes

| Code | Meaning                                                                            |
| ---- | ---------------------------------------------------------------------------------- |
| `0`  | A new value was computed and written to `--out`.                                   |
| `3`  | The value is unchanged — the routine no-op. CI logs a notice.                      |
| `4`  | A guard rejected the computed value; the reason is printed. CI logs a **warning**. |
| `1`  | Bad invocation.                                                                    |

Neither `3` nor `4` is a failure: both leave the published value standing, which is how the badge
stays stable across a flaky run. They are split because a repeating `4` is worth investigating while
a repeating `3` is just a quiet week.

The collapse guard **latches**: it compares against the last _published_ total, and that total only
advances when a value is published. A legitimate halving of the combined line count — a large module
deleted, or Vitest's `include` narrowed — therefore trips it on every subsequent develop push instead
of settling, and the badge freezes until someone re-seeds. The `::warning::` on exit `4` is what
makes that visible; the re-seed below is the fix.

## Re-seeding the `badges` branch

The CI job never creates the branch — if `badges` is deleted, the job warns and the badge breaks
until someone recreates it. Build the value from a green `develop` run, then push an orphan commit
(plumbing commands, so no branch switch and no working-tree churn):

```bash
gh run download <green-develop-run-id> -n "Server JaCoCo XML" -n "Client Coverage Summaries" -D /tmp/cov
node supporting_scripts/code-coverage/coverage-badge/compute-coverage-badge.mjs \
  --jacoco "/tmp/cov/Server JaCoCo XML/aggregated/jacocoTestReport.xml" \
  --vitest "/tmp/cov/Client Coverage Summaries/coverage-summary.json" \
  --out coverage.json --sha <commit-sha>

BLOB=$(git hash-object -w coverage.json)
TREE=$(printf '100644 blob %s\tcoverage.json\n' "$BLOB" | git mktree)
COMMIT=$(git commit-tree "$TREE" -m 'Seed the coverage badge')
git push origin "$COMMIT":refs/heads/badges
rm coverage.json
```

## Tests

`compute-coverage-badge.spec.mjs` covers the parsers and each guard. It runs in CI as part of the
client test job:

```bash
pnpm run test:rules
```
