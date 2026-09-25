import { defineConfig } from '@playwright/test';
import { resolve } from 'node:path';

const consumerDirectory = process.env.TUM_UI_CONSUMER_DIR;
if (!consumerDirectory) {
    throw new Error('Run test:consumer to install the tarball in an isolated application first.');
}

export default defineConfig({
    testDir: '.',
    testMatch: 'consumer.spec.ts',
    forbidOnly: true,
    retries: 0,
    workers: 1,
    reporter: 'list',
    outputDir: process.env.TUM_UI_CONSUMER_RESULTS ?? resolve(process.cwd(), '../../build/test-results/tum-ui-consumer'),
    use: { baseURL: 'http://127.0.0.1:6206', trace: 'retain-on-failure' },
    webServer: {
        command: 'pnpm exec vite preview --outDir "$TUM_UI_CONSUMER_DIR/dist/browser" --host 127.0.0.1 --port 6206 --strictPort',
        cwd: process.cwd(),
        env: { TUM_UI_CONSUMER_DIR: consumerDirectory },
        url: 'http://127.0.0.1:6206',
        reuseExistingServer: false,
    },
});
