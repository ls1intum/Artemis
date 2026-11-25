# Troubleshooting the query report

Start with the log of the "Query report" step and the HTML artifact of the E2E job.

| Symptom                                 | Likely cause and what to check                                                                                                                                                           |
| --------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| "Query Report Unavailable"              | No report file, or the formatter failed. Search the Playwright log for `[query-report]`: wrong profile, login failure, or one node that did not answer (a partial report is never saved) |
| "No develop baseline is available"      | No unexpired "Query Baseline" artifact from `develop`. Only the non-PR job on `develop` publishes it, and only for a complete run                                                        |
| "Incomplete comparison"                 | The text names the reasons. For the size limit, search the server log for `Circuit-breaker` and see below                                                                                |
| Many unexpected new findings            | A value in the key differs between runs, the key format changed, or the baseline is old. See below                                                                                       |
| One unexpected new finding              | Often timing: code that only repeats a query when two operations race. Compare with a second run                                                                                         |
| No new findings although expected       | No test requests the endpoint, the loop ran once in the test data, or the finding is already in the baseline                                                                             |
| A complete run with no repeated queries | Treat it as a broken detector, not as a clean codebase. Check that the proxy log line appears at server start. Nothing reports this on its own                                           |
| Inline comment missing                  | The line is not in the pull request's diff, the comparison is incomplete, or the head moved after the tested commit. The summary comment still lists the finding                         |

## Find the cause of noisy "new" findings

Compare the keys of two runs of the same code and list those that only one of them has. Pull request
runs upload neither their keys nor the report JSON, so get them in one of these ways:

- Locally: after each E2E run with the profile, run the formatter with `--keys-out` on the
  `query-report.json` that the global teardown writes to the Playwright `test-reports` folder, before
  the next run replaces it.
- In CI: on two temporary branches that are not meant to be merged, set `publish-baseline: 'true'` on
  the report step and download each "Query Baseline" artifact. Draft pull requests defer the E2E jobs.

Then read the differences:

- Differences that share a pattern in one part of the key, such as a generated name in the endpoint,
  are a defect: make that part stable.
- A handful of unrelated differences are timing and test data. Do not hide them by loosening the key
  for every finding.
- Static findings come from the source code, so they must be identical between two runs.

## The size limit is reached

Each finding costs server memory and report size, and nothing is reserved in advance.

1. Check first whether keys contain varying values, which inflate the number of distinct findings.
2. If the findings are genuine, raise the default in `QueryProperties` and its test. The server logs
   the warning once, so the number of dropped findings is not known from the log.
