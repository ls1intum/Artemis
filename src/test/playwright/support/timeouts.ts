/**
 * Centralized timeout configuration for E2E tests.
 * Values can be overridden via environment variables for different environments (CI vs local).
 *
 * CI uses longer timeouts (default values), local uses shorter ones for faster feedback.
 * Set environment variables in run-e2e-tests-local.sh or docker-compose for different values.
 */

// Default timeouts (CI values)
const DEFAULT_BUILD_RESULT_TIMEOUT = 90000; // 90 seconds
const DEFAULT_BUILD_FINISH_TIMEOUT = 60000; // 60 seconds
const DEFAULT_EXAM_DASHBOARD_TIMEOUT = 60000; // 60 seconds

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
 * Interval between polling attempts (shared across all polling operations).
 */
export const POLLING_INTERVAL = 2000; // 2 seconds
