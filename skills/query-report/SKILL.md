---
name: query-report
description: Maintain or extend the E2E query report (runtime N+1 and duplicate detector, static query rules, PR comment, HTML report, CI wiring), or find out why it is noisy, incomplete or missing.
---

# E2E query report

The query report tells a pull request which query anti-patterns it adds. During the E2E run the
server records the queries that one HTTP request repeats; a source scan finds risky query shapes;
CI compares both with the latest `develop` run and comments on what is new.

It is advisory. No report step may fail a build, and the detector only exists under the
`e2e-performance` Spring profile, which `deployment/docker/artemis/config/playwright.env` activates.

## Read the reference that matches the task

| Task                                                     | Read                           |
| -------------------------------------------------------- | ------------------------------ |
| Understand how the parts fit, or find the file to change | `reference/architecture.md`    |
| Add a finding type, a static rule, a report field        | `reference/recipes.md`         |
| The report is noisy, incomplete, empty or missing        | `reference/troubleshooting.md` |

## Rules that must keep holding

Breaking one of these usually passes every unit test and only shows up in later pull requests.

1. **A finding's identity must be the same in every run of the same code.** A finding is new when its
   key is not in the baseline. The key is built by `repeated_query_key` in
   `supporting_scripts/format_query_report.py` from the shape, the HTTP method and endpoint, the
   repository method, the calling method and the normalized SQL. Never let a random name, an id, a
   timestamp, a count, a line number or a test name into it. Endpoints are route templates; Git
   requests have none, so `QueryRequestFilter` replaces the project and repository names. Raw Git
   paths once made two runs of identical code differ by hundreds of findings.
2. **Changing the key format invalidates the baseline.** This includes SQL normalization, endpoint
   naming and `static_key`. Every affected finding appears as new until a `develop` run publishes new
   keys. Say so in the pull request.
3. **An incomplete run must not look like an improvement.** Findings dropped at the size limit,
   unsuccessful E2E tests or an unavailable static scan mean: no baseline keys, no inline comment
   file, existing inline comments kept, and a notice in the comment and the HTML report. A missing
   finding is only evidence of a fix when the comparison is complete.
4. **Inline comments are only the bot's own.** Anyone can quote the hidden marker.
   `.github/scripts/post-inline-query-comments.js` edits and deletes only comments of
   `github-actions[bot]`, only while the tested commit is still the head, and only the run of all
   tests removes stale ones.
5. **The detector must never break the request it observes.** `QueryListener` runs inside the
   application's JDBC call, and the filter clears the per-thread state even when a request fails.
6. **The server report and the formatter share field names without a compiler.** Change them together
   with `supporting_scripts/tests/fixtures/query-report.json`.
7. **Findings are yes or no, without thresholds.** How often a query repeats depends on the test data;
   whether it repeats does not.

## After a Spring upgrade

The detector hooks into the frameworks in four places. All are in
`src/main/java/de/tum/cit/aet/artemis/core/config/performance`.

| File                           | Depends on                                                         |
| ------------------------------ | ------------------------------------------------------------------ |
| `QueryDataSourceConfiguration` | A bean post processor on the `dataSource` bean, datasource-proxy   |
| `QueryListener`                | The datasource-proxy listener and Spring's `RequestContextHolder`  |
| `QueryRequestFilter`           | Servlet filter order and the `HandlerMapping` route attribute      |
| `QueryCallSite`                | Spring Data repositories being proxies that implement `Repository` |

Run the server tests below, then check a full E2E run. Unit tests that call these classes directly
still pass when Spring no longer calls them. The HTML report must list repeated queries with their
repository method and caller, and endpoints as route templates. A complete run without repeated
queries means the detector stopped working. For the upgrade itself see
[spring-upgrade](../spring-upgrade/SKILL.md).

## Verify a change

Run what covers the files you touched.

```bash
# server: detector, endpoint and the report contract
./gradlew test --tests "de.tum.cit.aet.artemis.core.config.performance.*" --tests "*AdminQueryReportResourceTest" -x webapp
# formatter, static rules and the CI shell script
python3 supporting_scripts/test_format_query_report.py
python3 supporting_scripts/test_find_slow_queries.py
python3 supporting_scripts/test_analyze_query_report.py
# inline comments and the multi-node merge
node --test .github/scripts/post-inline-query-comments.test.cjs supporting_scripts/tests/query-report-merge.test.mjs
```

For server changes also follow [server-arch-gates](../server-arch-gates/SKILL.md).

Render the report locally from the contract fixture to look at a layout change. The output files are
not ignored by Git, so delete them afterwards.

```bash
python3 supporting_scripts/find_slow_queries.py --json static-findings.json
python3 supporting_scripts/format_query_report.py supporting_scripts/tests/fixtures/query-report.json "" query-report.html static-findings.json
```

Unit tests cannot show that a real run is complete and stable. After a change to the detector or to
the key, check the full E2E run of the pull request: its comment must not say "Incomplete
comparison", and the HTML report must still list repeated queries with their calling code.
