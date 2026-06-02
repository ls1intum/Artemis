/**
 * Vitest configuration for the Monaco editor *integration* specs.
 *
 * These specs exercise Monaco's real text-editing engine (insert/replace/cursor/selection) via the
 * editor action adapters, so — unlike the rest of the client suite — they must run against the REAL
 * `monaco-editor` package rather than the lightweight mock used everywhere else. This mirrors how the
 * legacy Jest setup ran them (jest.config.js mapped `monaco-editor` to `editor.api.js`).
 *
 * Run as a separate Vitest project so the global `monaco-editor` -> mock alias in vitest.config.ts is
 * not applied here.
 */
import { defineConfig } from 'vitest/config';
import angular from '@analogjs/vite-plugin-angular';
import tsconfigPaths from 'vite-tsconfig-paths';
import path from 'node:path';

export default defineConfig({
    logLevel: 'error',
    resolve: {
        alias: {
            // Real Monaco (no mock) for these integration specs, matching the legacy Jest mapping.
            'monaco-editor': path.resolve(__dirname, 'node_modules/monaco-editor/esm/vs/editor/editor.api.js'),
            app: path.resolve(__dirname, 'src/main/webapp/app'),
            test: path.resolve(__dirname, 'src/test/javascript/spec'),
        },
    },
    css: {
        preprocessorOptions: {
            scss: {
                loadPaths: [path.resolve(__dirname)],
                silenceDeprecations: ['color-functions', 'global-builtin', 'import', 'if-function'],
            },
        },
    },
    plugins: [angular({ jit: true }), tsconfigPaths({ projects: ['tsconfig.app.json', 'tsconfig.spec.json'] })],
    test: {
        name: 'monaco-integration',
        globals: true,
        pool: 'forks',
        environment: 'jsdom',
        setupFiles: ['src/test/javascript/spec/vitest-test-setup.ts', 'src/test/javascript/spec/vitest-monaco-setup.ts'],
        include: ['src/test/javascript/spec/integration/monaco-editor/**/*.spec.ts'],
        exclude: ['**/node_modules/**', '**/build/**'],
        testTimeout: 15000,
        reporters: ['default'],
        server: {
            deps: {
                inline: [/@tumaet\/apollon/, /html-diff-ts/, /monaco-editor/],
            },
        },
    },
});
