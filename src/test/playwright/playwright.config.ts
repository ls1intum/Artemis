import { defineConfig, devices } from '@playwright/test';
import dotenv from 'dotenv';
import { parseNumber } from './support/utils';

/**
 * Read environment variables from file.
 * https://github.com/motdotla/dotenv
 */
dotenv.config({ path: `./playwright.env` });

/**
 * See https://playwright.dev/docs/test-configuration.
 */
export default defineConfig({
    testDir: './',
    /* Run tests in files in parallel */
    fullyParallel: true,
    timeout: (parseNumber(process.env.TEST_TIMEOUT_SECONDS) ?? 3 * 60) * 1000,
    retries: parseNumber(process.env.TEST_RETRIES) ?? 2,
    workers: parseNumber(process.env.TEST_WORKER_PROCESSES) ?? 3,
    /* Reporter to use. See https://playwright.dev/docs/test-reporters */
    reporter: [['junit', { outputFile: './test-reports/results.xml' }]],
    /* Shared settings for all the projects below. See https://playwright.dev/docs/api/class-testoptions. */
    use: {
        /* Base URL to use in actions like `await page.goto('/')`. */
        baseURL: process.env.BASE_URL || 'http://localhost:9000',
        /* Collect trace when retrying the failed test. See https://playwright.dev/docs/trace-viewer */
        trace: 'on-first-retry',
        ignoreHTTPSErrors: true,
    },

    /* Configure projects for major browsers */
    projects: [
        {
            name: 'chromium',
            use: { ...devices['Desktop Chrome'] },
        },
    ],
});
