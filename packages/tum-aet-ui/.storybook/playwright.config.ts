import { defineConfig } from '@playwright/test';
import { resolve } from 'node:path';

export default defineConfig({
    testDir: './tests',
    forbidOnly: true,
    retries: 0,
    workers: 1,
    reporter: 'list',
    outputDir: resolve(process.cwd(), '../../build/test-results/tumaet-ui-storybook'),
    use: {
        baseURL: 'http://127.0.0.1:6106/tum-aet-ui/',
        trace: 'retain-on-failure',
    },
    webServer: {
        command: 'pnpm exec vite preview --outDir ../../build/storybook --host 127.0.0.1 --port 6106 --strictPort',
        cwd: process.cwd(),
        url: 'http://127.0.0.1:6106/tum-aet-ui/',
        reuseExistingServer: false,
    },
});
