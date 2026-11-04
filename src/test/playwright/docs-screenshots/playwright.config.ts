import { defineConfig } from '@playwright/test';
import path from 'path';

/**
 * Renders the documentation screenshots described in documentation/screenshots/manifest.json.
 *
 * This is deliberately a configuration of its own and not a project of ../playwright.config.ts: the screenshots need no
 * end-to-end seed data and no global setup, only a running Artemis with the `demo` profile (see
 * documentation/docs/developer/guidelines/documentation.mdx#screenshots). The files carry the suffix `.capture.ts`, which the
 * default `testMatch` of the end-to-end configuration does not select, so the screenshots never run with the end-to-end suite.
 *
 * Run: BASE_URL=http://localhost:9000 pnpm exec playwright test --config docs-screenshots/playwright.config.ts
 */
export default defineConfig({
    testDir: __dirname,
    testMatch: /.*\.capture\.ts/,
    // One worker and no retries: a retry would hide a page that renders differently on every attempt.
    workers: 1,
    retries: 0,
    fullyParallel: false,
    timeout: 60_000,
    outputDir: path.join(__dirname, '..', 'test-results', 'docs-screenshots'),
    reporter: [['list']],
    expect: { timeout: 15_000 },
    use: {
        baseURL: process.env.BASE_URL || 'http://localhost:9000',
        browserName: 'chromium',
        locale: 'en-US',
        timezoneId: 'Europe/Berlin',
        colorScheme: 'light',
        reducedMotion: 'reduce',
    },
});
