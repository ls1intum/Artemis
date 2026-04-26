/**
 * Temporary Vitest Configuration for verification of exam/overview/summary tests
 */
import { defineConfig } from 'vitest/config';
import angular from '@analogjs/vite-plugin-angular';
import tsconfigPaths from 'vite-tsconfig-paths';
import path from 'node:path';

const __projectRoot = '/Users/krusche/Projects/Artemis2';

export default defineConfig({
    logLevel: 'error',
    resolve: {
        alias: {
            'monaco-editor': path.resolve(__projectRoot, 'src/test/javascript/spec/helpers/mocks/mock-monaco-editor.ts'),
            app: path.resolve(__projectRoot, 'src/main/webapp/app'),
            test: path.resolve(__projectRoot, 'src/test/javascript/spec'),
        },
    },
    css: {
        preprocessorOptions: {
            scss: {
                loadPaths: [path.resolve(__projectRoot)],
                silenceDeprecations: ['color-functions', 'global-builtin', 'import', 'if-function'],
            },
        },
    },
    plugins: [angular({ jit: true }), tsconfigPaths({ projects: [path.resolve(__projectRoot, 'tsconfig.app.json'), path.resolve(__projectRoot, 'tsconfig.spec.json')] })],
    test: {
        globals: true,
        pool: 'forks',
        environment: 'jsdom',
        setupFiles: [path.resolve(__projectRoot, 'src/test/javascript/spec/vitest-test-setup.ts')],
        include: [path.resolve(__projectRoot, 'src/main/webapp/app/exam/overview/summary/**/*.spec.ts')],
        exclude: ['**/node_modules/**', '**/build/**'],
        testTimeout: 10000,
        reporters: ['default'],
        server: {
            deps: {
                inline: [/@ls1intum\/apollon/, /html-diff-ts/, /monaco-editor/],
            },
        },
    },
});
