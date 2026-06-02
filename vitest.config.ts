/**
 * Vitest Configuration for Artemis Angular Tests
 *
 * Uses @analogjs/vite-plugin-angular for Angular compilation with Vitest 4.
 *
 * The entire Angular client runs on Vitest, so the spec/coverage globs are broad (all of
 * src/main/webapp/app) rather than an explicit per-module allowlist. The only exceptions:
 *   - the Monaco editor *integration* specs run against the real Monaco package and therefore live
 *     in a separate project (see vitest.monaco.config.ts); they are excluded here.
 *   - generated code (openapi) and non-testable config/model/route files are excluded from coverage.
 */
import { defineConfig } from 'vitest/config';
import angular from '@analogjs/vite-plugin-angular';
import tsconfigPaths from 'vite-tsconfig-paths';
import path from 'node:path';

const isCI = process.env.CI === 'true';

export default defineConfig({
    // Only show errors, suppress sourcemap warnings from node_modules packages
    logLevel: 'error',
    resolve: {
        alias: {
            'monaco-editor': path.resolve(__dirname, 'src/test/javascript/spec/helpers/mocks/mock-monaco-editor.ts'),
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
    // JIT mode required for ng-mocks compatibility
    plugins: [angular({ jit: true }), tsconfigPaths({ projects: ['tsconfig.app.json', 'tsconfig.spec.json'] })],
    test: {
        globals: true,
        pool: 'forks',
        environment: 'jsdom',
        setupFiles: ['src/test/javascript/spec/vitest-test-setup.ts'],
        include: [
            'src/main/webapp/app/**/*.spec.ts', // entire Angular client runs on Vitest
            'src/test/javascript/spec/integration/code-editor/**/*.spec.ts', // code-editor integration specs (mock Monaco)
        ],
        // The Monaco editor integration specs need the real Monaco package; they run in vitest.monaco.config.ts.
        exclude: ['**/node_modules/**', '**/build/**', '**/integration/monaco-editor/**'],
        testTimeout: 10000,
        reporters: ['default', 'junit'],
        outputFile: './build/test-results/vitest/junit.xml',
        server: {
            deps: {
                inline: [/@tumaet\/apollon/, /html-diff-ts/, /monaco-editor/],
            },
        },
        coverage: {
            provider: 'istanbul',
            reporter: isCI ? ['text', 'lcov', 'json-summary'] : ['text', 'lcov', 'html', 'json-summary'],
            reportsDirectory: 'build/test-results/vitest/coverage',
            include: ['src/main/webapp/app/**/*.ts'],
            exclude: [
                '**/node_modules/**', // exclude node_modules with third-party code
                '**/*.spec.ts', // exclude test specification files
                '**/*.route.ts', // exclude route definition files (not really testable)
                '**/*.routes.ts', // exclude route definition files (not really testable)
                '**/*.model.ts', // exclude data model files (not really testable)
                'src/main/webapp/app/openapi/**', // generated OpenAPI client (not hand-written, not tested)
                'src/main/webapp/app/core/config/dayjs.ts', // exclude dayjs configuration file (not really testable)
                'src/main/webapp/app/core/config/monaco.config.ts', // exclude monaco configuration file (not really testable)
                'src/main/webapp/app/core/config/prod.config.ts', // exclude prod configuration file (not really testable)
            ],
            thresholds: {
                // Floors set just below current actuals to absorb Jest->Vitest migration drift; re-tune when migration completes.
                lines: 88.8,
                statements: 88.7,
                branches: 73.6,
                functions: 86.0,
            },
        },
    },
});
