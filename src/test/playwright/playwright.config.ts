import { defineConfig } from '@playwright/test';
import dotenv from 'dotenv';
import { parseNumber } from './support/utils';
import 'app/foundation/util/map.extension';
import 'app/foundation/util/array.extension';
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
    workers: parseNumber(process.env.TEST_WORKER_PROCESSES) ?? 5,
    /* Reporter to use. See https://playwright.dev/docs/test-reporters */
    reporter: [
        ['list'],
        ['junit', { outputFile: process.env.PLAYWRIGHT_TEST_TYPE ? `./test-reports/results-${process.env.PLAYWRIGHT_TEST_TYPE}.xml` : './test-reports/results.xml' }],
        ...(process.env.PLAYWRIGHT_COVERAGE !== 'off'
            ? ([
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
              ] as const)
            : []),
    ],
    globalSetup: require.resolve('./init/global-setup.ts'),

    /* Increase default expect timeout from 5s to 10s for CI environments under parallel load */
    expect: {
        timeout: parseNumber(process.env.EXPECT_TIMEOUT_MS) ?? 10000,
    },
    /* Shared settings for all the projects below. See https://playwright.dev/docs/api/class-testoptions. */
    use: {
        /* Base URL to use in actions like `await page.goto('/')`. */
        baseURL: process.env.BASE_URL || 'http://localhost:9000',
        /* Collect trace when retrying the failed test. See https://playwright.dev/docs/trace-viewer */
        trace: 'on-first-retry',
        /* Record video for all tests (passed and failed). Videos are saved in test-results folder. */
        video: {
            mode: (process.env.PLAYWRIGHT_VIDEO_MODE as 'on' | 'off' | 'on-first-retry' | 'retain-on-failure') || 'on',
            size: { width: 1920, height: 1080 },
        },
        ignoreHTTPSErrors: true,
        /*
         * Cap every navigation (`goto`/`reload`) and `waitForLoadState` — they all default their
         * timeout to the context navigation timeout, which is otherwise unset (= infinite). Behind
         * the multi-node HTTPS load balancer an individual lazy-chunk fetch occasionally stalls (the
         * `load` event then never fires); without this cap the navigation hangs until the much larger
         * per-test timeout (observed: a single stalled edit-page chunk burning the full 360s budget).
         * 60s is far above any healthy navigation but well below the per-test budgets, so a genuine
         * stall fails fast and is picked up by the test-level retries instead of hanging.
         */
        navigationTimeout: parseNumber(process.env.NAVIGATION_TIMEOUT_MS) ?? 60000,
        launchOptions: {
            args: [
                '--disable-features=WebAuthnICloudKeychain,WebAuthnEnclaveAuthenticator',
                // When the app is served over HTTPS with a self-signed cert (multi-node runner), bypass
                // certificate validation at the browser-process level. The context-level `ignoreHTTPSErrors`
                // does not reliably cover ES-module / lazy-chunk script fetches, which intermittently failed
                // with "An SSL certificate error occurred when fetching the script", aborting route bootstraps
                // (Angular NG05604). No-op when the app is served over plain HTTP (single-node fast runner).
                ...((process.env.BASE_URL || '').startsWith('https') ? ['--ignore-certificate-errors'] : []),
                // Optional browser-level host resolver override (e.g. "MAP localhost 127.0.0.1"). The multi-node
                // runner sets this so the browser reaches the nginx LB over IPv4 (avoiding the historical ::1
                // ECONNREFUSED cascade) while still using a domain origin (https://localhost) — an IP literal such
                // as 127.0.0.1 is not a valid WebAuthn Relying Party ID and breaks every passkey test.
                ...(process.env.PW_BROWSER_HOST_RESOLVER_RULES ? [`--host-resolver-rules=${process.env.PW_BROWSER_HOST_RESOLVER_RULES}`] : []),
            ],
        },
    },

    /* Configure projects for fast, slow, and multi-node tests */
    projects: [
        // Tests with @fast tag or without any tags. These are the lightweight tests with lower timeout.
        // grepInvert excludes @multi-node so single-node runs do not pick up cluster-only assertions.
        {
            name: 'fast-tests',
            grep: /@fast|^[^@]*$/,
            grepInvert: /@multi-node/,
            timeout: (parseNumber(process.env.FAST_TEST_TIMEOUT_SECONDS) ?? 60) * 1000,
            use: { browserName: 'chromium', viewport: { width: 1920, height: 1080 } },
        },
        // Tests with @slow tag. These tests are expected to run longer
        // than faster tests and have higher timeout.
        {
            name: 'slow-tests',
            grep: /@slow/,
            grepInvert: /@multi-node/,
            timeout: (parseNumber(process.env.SLOW_TEST_TIMEOUT_SECONDS) ?? 90) * 1000,
            use: {
                browserName: 'chromium',
                viewport: { width: 1920, height: 1080 },
            },
        },
        // Tests with @multi-node tag. These exercise the clustered Hazelcast / ActiveMQ stack and
        // are skipped by the single-node fast pipeline. The multi-node runner opts in explicitly.
        {
            name: 'multi-node-tests',
            grep: /@multi-node/,
            timeout: (parseNumber(process.env.SLOW_TEST_TIMEOUT_SECONDS) ?? 90) * 1000,
            use: {
                browserName: 'chromium',
                viewport: { width: 1920, height: 1080 },
            },
        },
    ],
});
