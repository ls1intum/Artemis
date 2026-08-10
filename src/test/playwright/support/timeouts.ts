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
 * Timeout for waiting for build results to appear in the UI (e.g., commit history).
 * Environment variable: BUILD_RESULT_TIMEOUT_MS
 */
export const BUILD_RESULT_TIMEOUT = parseInt(process.env.BUILD_RESULT_TIMEOUT_MS || String(DEFAULT_BUILD_RESULT_TIMEOUT), 10);

/**
 * Timeout for waiting for a build to finish (polling API).
 * Environment variable: BUILD_FINISH_TIMEOUT_MS
 */
export const BUILD_FINISH_TIMEOUT = parseInt(process.env.BUILD_FINISH_TIMEOUT_MS || String(DEFAULT_BUILD_FINISH_TIMEOUT), 10);

/**
 * Timeout for waiting for exam assessment dashboard to load.
 * Environment variable: EXAM_DASHBOARD_TIMEOUT_MS
 */
export const EXAM_DASHBOARD_TIMEOUT = parseInt(process.env.EXAM_DASHBOARD_TIMEOUT_MS || String(DEFAULT_EXAM_DASHBOARD_TIMEOUT), 10);

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
export const RELOAD_RENDER_TIMEOUT = parseInt(process.env.RELOAD_RENDER_TIMEOUT_MS || String(DEFAULT_RELOAD_RENDER_TIMEOUT), 10);

/**
 * Interval between polling attempts (shared across all polling operations).
 */
export const POLLING_INTERVAL = 2000; // 2 seconds
