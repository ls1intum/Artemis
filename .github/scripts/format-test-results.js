// Produce formatted markdown fragments for the E2E PR comment from raw test data.
// Called from actions/github-script; reads inputs from environment variables.
//
// Required env: INPUT_TEST_OUTCOME, INPUT_JOB_STATUS
// Optional env: INPUT_SUMMARY, INPUT_WALL_CLOCK, INPUT_FAILURES,
//               INPUT_PHASE_LABEL, INPUT_REPORTER_FAILED

module.exports = function (core) {
  const wallClock = process.env.INPUT_WALL_CLOCK || '';
  let summary = process.env.INPUT_SUMMARY || '';

  // The summary from mikepenz/action-junit-report@v6.3.0 is an HTML table where
  // the last <td> before </tr></table> is the cumulative test time across all
  // parallel worker threads. We replace it with the wall-clock duration since the
  // cumulative time overstates the real elapsed time when tests run in parallel.
  //
  // Example summary HTML:
  //   <table><tr><th>...</th><th>Time ⏱</th></tr>
  //   <tr><td>28 ran</td><td>28 passed</td>...<td>5m 33s 789ms</td></tr></table>
  //                                               ^^^^^^^^^^^^^^ replaced
  if (wallClock) {
    summary = summary.replace(/<td>[^<]*<\/td>(<\/tr><\/table>)/, `<td>${wallClock}</td>$1`);
  }

  const testOutcome = process.env.INPUT_TEST_OUTCOME || '';
  const jobStatus = process.env.INPUT_JOB_STATUS || '';
  const failures = process.env.INPUT_FAILURES || '';
  const phaseLabel = process.env.INPUT_PHASE_LABEL || '';
  const reporterFailed = process.env.INPUT_REPORTER_FAILED || '';

  const emoji = testOutcome === 'success' ? '\u2705' : '\u274C';
  const statusText = testOutcome === 'success' ? 'Passed' : 'Failed';
  const details = summary || (testOutcome === 'success' ? 'All tests passed' : 'Tests failed');

  let failuresSection = '';
  if (failures.trim()) {
    const label = phaseLabel ? `Failed Tests (${phaseLabel})` : 'Failed Tests';
    failuresSection = `\n\n<details><summary>\u274C ${label}</summary>\n\n`;
    for (const line of failures.trim().split('\n')) {
      failuresSection += `- \`${line}\`\n`;
    }
    failuresSection += '\n</details>\n';
  }

  let reporterNote = '';
  if (reporterFailed === 'true') {
    const phaseNote = phaseLabel ? ` in ${phaseLabel}` : '';
    reporterNote = `\n\n> **Note:** The test reporter (monocart) failed${phaseNote}. Test results were not affected, but coverage data may be incomplete.`;
  }

  let infraNote = '';
  if (testOutcome === 'success' && jobStatus !== 'success') {
    infraNote = '\n\n> \u26A0\uFE0F **Note:** Tests passed but the job encountered an infrastructure issue (e.g., artifact upload failure). See the workflow run linked below for details.';
  }

  core.setOutput('formatted-summary', summary);
  core.setOutput('failures-section', failuresSection);
  core.setOutput('reporter-note', reporterNote);
  core.setOutput('infra-note', infraNote);
  core.setOutput('status-emoji', emoji);
  core.setOutput('status-text', statusText);
  core.setOutput('details', details);
};
