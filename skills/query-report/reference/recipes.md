# Extending the query report

Read the rules in `../SKILL.md` first. File responsibilities are in `architecture.md`.

## Add a static rule

1. Add the check to `supporting_scripts/find_slow_queries.py`. The rule applies or it does not; add no
   threshold.
2. Append findings as a dictionary with a literal `"type"` string. A test reads these literals from
   the source, so a type built at runtime goes unnoticed.
3. Give each finding `key`, `file`, `line`, `member` and `detail`, plus `repositoryMethod` if it names
   a repository call. The key is `rule:Class.member` or similarly stable: no line numbers. Without a
   `line` there is no inline comment.
4. Add the rule's words to `RULE_LABELS`. The formatter imports it.
5. Add tests to `supporting_scripts/test_find_slow_queries.py`.
6. Do not print new findings with the prefixes `[EntityGraph] Potential over-fetch` or
   `[@Query] Potential over-fetch`: `.github/workflows/ci-quality.yml` counts those lines and fails the
   build above its limit.

A new rule marks all its existing matches as new until `develop` publishes a baseline that has them.

Only `repository_call_in_loop` confirms a runtime finding, matched on caller and repository method.
Add another rule to `link_static_confirmations` only if it also describes one query issued per item.

## Add a shape of repeated query

1. Add the value to `RepeatedQueryFinding.Type` and derive it in
   `QueryCollector.addRepeatedQueryFindings`. Document the condition on the record.
2. In `supporting_scripts/format_query_report.py`, update `avoidable_executions`, `summarize_problem`,
   `CATEGORY_LABELS`, `query_counts_label` and both "How to read this" texts (comment and HTML).
3. Decide whether it has one line of code to comment on; `build_inline_comments` lists the categories
   that do.

## Add a different kind of runtime finding

For something that is not a repeated query, such as a property of a single statement:

1. Record it in `QueryCollector` with its own limit handling, so that dropping one sets `truncated`.
2. Add a record and a list to `QueryReportDTO`.
3. Merge the new list in `src/test/playwright/init/mergeQueryReports.ts`. It takes every field it does
   not combine from the first node, so a forgotten list silently loses the other nodes' findings.
4. In the formatter, give each finding a stable key, add it to `collect_keys` and `mark_new`, and
   render it in the comment and the HTML page.
5. Cover it in the contract fixture and both contract tests.

Prefer a yes or no condition. A duration measured in E2E depends on the machine and is not comparable
between runs.

## Add or rename a report field

1. Change `RepeatedQueryFinding`, `QueryReportDTO` or `TableIndex`.
2. Update `supporting_scripts/tests/fixtures/query-report.json`.
3. Update the formatter. `QueryReportContractTest` checks the server against the fixture and
   `ServerReportContractTest` checks the formatter against it.

Empty fields are omitted from the JSON, so read them with a default. Add the field to a key only if it
is the same in every run.

## Change the comment or the HTML page

- The comment lists new findings only and is capped by `MAX_ROWS_PER_SECTION`; GitHub limits a comment
  to 65,536 characters. CI reads it from a file, because a large environment variable once broke the
  step.
- The page is one self-contained file with inline CSS and JavaScript, so that it works when only the
  HTML is downloaded. Escape everything that comes from the report.
- Render it locally as described in `../SKILL.md` and check both light and dark mode.

## Change the CI wiring

- Both composite actions are used by several jobs in `.github/workflows/ci-e2e.yml`; change the action,
  not each job.
- Keep report steps non-fatal and conditional on `!cancelled()`, so that a failed test run still
  reports.
- Self-hosted runners keep their workspace, so remove an output before producing it.
- A new test file or input file must be added to the Java path filter in `.github/workflows/ci.yml` and
  to the step that runs it in `.github/workflows/ci-quality.yml` or `.github/workflows/ci-workflows.yml`.

## Add or rename an endpoint

A new endpoint changes the feature usage catalogue; run `FeatureUsageAnnotationTest` with
`-DupdateFeatureUsageCatalogue=true` after rebasing on `develop` instead of editing the page by hand.
