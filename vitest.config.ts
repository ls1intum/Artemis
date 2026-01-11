/**
 * Vitest Configuration for Artemis Angular Tests
 *
 * Uses @analogjs/vite-plugin-angular for Angular compilation with Vitest 4.
 */
import { defineConfig } from 'vitest/config';
import angular from '@analogjs/vite-plugin-angular';
import tsconfigPaths from 'vite-tsconfig-paths';
import path from 'node:path';

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
    // JIT mode required for ng-mocks compatibility
    plugins: [angular({ jit: true }), tsconfigPaths({ projects: ['tsconfig.app.json', 'tsconfig.spec.json'] })],
    test: {
        globals: true,
        environment: 'jsdom',
        setupFiles: ['src/test/javascript/spec/vitest-test-setup.ts'],
        include: [
            'src/main/webapp/app/fileupload/**/*.spec.ts',      // include fileupload tests
            'src/main/webapp/app/core/account/**/*.spec.ts',    // include core account tests
            'src/main/webapp/app/core/admin/**/*.spec.ts',      // include core admin tests
            'src/main/webapp/app/core/course/manage/**/*.spec.ts', // include course manage tests
            'src/main/webapp/app/buildagent/**/*.spec.ts',      // include build agent tests
            'src/main/webapp/app/text/**/*.spec.ts',            // include text module tests
            'src/main/webapp/app/assessment/**/*.spec.ts',      // include assessment tests
            'src/main/webapp/app/tutorialgroup/**/*.spec.ts',   // include tutorial group tests
            'src/main/webapp/app/quiz/**/*.spec.ts',            // include quiz tests
            'src/main/webapp/app/lecture/**/*.spec.ts',         // include lecture tests
            'src/main/webapp/app/lti/**/*.spec.ts',             // include lti tests
        ],
        exclude: ['**/node_modules/**', '**/build/**'],
        testTimeout: 10000,
        reporters: ['default', 'junit'],
        outputFile: './build/test-results/vitest/junit.xml',
        server: {
            deps: {
                inline: [/@ls1intum\/apollon/, /html-diff-ts/, /monaco-editor/],
            },
        },
        coverage: {
            provider: 'v8',
            reporter: ['text', 'lcov', 'html', 'json-summary'],
            reportsDirectory: 'build/test-results/vitest/coverage',
            include: [
                'src/main/webapp/app/assessment/**/*.ts',       // include assessment for code coverage
                'src/main/webapp/app/buildagent/**/*.ts',       // include buildagent for code coverage
                'src/main/webapp/app/core/account/**/*.ts',     // include core account for code coverage
                'src/main/webapp/app/core/admin/**/*.ts',       // include core admin for code coverage
                'src/main/webapp/app/core/course/manage/**/*.ts', // include course manage for code coverage
                'src/main/webapp/app/fileupload/**/*.ts',       // include fileupload for code coverage
                'src/main/webapp/app/lecture/**/*.ts',          // include lecture for code coverage
                'src/main/webapp/app/quiz/**/*.ts',             // include quiz for code coverage
                'src/main/webapp/app/text/**/*.ts',             // include text module for code coverage
                'src/main/webapp/app/tutorialgroup/**/*.ts',    // include tutorial group for code coverage
                'src/main/webapp/app/lti/**/*.ts',              // include lti for code coverage
            ],
            exclude: ['**/node_modules/**', '**/*.spec.ts', '**/*.route.ts', '**/*.routes.ts', '**/*.model.ts'],
            thresholds: {
                lines: 92.20,
                statements: 92.20,
                branches: 77.20,
                functions: 89.40,
            },
        },
    },
});
