import { defineConfig } from '@playwright/test';
import dotenv from 'dotenv';
import { parseNumber } from './support/utils';
import 'app/shared/util/map.extension';
import 'app/shared/util/string.extension';
import 'app/shared/util/array.extension';
import path from 'path';

/**
 * Read environment variables from file.
 * https://github.com/motdotla/dotenv
 */
dotenv.config({ path: path.join(__dirname, 'playwright.env') });

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
    reporter: [
        ['list'],
        ['junit', { outputFile: process.env.PLAYWRIGHT_TEST_TYPE ? `./test-reports/results-${process.env.PLAYWRIGHT_TEST_TYPE}.xml` : './test-reports/results.xml' }],
        [
            'monocart-reporter',
            {
                outputFile: process.env.PLAYWRIGHT_TEST_TYPE ? `./test-reports/monocart-report-${process.env.PLAYWRIGHT_TEST_TYPE}` : './test-reports/monocart-report',
                coverage: {
                    reports: ['lcov', 'json'],
                    filter: {
                        '**/src/**': true,
                        '**/node_modules/**': false,
                        client: false,
                        '**/**': true,
                    },
                },
            },
        ],
    ],
    globalSetup: require.resolve('./init/global-setup.ts'),

    /* Shared settings for all the projects below. See https://playwright.dev/docs/api/class-testoptions. */
    use: {
        /* Base URL to use in actions like `await page.goto('/')`. */
        baseURL: process.env.BASE_URL || 'http://localhost:9000',
        /* Collect trace when retrying the failed test. See https://playwright.dev/docs/trace-viewer */
        trace: 'on-first-retry',
        ignoreHTTPSErrors: true,
    },

    /* Configure projects for fast and slow tests */
    projects: [
        // Tests with @fast tag or without any tags. These are the lightweight tests with lower timeout.
        {
            name: 'fast-tests',
            grep: /@fast|^[^@]*$/,
            timeout: (parseNumber(process.env.FAST_TEST_TIMEOUT_SECONDS) ?? 45) * 1000,
            use: { browserName: 'chromium', viewport: { width: 1920, height: 1080 } },
        },
        // Tests with @slow tag. These tests are expected to run longer
        // than faster tests and have higher timeout.
        {
            name: 'slow-tests',
            grep: /@slow/,
            timeout: (parseNumber(process.env.SLOW_TEST_TIMEOUT_SECONDS) ?? 180) * 1000,
            use: {
                browserName: 'chromium',
                viewport: { width: 1920, height: 1080 },
            },
        },
        // Tests with @sequential tag. These tests are triggering programming exercise submissions.
        // Running only one programming exercise evaluation at a time could make the tests more stable.
        // Thus, it is recommended to run this project with a single worker.
        {
            name: 'sequential-tests',
            grep: /@sequential/,
            timeout: (parseNumber(process.env.SLOW_TEST_TIMEOUT_SECONDS) ?? 180) * 1000,
            use: {
                browserName: 'chromium',
                viewport: { width: 1920, height: 1080 },
            },
        },
    ],
});
