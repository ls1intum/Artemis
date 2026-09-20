import { defineConfig } from '@playwright/test';
import { resolve } from 'node:path';

export default defineConfig({
    testDir: '.',
    forbidOnly: true,
    retries: 0,
    reporter: 'list',
    outputDir: resolve(__dirname, '../../build/test-results/tum-ui-documentation'),
    use: {
        baseURL: 'http://127.0.0.1:6107/',
        trace: 'retain-on-failure',
    },
    webServer: {
        command: 'pnpm --dir ../../packages/tum-ui exec vite preview --outDir ../../documentation/build --host 127.0.0.1 --port 6107 --strictPort',
        cwd: __dirname,
        url: 'http://127.0.0.1:6107/developer/intro',
        reuseExistingServer: false,
    },
});
