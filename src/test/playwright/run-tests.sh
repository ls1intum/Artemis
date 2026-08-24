#!/bin/bash
# Script to run Playwright tests with optional test path filtering
# Usage: ./run-tests.sh [test-paths...]
# If no test paths are provided, runs all tests in e2e/

TEST_PATHS=("$@")
FAILED=0
REPORTER_FAILED=0

# Clean up stale markers/logs from previous runs (self-hosted runners have persistent workspaces)
mkdir -p ./test-reports
rm -f ./test-reports/.reporter-failed ./test-reports/pw-output-*.log ./test-reports/e2e-counts.env
# The per-project reports go too. A rerun that dies before writing one would otherwise be judged against the
# previous run's passing XML and merged into the final report as though it had just passed.
rm -f ./test-reports/results.xml ./test-reports/results-*.xml

if [ ${#TEST_PATHS[@]} -eq 0 ] && [ -n "$PLAYWRIGHT_TEST_PATHS" ]; then
    read -r -a TEST_PATHS <<< "$PLAYWRIGHT_TEST_PATHS"
fi

# Whether a filter narrowed the run. Only a filtered run may legitimately match no test in a given project.
FILTERED=0
[ ${#TEST_PATHS[@]} -gt 0 ] && FILTERED=1

# Check JUnit XML to determine if actual test failures occurred.
# Returns 0 if tests passed (including when tests ran with no failures).
# Returns 1 if tests failed.
# Returns 2 if no tests were found (XML missing or has no testcases).
check_test_results() {
    local xml_file="$1"

    if [ ! -f "$xml_file" ]; then
        return 2
    fi

    if ! grep -q '<testcase' "$xml_file"; then
        return 2
    fi

    if grep -qE 'failures="[1-9]|errors="[1-9]' "$xml_file"; then
        return 1
    fi

    return 0
}

# Run a playwright test command and evaluate the result.
# Sets FAILED=1 on real test failures, REPORTER_FAILED=1 on reporter-only failures.
run_playwright() {
    local test_type="$1"
    shift

    # Raise the runner heap headroom: the single "run all" CI job executes the whole ~316-test suite in
    # one Playwright process, and its per-run accumulation (results, coverage, attachments) hit the old
    # 6144 MB cap mid-run and OOM'd a worker ("Reached heap limit — JavaScript heap out of memory"),
    # which spuriously failed the tests it was running. We APPEND the flag rather than rely on a default:
    # the root .npmrc sets `node-options=--max-old-space-size=6144`, which pnpm injects into NODE_OPTIONS,
    # so a `${NODE_OPTIONS:-…}` fallback would never fire. Node applies the LAST --max-old-space-size, so
    # appending 8192 raises the cap for this invocation only, without touching the global .npmrc value.
    # Tee the output to a per-type log (in addition to the console) so we can later aggregate
    # Playwright's own pass/flaky/fail/skip summary — the flaky count is not expressible in JUnit.
    # PIPESTATUS[0] preserves Playwright's real exit code across the pipe.
    # `--pass-with-no-tests` is what makes the failure handling below unambiguous. A filtered run may legitimately
    # match no test in a given project, and without this flag that case is a non-zero exit with no report, which
    # is indistinguishable from a browser that failed to launch or a global setup that threw. With it, "nothing
    # matched" exits 0 and every non-zero exit is a real problem.
    NODE_OPTIONS="${NODE_OPTIONS:-} --max-old-space-size=8192" PLAYWRIGHT_TEST_TYPE="$test_type" pnpm exec playwright test --pass-with-no-tests "$@" 2>&1 | tee "./test-reports/pw-output-${test_type}.log"
    local exit_code=${PIPESTATUS[0]}

    local junit_file="./test-reports/results-${test_type}.xml"

    # `--pass-with-no-tests` buys an unambiguous exit code at the price of turning "this project selected no
    # test at all" into a silent success, so that case is asserted separately. On an unfiltered run every project
    # named on the command line has to contribute tests; a selection that quietly matches nothing (a renamed
    # project, a changed grep, a spec that moved out of range) would otherwise stop a suite from running while CI
    # stays green.
    #
    # The assertion is per project, not per invocation, because several projects share one report: the cross-engine
    # invocation runs three browser engines at once, so WebKit going silent while Chromium still reports would leave
    # the aggregate non-empty. Playwright's JUnit reporter records each testsuite's project in its `hostname`
    # attribute, which is where that per-project fact survives.
    if [ "$FILTERED" -eq 0 ]; then
        local argument
        local project
        for argument in "$@"; do
            case "$argument" in
                --project=*) project="${argument#--project=}" ;;
                *) continue ;;
            esac
            if ! grep -q "hostname=\"${project}\"" "$junit_file" 2>/dev/null; then
                echo "ERROR: Project '$project' contributed no test result, although nothing filtered this run."
                echo "It ran no spec at all. See ./test-reports/pw-output-${test_type}.log."
                FAILED=1
            fi
        done
    fi

    if [ "$exit_code" -ne 0 ]; then
        check_test_results "$junit_file"
        local check_result=$?
        if [ $check_result -eq 0 ]; then
            echo "WARNING: Playwright exited with code $exit_code but JUnit XML shows no test failures."
            echo "This likely indicates a reporter failure (e.g., monocart OOM). Tests themselves passed."
            REPORTER_FAILED=1
        elif [ $check_result -eq 2 ]; then
            # Playwright failed and wrote no usable report, so it never got as far as running a test: a collection
            # error, a browser that is not installed, a global setup that threw. Reading that as "nothing to run"
            # is how a suite silently stops executing while CI stays green, so it counts as a failure.
            echo "ERROR: Project type '$test_type' exited with code $exit_code and produced no test results."
            echo "Playwright did not get as far as running a test. See ./test-reports/pw-output-${test_type}.log."
            FAILED=1
        else
            FAILED=1
        fi
    fi
}

echo "=== Running Playwright Tests ==="

if [ ${#TEST_PATHS[@]} -gt 0 ]; then
    echo "Running filtered tests: ${TEST_PATHS[*]}"

    # Run parallel tests (fast and slow projects)
    echo "--- Running parallel tests ---"
    run_playwright parallel --project=fast-tests --project=slow-tests "${TEST_PATHS[@]}"

    # Then the tests that mutate global server state, alone.
    echo "--- Running sequential tests ---"
    run_playwright sequential --project=sequential-tests --workers 1 "${TEST_PATHS[@]}"

    echo "--- Running cross-engine tests ---"
    run_playwright cross-engine --project=cross-engine-chromium --project=cross-engine-firefox --project=cross-engine-webkit "${TEST_PATHS[@]}"
else
    echo "Running all tests"

    # Run parallel tests (fast and slow projects)
    echo "--- Running parallel tests ---"
    run_playwright parallel e2e --project=fast-tests --project=slow-tests

    # A test in this project flips a global feature toggle, which would decide which renderer a concurrent worker's
    # page loads. It therefore runs in its own invocation, single-worker, once nothing else is in flight.
    echo "--- Running sequential tests ---"
    run_playwright sequential e2e --project=sequential-tests --workers 1

    # A handful of assertions whose answer differs per browser engine, so a Chromium-only run cannot make them.
    # Deliberately its own invocation rather than a second browser for the whole suite: running everything three
    # times over would be unaffordable, and almost nothing else here depends on the engine.
    echo "--- Running cross-engine tests ---"
    run_playwright cross-engine e2e --project=cross-engine-chromium --project=cross-engine-firefox --project=cross-engine-webkit
fi

# Run the @multi-node project only when the surrounding stack opts in via env var. The multi-node
# docker compose sets EXPECTED_CLUSTER_NODE_COUNT, the single-node compose does not. This keeps the
# cluster smoke-test out of every other Playwright run while still running it automatically when
# the multi-node stack is up.
if [ -n "$EXPECTED_CLUSTER_NODE_COUNT" ]; then
    echo "--- Running multi-node tests (cluster size $EXPECTED_CLUSTER_NODE_COUNT) ---"
    if [ ${#TEST_PATHS[@]} -gt 0 ]; then
        run_playwright multinode --project=multi-node-tests "${TEST_PATHS[@]}"
    else
        run_playwright multinode e2e --project=multi-node-tests
    fi
fi

# Aggregate Playwright's own pass/flaky/fail/skip counts across every project invocation above and
# write them to a small env file the CI workflow folds into the E2E commit-status description
# ("309 passed, 3 flaky, 0 failed"). Playwright's JUnit report cannot express "flaky" (a retried test
# that ultimately passes is just a passing <testcase>), so Playwright's own end-of-run summary is the
# authoritative source. Each summary count line looks like "  309 passed (34.0m)" / "  3 flaky".
echo "--- Aggregating test counts ---"
E2E_PASSED=0
E2E_FLAKY=0
E2E_FAILED=0
E2E_SKIPPED=0
for pw_log in ./test-reports/pw-output-*.log; do
    [ -f "$pw_log" ] || continue
    while read -r count kind; do
        case "$kind" in
            passed) E2E_PASSED=$((E2E_PASSED + count)) ;;
            flaky) E2E_FLAKY=$((E2E_FLAKY + count)) ;;
            failed) E2E_FAILED=$((E2E_FAILED + count)) ;;
            skipped) E2E_SKIPPED=$((E2E_SKIPPED + count)) ;;
        esac
    done < <(sed -E 's/\x1b\[[0-9;]*m//g' "$pw_log" | tr -d '\r' | grep -oE '^[[:space:]]+[0-9]+ (passed|flaky|failed|skipped)' | grep -oE '[0-9]+ (passed|flaky|failed|skipped)')
done
{
    echo "E2E_PASSED=${E2E_PASSED}"
    echo "E2E_FLAKY=${E2E_FLAKY}"
    echo "E2E_FAILED=${E2E_FAILED}"
    echo "E2E_SKIPPED=${E2E_SKIPPED}"
} > ./test-reports/e2e-counts.env
echo "E2E counts: ${E2E_PASSED} passed, ${E2E_FLAKY} flaky, ${E2E_FAILED} failed, ${E2E_SKIPPED} skipped"

# Remove any stale results.xml (e.g. from playwright:setup init test) before
# moving the real report into place, so CI never consumes an outdated report.
echo "--- Finalizing test reports ---"
rm -f ./test-reports/results.xml
# Globbed rather than enumerated: each invocation above writes its own results-<type>.xml, and a report left out
# here is invisible to the JUnit report and to the failure classifier, so a failure in it cannot even be named
# while the script still exits nonzero. A glob cannot fall behind when an invocation is added.
REPORTS=()
for report in ./test-reports/results-*.xml; do
    [ -f "$report" ] && REPORTS+=("$report")
done
# The exit codes are checked because a merge that fails leaves no results.xml behind, and a consumer that finds
# no report cannot distinguish that from a run in which nothing failed.
if [ ${#REPORTS[@]} -gt 1 ]; then
    if ! pnpm exec junit-merge "${REPORTS[@]}" -o ./test-reports/results.xml; then
        echo "ERROR: Merging the JUnit reports failed, so ./test-reports/results.xml is missing or incomplete."
        FAILED=1
    fi
elif [ ${#REPORTS[@]} -eq 1 ]; then
    if ! mv "${REPORTS[0]}" ./test-reports/results.xml; then
        echo "ERROR: Could not move ${REPORTS[0]} into place as ./test-reports/results.xml."
        FAILED=1
    fi
else
    # Every invocation exited 0 and still nothing was reported. Harmless for a filter that matched nothing,
    # but worth saying out loud rather than leaving an empty report to be read as a clean run.
    echo "WARNING: No JUnit report was produced by any project invocation."
fi
pnpm run merge-coverage-reports || true

# Upload reports to E2E Reports Dashboard
if [ -n "$PLAYWRIGHT_REPORT_SERVER_URL" ] && [ -n "$PLAYWRIGHT_REPORT_TOKEN" ]; then
    echo "--- Uploading reports to E2E Reports Dashboard ---"

    PHASE="${PLAYWRIGHT_REPORT_PHASE:-all}"
    RUN_ID="${GITHUB_RUN_ID:-local}-${PHASE}"

    # Build file list dynamically — only include paths that actually exist
    UPLOAD_PATHS=()
    for p in \
        test-reports/results.xml \
        test-reports/monocart-report-parallel \
        test-reports/monocart-report-sequential \
        test-reports/client-coverage \
        test-results/; do
        [ -e "$p" ] && UPLOAD_PATHS+=("$p")
    done

    UPLOAD_ARCHIVE="/tmp/e2e-upload-${RUN_ID}.tar.gz"
    if [ ${#UPLOAD_PATHS[@]} -gt 0 ]; then
        tar -czf "$UPLOAD_ARCHIVE" "${UPLOAD_PATHS[@]}" 2>/dev/null
    fi

    if [ -f "$UPLOAD_ARCHIVE" ]; then
        echo "Uploading reports ($(du -h "$UPLOAD_ARCHIVE" | cut -f1))..."
        if ! curl --silent --show-error --fail-with-body \
            --connect-timeout 10 --max-time 300 \
            --request PUT "${PLAYWRIGHT_REPORT_SERVER_URL}/api/upload" \
            -H "Authorization: Bearer ${PLAYWRIGHT_REPORT_TOKEN}" \
            -F "archive=@${UPLOAD_ARCHIVE}" \
            -F "run_id=${RUN_ID}" \
            -F "github_run_id=${GITHUB_RUN_ID:-local}" \
            -F "branch=${PLAYWRIGHT_REPORT_BRANCH:-unknown}" \
            -F "commit_sha=${PLAYWRIGHT_REPORT_COMMIT_SHA:-unknown}" \
            -F "pr_number=${PLAYWRIGHT_REPORT_PR_NUMBER:-}" \
            -F "phase=${PHASE}" \
            -F "triggered_by=${PLAYWRIGHT_REPORT_TRIGGERED_BY:-unknown}"; then
            echo "WARNING: Failed to upload reports to E2E dashboard"
        fi
        rm -f "$UPLOAD_ARCHIVE"
    else
        echo "WARNING: No report artifacts found to upload"
    fi
fi

# Write marker file if reporter failed but tests passed (picked up by execute.sh for CI reporting).
# When tests also fail, the test failure is the primary signal — no need to add reporter noise.
if [ "$REPORTER_FAILED" -eq 1 ] && [ "$FAILED" -eq 0 ]; then
    echo "Reporter process failed (likely monocart OOM). Test results were not affected." > ./test-reports/.reporter-failed
    echo "WARNING: Reporter failure detected. See ./test-reports/.reporter-failed"
fi

echo "=== Tests completed ==="
exit $FAILED
