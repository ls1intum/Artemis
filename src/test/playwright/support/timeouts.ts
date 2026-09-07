/**
 * Centralized timeout configuration for E2E tests.
 * Values can be overridden via environment variables for different environments (CI vs local).
 *
 * CI uses longer timeouts (default values), local uses shorter ones for faster feedback.
 * Set environment variables in run-e2e-tests-local.sh or docker-compose for different values.
 */

// Default timeouts (CI values — generous to handle parallel test load)
const DEFAULT_BUILD_RESULT_TIMEOUT = 120000; // 120 seconds
const DEFAULT_BUILD_FINISH_TIMEOUT = 300000; // 300 seconds
const DEFAULT_EXAM_DASHBOARD_TIMEOUT = 120000; // 120 seconds
const DEFAULT_RELOAD_RENDER_TIMEOUT = 30000; // 30 seconds

/**
 * Reads a timeout from the environment, falling back to the default unless the value is a clean positive integer
 * number of milliseconds.
 * <p>
 * `parseInt` is too permissive for configuration that decides how long a test waits: it accepts a partial string like
 * `30000ms`, yields `NaN` for anything unparseable, and happily returns negatives. Any of those would silently
 * override a documented budget with a nonsensical one, and the resulting failure would look like a flaky test rather
 * than a misconfigured variable. So a value that is present but unusable is reported and ignored rather than used.
 *
 * A blank value is deliberately treated as "not configured" and passes without a warning, because that is how these
 * variables arrive when they are not set: `ci-e2e.yml` supplies them as `${{ vars.SOME_TIMEOUT }}`, which renders an
 * empty string for an unset repository variable, and the compose files use `${SOME_TIMEOUT:-default}`, which also
 * treats empty as absent. Warning here would fire on ordinary runs rather than on a mistake.
 *
 * @param variableName name of the environment variable to read
 * @param fallback default timeout in ms, used when the variable is unset, blank, or unusable
 */
function readTimeoutMs(variableName: string, fallback: number): number {
    const raw = process.env[variableName];
    if (raw === undefined || raw.trim() === '') {
        // Unset, or set to an empty string by an unconfigured CI variable — not a misconfiguration to report.
        return fallback;
    }
    const parsed = Number(raw);
    if (!Number.isSafeInteger(parsed) || parsed <= 0) {
        console.warn(`[timeouts] Ignoring ${variableName}="${raw}": expected a positive integer number of milliseconds. Falling back to ${fallback}ms.`);
        return fallback;
    }
    return parsed;
}

/**
 * Timeout for waiting for build results to appear in the UI (e.g., commit history).
 * Environment variable: BUILD_RESULT_TIMEOUT_MS
 */
export const BUILD_RESULT_TIMEOUT = readTimeoutMs('BUILD_RESULT_TIMEOUT_MS', DEFAULT_BUILD_RESULT_TIMEOUT);

/**
 * Timeout for waiting for a build to finish (polling API).
 * Environment variable: BUILD_FINISH_TIMEOUT_MS
 */
export const BUILD_FINISH_TIMEOUT = readTimeoutMs('BUILD_FINISH_TIMEOUT_MS', DEFAULT_BUILD_FINISH_TIMEOUT);

/**
 * Timeout for waiting for exam assessment dashboard to load.
 * Environment variable: EXAM_DASHBOARD_TIMEOUT_MS
 */
export const EXAM_DASHBOARD_TIMEOUT = readTimeoutMs('EXAM_DASHBOARD_TIMEOUT_MS', DEFAULT_EXAM_DASHBOARD_TIMEOUT);

/**
 * Timeout for the first assertion after a full `page.reload()`.
 * <p>
 * A reload re-bootstraps the entire client, and Playwright disables the HTTP cache per context, so the bundle and its
 * lazy chunks are re-fetched from scratch. Under parallel CI load that regularly exceeds the default 10s expect
 * timeout, which is what made the post-reload assertions in the channel and exam participation tests flaky.
 * <p>
 * Apply it only to the first assertion after the reload: anything rendered in the same pass is already present by then,
 * so later assertions keep the default timeout and the budgets cannot stack into the per-test cap.
 *
 * Environment variable: RELOAD_RENDER_TIMEOUT_MS
 */
export const RELOAD_RENDER_TIMEOUT = readTimeoutMs('RELOAD_RENDER_TIMEOUT_MS', DEFAULT_RELOAD_RENDER_TIMEOUT);

/**
 * Interval between polling attempts (shared across all polling operations).
 */
export const POLLING_INTERVAL = 2000; // 2 seconds
