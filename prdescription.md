### Summary

Fix multiple flaky server tests caused by shared mutable state, race conditions, and timing-dependent initialization. Each fix improves test isolation so results are deterministic regardless of execution order or parallelism.

Also adjust the GitHub Action for server tests to make failing test results easier to understand, and improve error handling for repository file reading endpoints.

### Checklist
#### General
- [x] This is a small issue that I tested locally
- [x] I chose a title conforming to the [naming conventions for pull requests](https://docs.artemis.tum.de/developer/development-process#pr-naming-conventions).


#### Server
- [x] I documented the Java code using JavaDoc style.

#### Client
- [x] I added multiple translations for those labels (at least English and German).


### Motivation and Context

I do currently have quite some open PRs and spend too much time on reviewing the consistently failing server tests on my own PRs and when reviewing PRs of other developers.
This PR fixes some flaky tests and improves the GitHub Action server test results.

Several server-side integration tests fail occasionally on CI due to:
- **Shared metrics state**: `MeterRegistry` accumulates values across tests, breaking absolute-value assertions.
- **File system race conditions**: Tests move a shared file, causing conflicts during parallel execution.
- **Database interference**: `deleteAll()` removes data belonging to other parallel test classes.
- **Premature await conditions**: Build agent readiness checks pass before agents are fully initialized.
- **Stale session baselines**: SSH session counts are captured before setup completes, skewing assertions.
- **CI false positives**: A `grep` pattern in the test workflow matches a parameterized test enum value (`FAILED`), falsely reporting test failures.
- **Gradle cache corruption**: Parallel template integration tests share the same Gradle user home, corrupting transform caches.
- **Unhelpful CI output**: Coverage threshold violations are not surfaced clearly, and stderr is not captured in test logs.


### Description

#### Fixed Flaky Tests

**1. `MetricsBeanTest.testPrometheusMetricsExams()`**
- **Problem:** Asserted absolute metric values (e.g. `assertMetricEquals(2, ...)`) but the shared `MeterRegistry` accumulates values from other tests that run before this one.
- **Fix:** Capture baseline metric values _before_ creating test data and assert against the delta (`baseline + expected`). A new `getMetricValue()` helper safely reads the current gauge value, defaulting to `0.0` if the metric doesn't exist yet.

**2. `DataExportResourceIntegrationTest` — File operation race condition**
- **Problem:** `prepareDataExportForDownload()` used `FileUtils.moveFile()` on a shared source file, so parallel tests would fail when the file was already moved. Timestamp-based naming wasn't granular enough to prevent collisions.
- **Fix:** Use `FileUtils.copyFile()` to a UUID-named destination (`data-export-{UUID}.zip`) so each test gets its own independent copy.

**3. `DataExportResourceIntegrationTest` — Database isolation**
- **Problem:** `dataExportRepository.deleteAll()` in setup would delete data exports belonging to other test classes running in parallel.
- **Fix:** A new `cleanupTestUserDataExports()` method filters and deletes only exports belonging to this test's specific users (`dataexport-student1`, `dataexport-student2`).

**4. `DataExportResourceIntegrationTest` — Resource cleanup**
- **Problem:** The old `restoreTestDataInitState()` (which moved the file back) could race with other tests and would not execute if the test failed.
- **Fix:** Track all created files in a list and clean them up in `@AfterEach` with `Files.deleteIfExists()`, which is resilient to test failures.

**5. `LocalCIResourceIntegrationTest.testPauseAllBuildAgents()`**
- **Problem:** The `await()` condition only checked `!buildAgentInformation.values().isEmpty()`, which returns `true` as soon as an agent object is added to the map — but the agent may still have a `null` or transitional status. Agent registration after `init()` is asynchronous and happens in stages: (1) agent added to map → `isEmpty()` becomes false, (2) status may be `null`/transitional, (3) status updates to `IDLE`/`ACTIVE`. Subsequent pause/resume operations fail on agents still at stage 2.
- **Fix:** Extend the condition to also verify all agents have reached `IDLE` or `ACTIVE` status, matching the pattern already used by the passing `testPauseBuildAgent()` test. Timeout increased from 10 s → 30 s for slower CI environments.

**6. `LocalVCSshIntegrationTest.clientConnectToArtemisSshServer()`**
- **Problem:** The baseline session count was captured _before_ participation creation and key pair setup. These setup steps could create additional sessions, causing the `baselineSessionCount + 1` assertion to fail.
- **Fix:** Capture the baseline session count _after_ all setup but _before_ opening the SSH client connection, so the assertion correctly validates exactly one new session.

**7. CI workflow false positives ([.github/workflows/test.yml](file:///Users/florianglombik/Anitgravity/Artemis/Artemis/.github/workflows/test.yml))**
- **Problem:** `DataExportResourceIntegrationTest` has a parameterized test with `DataExportState.FAILED` as an enum value. The `grep "Test > .* FAILED$"` pattern matched the log line for this variant and incorrectly reported a test failure.
- **Fix:** Add a `grep -v` filter to exclude lines matching `Starting logs for .* > .*state = FAILED$`.

**8. `LocalCIServiceTest.testReturnCorrectBuildStatus()`**
- **Problem:** The setUp calls `removeListenerAndCancelScheduledFuture()` which cancels the periodic `checkAvailabilityAndProcessNextBuild` task with `cancel(false)` (allowing an in-flight execution to complete). It then set `isPaused=false`. If the in-flight task ran after the test added an item to the queue, it would dequeue the item (since `isPaused` was `false`), causing the assertion to see `BUILDING` instead of `QUEUED`.
- **Fix:** Set `isPaused=true` in setUp so any in-flight scheduled task returns early at the `isPaused.get()` check in `checkAvailabilityAndProcessNextBuild` and cannot consume queue items. Renamed `resetPauseState()` to `setPauseState(boolean)` for flexibility and updated all callers.

**9. `BuildAgentIntegrationTest.testPauseBuildAgentBehavior()`**
- **Problem:** The test waited for the build agent status to be `ACTIVE` (which could pass immediately before the job started executing), then published a pause. With a 5 s mock sleep and 2 s grace period, on slow CI runners the mock sleep could finish before pause was triggered, causing the job to complete normally without being re-queued — so the final assertion waiting for the job to reappear in the queue timed out.
- **Fix:** Replace the status-polling wait with a `CountDownLatch` that is counted down inside the mock `startContainerCmd.exec()`, ensuring the test only triggers pause after the build is actually executing. The mock now sleeps for 60 s (instead of 5 s) to guarantee it is still running when the 2 s pause grace period expires. After the grace period, `handleTimeoutAndCancelRunningJobs` cancels the build job via `future.cancel(true)`, which interrupts the sleeping thread, and re-queues the job. Because the thread is interrupted, the test does not actually wait 60 s.

**10. `ProgrammingExerciseTemplateIntegrationTest` — Gradle cache corruption**
- **Problem:** Parallel test methods share the default Gradle user home (`/root/.gradle`), leading to transform cache corruption when multiple Gradle builds run simultaneously.
- **Fix:** Each `invokeGradle()` call now creates an isolated Gradle user home directory (`-g <unique-dir>`) using a UUID-based path, preventing cache conflicts. The directory is cleaned up in a `finally` block after each invocation.

#### Production Code Improvements

**11. `RepositoryService` — Diagnostic logging for `MissingObjectException`**
- **Problem:** Intermittent `MissingObjectException` errors occur on CI when reading repository files at a commit, with no diagnostic information to help debug the root cause.
- **Fix:** Added a catch block for `MissingObjectException` that logs detailed context (object ID, file path, commit, repo directory, object/pack directory existence, bare repo flag) before re-throwing. This helps diagnose potential JGit object visibility race conditions on the CI filesystem.

**12. `ProgrammingExerciseParticipationResource` — Proper error handling for file reading endpoints**
- **Problem:** The `getParticipationRepositoryFiles` and `getParticipationRepositoryFilesForCommitsDetailsView` endpoints propagated raw `IOException` from the method signature, resulting in unstructured 500 errors.
- **Fix:** Wrapped the `IOException` in an `InternalServerErrorAlertException` with a `fileReadError` error key, providing a user-friendly error message. Added corresponding i18n translations in English and German.

#### CI Workflow Improvements

**13. JaCoCo coverage violation reporting**
- **Problem:** When JaCoCo coverage thresholds were violated, the failure was buried in the Gradle output with no clear indication of which module(s) failed or how to fix it.
- **Fix:** Added a new "Print JaCoCo coverage violations" step that extracts violation messages and failing module names from the test log, displays them as GitHub Actions error annotations, and provides guidance on adjusting thresholds in `gradle/jacoco.gradle`.

**14. Capture stderr in test logs**
- **Problem:** Gradle test commands only captured stdout via `tee`, so stderr output (including some error messages) was not written to `tests.log`.
- **Fix:** Added `2>&1` to all Gradle test invocations to merge stderr into stdout, ensuring complete test output is available in the log file.

**15. Core module coverage threshold adjustment**
- Updated the core module instruction coverage threshold from 0.785 to 0.788 in `gradle/jacoco.gradle` to reflect improved test coverage.


### Steps for Testing

#### A) Verify the respective tests pass in the pipeline
E.g. this test run https://github.com/ls1intum/Artemis/actions/runs/22622054974/job/65552172823?pr=12220

#### B) Test locally
Prerequisites:
- None (changes are to test code, CI configuration, and error handling improvements)

1. Run the affected test classes individually and verify they pass:
    - `MetricsBeanTest`
    - `DataExportResourceIntegrationTest`
    - `LocalCIResourceIntegrationTest`
    - `LocalCIServiceTest`
    - `BuildAgentIntegrationTest`
    - `LocalVCSshIntegrationTest`
    - `ProgrammingExerciseTemplateIntegrationTest`
2. Run them repeatedly (e.g. 10×) to confirm no flakiness.
3. Run the full server test suite to verify no regressions.
4. Verify that the `getParticipationRepositoryFiles` and `getParticipationRepositoryFilesForCommitsDetailsView` endpoints return a proper error response (with `fileReadError` key) when file reading fails, instead of a raw 500 error.

### Review Progress
#### Code Review
- [ ] Code Review 1
- [ ] Code Review 2

### Test Coverage
<!-- Please add the test coverages for all changed files modified in this PR here. You can generate the coverage table using one of these options: -->
<!-- 1. Run `npm run coverage:pr` to generate coverage locally by running only the affected module tests (see supporting_scripts/code-coverage/local-pr-coverage/README.md) -->
<!-- 2. Use `supporting_scripts/code-coverage/generate_code_cov_table/generate_code_cov_table.py` to generate the table from CI artifacts (requires GitHub token, follow the README for setup) -->
<!-- The line coverage must be above 90% for changed files, and you must use extensive and useful assertions for server tests and expect statements for client tests. -->
<!-- Note: Confirm in the last column that you have implemented extensive assertions for server tests and expect statements for client tests. -->
<!--       Remove rows with only trivial changes from the table. -->

**Warning:** Server tests failed. Coverage could not be fully measured. Please check the [workflow logs](https://github.com/ls1intum/Artemis/actions/runs/22625574515).

_Last updated: 2026-03-03 14:11:42 UTC_

